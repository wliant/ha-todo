import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, waitFor } from '@testing-library/react'
import { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as liveApi from '../api/live'
import type { LiveNotification } from '../api/live'
import { useLiveUpdates } from './useLiveUpdates'

/**
 * The connection, and the three ways it can fail.
 *
 * jsdom has no `EventSource`, which is the whole reason the production code
 * feature-detects. A fake is installed **per file, here** and never globally in
 * `src/test/setup.ts`: a global one would mean no test anywhere exercises the
 * degraded path, and every unrelated test would hold a fake connection.
 */
class FakeEventSource {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSED = 2

  static opened: FakeEventSource[] = []

  readyState = FakeEventSource.CONNECTING
  closed = false
  private readonly listeners = new Map<string, ((event: Event) => void)[]>()

  constructor() {
    FakeEventSource.opened.push(this)
  }

  addEventListener(type: string, listener: (event: Event) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  close() {
    this.closed = true
    this.readyState = FakeEventSource.CLOSED
  }

  /** The server accepted the connection. */
  open() {
    this.readyState = FakeEventSource.OPEN
    this.fire('open', new Event('open'))
  }

  /** The server wrote a frame. */
  push(notification: LiveNotification) {
    this.fire('item.created', new MessageEvent('item.created', {
      data: JSON.stringify(notification),
    }))
  }

  pushRaw(data: string) {
    this.fire('item.created', new MessageEvent('item.created', { data }))
  }

  /** The connection failed for good - a 401, or a refused endpoint. */
  fail() {
    this.readyState = FakeEventSource.CLOSED
    this.fire('error', new Event('error'))
  }

  private fire(type: string, event: Event) {
    act(() => {
      this.listeners.get(type)?.forEach((listener) => listener(event))
    })
  }
}

function notification(over: Partial<LiveNotification> = {}): LiveNotification {
  return {
    type: 'item.created',
    itemId: 'i1',
    listId: 'l1',
    listName: 'Groceries',
    description: 'Milk',
    actorDisplayName: 'Ben',
    byYou: false,
    ...over,
  }
}

let queryClient: QueryClient
let invalidated: unknown[][]
let announced: LiveNotification[]

function Harness() {
  useLiveUpdates((item) => {
    announced.push(item)
  })
  return null
}

function mount() {
  return render(
    <QueryClientProvider client={queryClient}>
      <Harness />
    </QueryClientProvider>,
  )
}

/** The stream the hook just opened. */
function stream(index = 0): FakeEventSource {
  const opened = FakeEventSource.opened[index]
  expect(opened).toBeDefined()
  return opened
}

function invalidatedKeys(): string[] {
  return invalidated.map((key) => String(key[0]))
}

beforeEach(() => {
  FakeEventSource.opened = []
  announced = []
  invalidated = []
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  vi.spyOn(queryClient, 'invalidateQueries').mockImplementation((filters) => {
    invalidated.push((filters?.queryKey ?? []) as unknown[])
    return Promise.resolve()
  })
  vi.spyOn(liveApi, 'liveStreamSupported').mockReturnValue(true)
  vi.spyOn(liveApi, 'openLiveStream')
    .mockImplementation(() => new FakeEventSource() as unknown as EventSource)
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.useRealTimers()
})

describe('a chore somebody else added', () => {
  it('refreshes the agenda and the list it came from', async () => {
    mount()
    stream().open()

    stream().push(notification())

    // Both key trees. Naming only ['dashboard'] leaves the list screen one
    // chore short with nothing to refetch it (adr/0039).
    await waitFor(() => {
      expect(invalidatedKeys()).toContain('dashboard')
      expect(invalidatedKeys()).toContain('lists')
    })
  })

  it('is announced by name', async () => {
    mount()
    stream().open()

    stream().push(notification({ description: 'Collect the parcel' }))

    await waitFor(() => expect(announced).toHaveLength(1))
    expect(announced[0].description).toBe('Collect the parcel')
  })
})

describe("a chore the member added themselves", () => {
  it('is not announced', async () => {
    mount()
    stream().open()

    stream().push(notification({ byYou: true }))

    await waitFor(() => expect(invalidatedKeys()).toContain('dashboard'))
    expect(announced).toHaveLength(0)
  })

  /*
   * R181. The member may have a second device open, and the one that did not
   * perform the create still needs the refetch - only the naming is suppressed.
   */
  it('still refreshes, because another device of theirs may be showing it', async () => {
    mount()
    stream().open()

    stream().push(notification({ byYou: true }))

    await waitFor(() => {
      expect(invalidatedKeys()).toContain('dashboard')
      expect(invalidatedKeys()).toContain('lists')
    })
  })
})

describe('a frame that cannot be read', () => {
  it('is ignored rather than tearing the agenda down', async () => {
    mount()
    stream().open()

    stream().pushRaw('not json at all')

    expect(announced).toHaveLength(0)
    // And the stream is still open and still listening.
    stream().push(notification())
    await waitFor(() => expect(announced).toHaveLength(1))
  })
})

describe('when the stream cannot be opened', () => {
  it('falls back to refreshing the agenda on an interval', async () => {
    vi.useFakeTimers()
    mount()

    stream().fail()
    invalidated = []
    await act(async () => {
      vi.advanceTimersByTime(60_000)
    })

    // adr/0056: R179 survives a proxy we do not control. Only the dashboard -
    // the fallback has no idea what changed, so it refreshes what it is for.
    expect(invalidatedKeys()).toContain('dashboard')
  })

  it('announces nothing, because it does not know what arrived', async () => {
    vi.useFakeTimers()
    mount()

    stream().fail()
    await act(async () => {
      vi.advanceTimersByTime(180_000)
    })

    expect(announced).toHaveLength(0)
  })

  it('stops trying after a handful of refusals rather than hammering', () => {
    vi.useFakeTimers()
    mount()

    for (let i = 0; i < 10; i++) {
      FakeEventSource.opened.at(-1)?.fail()
    }

    // A bound, not a loop. EventSource does not retry a 401 itself, so without
    // this the hook would be the thing hammering.
    expect(FakeEventSource.opened.length).toBeLessThanOrEqual(5)
  })

  it('asks whether the session is still good', () => {
    mount()

    stream().fail()

    expect(invalidatedKeys()).toContain('currentUser')
  })

  it('polls only while the tab is being looked at', async () => {
    vi.useFakeTimers()
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    mount()

    stream().fail()
    invalidated = []
    await act(async () => {
      vi.advanceTimersByTime(180_000)
    })

    expect(invalidated).toHaveLength(0)
  })
})

describe('a browser with no EventSource', () => {
  it('goes straight to the fallback instead of throwing', async () => {
    vi.useFakeTimers()
    vi.spyOn(liveApi, 'liveStreamSupported').mockReturnValue(false)

    expect(() => mount()).not.toThrow()
    expect(FakeEventSource.opened).toHaveLength(0)

    await act(async () => {
      vi.advanceTimersByTime(60_000)
    })
    expect(invalidatedKeys()).toContain('dashboard')
  })
})

describe('leaving the agenda', () => {
  /*
   * StrictMode double-invokes effects in development. A stream left open means
   * every message appears twice, which reads as a server bug.
   */
  it('closes the stream', () => {
    const { unmount } = mount()
    stream().open()

    unmount()

    expect(stream().closed).toBe(true)
  })

  it('stops the fallback too', async () => {
    vi.useFakeTimers()
    const { unmount } = mount()
    stream().fail()

    unmount()
    invalidated = []
    await act(async () => {
      vi.advanceTimersByTime(180_000)
    })

    expect(invalidated).toHaveLength(0)
  })
})
