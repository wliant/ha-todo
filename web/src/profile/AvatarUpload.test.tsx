import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AvatarUpload } from './AvatarUpload'
import * as profileApi from '../api/profile'
import type { CurrentUser } from '../api/auth'
import { theme } from '../theme/theme'

const ana: CurrentUser = {
  id: 'id-ana',
  username: 'ana',
  displayName: 'Ana',
  role: 'MEMBER',
  avatarImageId: null,
}

function renderUpload(user: CurrentUser = ana) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <AvatarUpload user={user} />
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

function file(name: string, bytes: number, type = 'image/png') {
  return new File([new Uint8Array(bytes)], name, { type })
}

describe('AvatarUpload', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('offers to add a picture when there is none', () => {
    renderUpload()
    expect(screen.getByRole('button', { name: 'Add a picture' })).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Remove' })).not.toBeInTheDocument()
  })

  it('shows the picture a member already has, and offers to replace it', () => {
    renderUpload({ ...ana, avatarImageId: 'id-face' })

    expect(screen.getByRole('button', { name: 'Change picture' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Remove' })).toBeVisible()
    expect(document.querySelector('img')).toHaveAttribute('src', '/api/images/id-face')
  })

  it('uploads the chosen file', async () => {
    const upload = vi.spyOn(profileApi, 'setOwnAvatar').mockResolvedValue('id-new')
    renderUpload()

    await userEvent.upload(screen.getByLabelText('Profile picture'), file('me.png', 10))

    await waitFor(() => expect(upload).toHaveBeenCalled())
    expect(upload.mock.calls[0][0].name).toBe('me.png')
  })

  it('refuses a file over the limit without troubling the server', async () => {
    const upload = vi.spyOn(profileApi, 'setOwnAvatar')
    renderUpload()

    await userEvent.upload(
      screen.getByLabelText('Profile picture'),
      file('huge.png', 5 * 1024 * 1024 + 1),
    )

    expect(await screen.findByText(/too large/)).toBeVisible()
    expect(upload).not.toHaveBeenCalled()
  })

  it('removes the picture', async () => {
    const remove = vi.spyOn(profileApi, 'removeOwnAvatar').mockResolvedValue()
    renderUpload({ ...ana, avatarImageId: 'id-face' })

    await userEvent.click(screen.getByRole('button', { name: 'Remove' }))

    await waitFor(() => expect(remove).toHaveBeenCalled())
  })

  it('surfaces the server wording when it refuses the bytes', async () => {
    vi.spyOn(profileApi, 'setOwnAvatar').mockRejectedValue({
      isAxiosError: true,
      response: { status: 415, data: { error: 'Pictures must be a PNG, JPEG or WebP.' } },
    })
    renderUpload()

    await userEvent.upload(screen.getByLabelText('Profile picture'), file('me.svg', 10))

    expect(await screen.findByText('Pictures must be a PNG, JPEG or WebP.')).toBeVisible()
  })
})
