import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SharePage } from './SharePage'
import * as client from '../api/auth'
import { parkedLinks } from '../share/parkedLinks'

function renderShare(query: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[`/share${query}`]}>
        <Routes>
          <Route path="/share" element={<SharePage />} />
          <Route path="/lists" element={<p>lists</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('SharePage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    window.localStorage.clear()
    vi.spyOn(client, 'fetchCurrentUser').mockResolvedValue({
      id: 'id-ana',
      username: 'ana',
      displayName: 'Ana',
      role: 'MEMBER',
    })
  })

  it('captures a shared link and says it is being held', async () => {
    renderShare('?title=Lemon%20pie&url=https%3A%2F%2Fexample.com%2Fpie')

    expect(await screen.findByText('Saved. It is waiting on your lists.')).toBeVisible()
    expect(screen.getByText('Lemon pie')).toBeVisible()
    expect(screen.getByRole('link', { name: 'https://example.com/pie' })).toBeVisible()
    // Held for the member who shared it, not claimed to be an item.
    expect(parkedLinks('id-ana')).toHaveLength(1)
  })

  it('parks a share once, not once per effect run', async () => {
    renderShare('?url=https%3A%2F%2Fexample.com%2Fpie')
    await screen.findByText('Saved. It is waiting on your lists.')

    expect(parkedLinks('id-ana')).toHaveLength(1)
  })

  it('says so when the share carried nothing', async () => {
    renderShare('')

    expect(await screen.findByText('Nothing came through with that share.')).toBeVisible()
    expect(parkedLinks('id-ana')).toHaveLength(0)
  })
})
