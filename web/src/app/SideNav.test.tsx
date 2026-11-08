import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SideNav } from './SideNav'
import * as listsApi from '../api/lists'
import * as dataListsApi from '../api/dataLists'
import type { CurrentUser } from '../api/auth'
import type { ListSummary } from '../api/lists'
import type { CollectionSummary } from '../api/dataLists'
import { theme } from '../theme/theme'

const ana: CurrentUser = {
  id: 'id-ana',
  username: 'ana',
  displayName: 'Ana',
  role: 'MEMBER',
  timezone: 'Asia/Singapore',
  avatarImageId: null,
}

function list(id: string, name: string, pinned = false): ListSummary {
  return {
    id,
    name,
    description: null,
    icon: 'CHECKLIST',
    owned: true,
    pinned,
    memberCount: 1,
  }
}

function collection(id: string, name: string, pinned = false): CollectionSummary {
  return {
    id,
    name,
    description: null,
    icon: 'READING',
    owned: true,
    pinned,
    memberCount: 1,
    entryCount: 0,
  }
}

function given(
  lists: ListSummary[],
  collections: CollectionSummary[] = [],
  pinned: { lists?: ListSummary[]; collections?: CollectionSummary[] } = {},
) {
  vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({ pinned: pinned.lists ?? [], lists })
  vi.spyOn(dataListsApi, 'fetchCollectionSidebar').mockResolvedValue({
    pinned: pinned.collections ?? [],
    collections,
  })
}

function renderNav(user: CurrentUser = ana) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme} defaultMode="light">
        <MemoryRouter>
          <SideNav user={user} onSignOut={() => {}} />
        </MemoryRouter>
      </ThemeProvider>
    </QueryClientProvider>,
  )
}

describe('SideNav', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('shows the member’s lists and collections under their own groups', async () => {
    given([list('id-g', 'Groceries')], [collection('id-r', 'Recipes')])
    renderNav()

    expect(await screen.findByRole('link', { name: 'Groceries' })).toHaveAttribute(
      'href',
      '/lists/id-g',
    )
    expect(screen.getByRole('link', { name: 'Recipes' })).toHaveAttribute(
      'href',
      '/collections/id-r',
    )
  })

  it('opens both groups to start with', async () => {
    // R99, and adr/0042: these groups replaced the Pinned and Recent sections,
    // so starting collapsed would offer strictly less than what they replaced.
    given([list('id-g', 'Groceries')], [collection('id-r', 'Recipes')])
    renderNav()

    expect(await screen.findByRole('button', { name: 'Hide lists' })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
    expect(screen.getByRole('button', { name: 'Hide collections' })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
  })

  it('collapses one group without touching the other', async () => {
    given([list('id-g', 'Groceries')], [collection('id-r', 'Recipes')])
    renderNav()

    await userEvent.click(await screen.findByRole('button', { name: 'Hide lists' }))

    // waitFor, because Collapse unmounts its children only once the transition
    // has run - the row is still in the DOM on the frame after the click.
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'Groceries' })).not.toBeInTheDocument(),
    )
    expect(screen.getByRole('link', { name: 'Recipes' })).toBeVisible()

    await userEvent.click(screen.getByRole('button', { name: 'Show lists' }))

    expect(await screen.findByRole('link', { name: 'Groceries' })).toBeVisible()
  })

  it('keeps the group label itself a link to the index', async () => {
    // R99: expanding is a separate control, so the label still navigates.
    given([list('id-g', 'Groceries')])
    renderNav()

    expect(await screen.findByRole('link', { name: 'Lists' })).toHaveAttribute('href', '/lists')
    expect(screen.getByRole('link', { name: 'Collections' })).toHaveAttribute(
      'href',
      '/collections',
    )
  })

  it('says a group is empty rather than showing nothing', async () => {
    // R103. Nothing at all reads as a request that failed.
    given([], [])
    renderNav()

    expect(await screen.findByText('No lists yet')).toBeVisible()
    expect(screen.getByText('No collections yet')).toBeVisible()
  })

  it('keeps both create buttons reachable with nothing at all', async () => {
    given([], [])
    renderNav()

    expect(await screen.findByRole('link', { name: 'Create a list' })).toHaveAttribute(
      'href',
      '/lists/new',
    )
    expect(screen.getByRole('link', { name: 'Create a collection' })).toHaveAttribute(
      'href',
      '/collections/new',
    )
  })

  it('holds pinned lists and collections together in one section', async () => {
    // 0012 R104, R105 and adr/0043: a member pins the handful of things they
    // are working from, and which of them is a list is not how they think
    // about it. Merged by name, so Chores sorts before Recipes.
    given([list('id-o', 'Other')], [collection('id-c', 'Cooking')], {
      lists: [list('id-g', 'Groceries', true)],
      collections: [collection('id-r', 'Recipes', true)],
    })
    renderNav()

    const pinned = await screen.findByRole('list', { name: 'Pinned' })
    expect(
      within(pinned)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Groceries', 'Recipes'])
    // A pinned collection links into the collections route, not the lists one.
    expect(within(pinned).getByRole('link', { name: 'Recipes' })).toHaveAttribute(
      'href',
      '/collections/id-r',
    )
  })

  it('keeps a pinned thing out of the group below, so nothing is drawn twice', async () => {
    given([list('id-o', 'Other')], [collection('id-c', 'Cooking')], {
      lists: [list('id-g', 'Groceries', true)],
    })
    renderNav()

    await screen.findByRole('list', { name: 'Pinned' })
    // The server partitions them; this asserts the shell does not re-merge.
    expect(screen.getAllByRole('link', { name: 'Groceries' })).toHaveLength(1)
  })

  it('renders no Pinned section at all when nothing is pinned', async () => {
    // Unlike the groups below, which say "no lists yet": an empty labelled
    // Pinned section is noise, and a member who never pinned anything does not
    // need telling.
    given([list('id-o', 'Other')], [collection('id-c', 'Cooking')])
    renderNav()

    await screen.findByRole('link', { name: 'Other' })
    expect(screen.queryByRole('list', { name: 'Pinned' })).not.toBeInTheDocument()
  })

  it('offers administration to the admin only', async () => {
    given([], [])
    renderNav({ ...ana, role: 'ADMIN' })

    expect(await screen.findByRole('link', { name: 'Users' })).toBeVisible()
  })

  it('does not offer administration to a member', async () => {
    given([], [])
    renderNav()
    await screen.findByText('No lists yet')

    expect(screen.queryByRole('link', { name: 'Users' })).not.toBeInTheDocument()
  })
})
