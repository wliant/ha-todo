import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ThemeProvider } from '@mui/material/styles'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SideNav } from './SideNav'
import * as listsApi from '../api/lists'
import * as dataListsApi from '../api/dataLists'
import * as trackersApi from '../api/trackers'
import type { CurrentUser } from '../api/auth'
import type { ListSummary } from '../api/lists'
import type { CollectionSummary } from '../api/dataLists'
import type { TrackerSummary } from '../api/trackers'
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

function tracker(id: string, name: string, pinned = false): TrackerSummary {
  return {
    id,
    name,
    description: null,
    icon: 'HEALTH',
    unit: 'kg',
    period: 'DAY',
    scope: 'PER_MEMBER',
    rollup: 'LATEST',
    decimals: 1,
    target: null,
    targetDirection: null,
    owned: true,
    pinned,
    memberCount: 1,
    readingCount: 0,
  }
}

function given(
  lists: ListSummary[],
  collections: CollectionSummary[] = [],
  pinned: {
    lists?: ListSummary[]
    collections?: CollectionSummary[]
    trackers?: TrackerSummary[]
  } = {},
  trackers: TrackerSummary[] = [],
) {
  vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({ pinned: pinned.lists ?? [], lists })
  vi.spyOn(dataListsApi, 'fetchCollectionSidebar').mockResolvedValue({
    pinned: pinned.collections ?? [],
    collections,
  })
  vi.spyOn(trackersApi, 'fetchTrackerSidebar').mockResolvedValue({
    pinned: pinned.trackers ?? [],
    trackers,
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

  it('does not claim a group is empty while it is still loading', async () => {
    // The regression. "No lists yet" is a confident lie about somebody's own
    // data, and before this it was what every member saw for as long as the
    // request took - most visibly right after a deploy, against a cold server.
    // Resolved against a promise that never settles, so the query cannot rescue
    // the assertion.
    vi.spyOn(listsApi, 'fetchSidebar').mockReturnValue(new Promise(() => {}))
    vi.spyOn(dataListsApi, 'fetchCollectionSidebar').mockReturnValue(new Promise(() => {}))
    vi.spyOn(trackersApi, 'fetchTrackerSidebar').mockReturnValue(new Promise(() => {}))
    renderNav()

    expect(await screen.findByRole('link', { name: 'Lists' })).toBeVisible()
    expect(screen.queryByText('No lists yet')).not.toBeInTheDocument()
    expect(screen.queryByText('No collections yet')).not.toBeInTheDocument()
    // The third group, added by 0019, and the one most likely to have been
    // written as `data ?? []` because it was copied in last.
    expect(screen.queryByText('No trackers yet')).not.toBeInTheDocument()
  })

  it('says so when a group cannot be loaded, rather than saying it is empty', async () => {
    vi.spyOn(listsApi, 'fetchSidebar').mockRejectedValue(new Error('offline'))
    vi.spyOn(dataListsApi, 'fetchCollectionSidebar').mockResolvedValue({
      pinned: [],
      collections: [collection('id-c', 'Cooking')],
    })
    renderNav()

    expect(await screen.findByText("Couldn't load your lists.")).toBeVisible()
    expect(screen.queryByText('No lists yet')).not.toBeInTheDocument()
    // The half that worked is unaffected.
    expect(screen.getByRole('link', { name: 'Cooking' })).toBeVisible()
  })

  it('still draws pinned lists when the collections request fails', async () => {
    // Requiring both halves before drawing the section was the same conflation
    // in a second place: one failure hid pins that had loaded fine.
    vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({
      pinned: [list('id-g', 'Groceries', true)],
      lists: [],
    })
    vi.spyOn(dataListsApi, 'fetchCollectionSidebar').mockRejectedValue(new Error('offline'))
    renderNav()

    const pinned = await screen.findByRole('list', { name: 'Pinned' })
    expect(within(pinned).getByRole('link', { name: 'Groceries' })).toBeVisible()
  })

  it('shows the member’s trackers under their own group', async () => {
    // 0019 R170: the third kind gets the same group the other two have.
    given([], [], {}, [tracker('id-w', 'Body Weight')])
    renderNav()

    // The entry, not the group label: the label is static markup and would
    // resolve before the query had answered.
    expect(await screen.findByRole('link', { name: 'Body Weight' })).toHaveAttribute(
      'href',
      '/trackers/id-w',
    )
    expect(screen.getByRole('link', { name: 'Trackers' })).toHaveAttribute('href', '/trackers')
  })

  it('draws a pinned tracker in the pinned section, beside the other kinds', async () => {
    // The concatenation in `pinnedEntries` fails silently if a kind is left
    // out: the pin simply never appears, with nothing to report.
    given([], [], {
      lists: [list('id-g', 'Groceries', true)],
      trackers: [tracker('id-w', 'Body Weight', true)],
    })
    renderNav()

    const pinned = await screen.findByRole('list', { name: 'Pinned' })
    expect(within(pinned).getByRole('link', { name: 'Body Weight' })).toBeVisible()
    expect(within(pinned).getByRole('link', { name: 'Groceries' })).toBeVisible()
  })

  it('still draws pinned trackers when both other requests fail', async () => {
    // `ready` is a disjunction over all three: requiring every half would hide
    // pins that loaded perfectly well, which is the conflation NavGroup and
    // PinnedSection have each shipped with once already.
    vi.spyOn(listsApi, 'fetchSidebar').mockRejectedValue(new Error('offline'))
    vi.spyOn(dataListsApi, 'fetchCollectionSidebar').mockRejectedValue(new Error('offline'))
    vi.spyOn(trackersApi, 'fetchTrackerSidebar').mockResolvedValue({
      pinned: [tracker('id-w', 'Body Weight', true)],
      trackers: [],
    })
    renderNav()

    const pinned = await screen.findByRole('list', { name: 'Pinned' })
    expect(within(pinned).getByRole('link', { name: 'Body Weight' })).toBeVisible()
  })

  it('says so when the trackers group cannot be loaded', async () => {
    vi.spyOn(listsApi, 'fetchSidebar').mockResolvedValue({ pinned: [], lists: [] })
    vi.spyOn(dataListsApi, 'fetchCollectionSidebar').mockResolvedValue({
      pinned: [],
      collections: [],
    })
    vi.spyOn(trackersApi, 'fetchTrackerSidebar').mockRejectedValue(new Error('offline'))
    renderNav()

    expect(await screen.findByText("Couldn't load your trackers.")).toBeVisible()
    expect(screen.queryByText('No trackers yet')).not.toBeInTheDocument()
  })

  it('says a group is empty rather than showing nothing', async () => {
    // R103. Nothing at all reads as a request that failed.
    given([], [])
    renderNav()

    expect(await screen.findByText('No lists yet')).toBeVisible()
    expect(screen.getByText('No collections yet')).toBeVisible()
    expect(screen.getByText('No trackers yet')).toBeVisible()
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
