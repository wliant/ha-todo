import { describe, expect, it } from 'vitest'
import type { TrackerSeries } from '../api/trackers'
import { againstTarget, formatAmount, headline, toChart } from './series'

/**
 * Server series into chart props (requirements/0019 R167).
 *
 * <p>These assertions live here rather than on the rendered chart on purpose:
 * `@mui/x-charts` rewrites its markup between minor versions, so a test
 * reaching into the SVG is a time bomb (adr/0053). Everything worth knowing
 * about what the chart will draw is decided in this module.
 */
function series(overrides: Partial<TrackerSeries> = {}): TrackerSeries {
  return {
    trackerId: 'id-t',
    unit: 'kg',
    period: 'DAY',
    rollup: 'LATEST',
    scope: 'PER_MEMBER',
    decimals: 1,
    target: null,
    targetDirection: null,
    by: 'DAY',
    from: '2026-11-17',
    to: '2026-11-20',
    buckets: ['2026-11-17', '2026-11-18', '2026-11-19'],
    series: [
      {
        memberId: 'id-ana',
        displayName: 'Ana',
        avatarImageId: null,
        values: [72.9, null, 72.4],
      },
    ],
    ...overrides,
  }
}

describe('toChart', () => {
  it('keeps a gap as a gap rather than turning it into zero', () => {
    // A missed weigh-in is not 0 kg. A chart that drew it as one invents a
    // crash that never happened.
    expect(toChart(series()).lines[0].data).toEqual([72.9, null, 72.4])
  })

  it('draws bars when the tracker sums and a line otherwise', () => {
    expect(toChart(series({ rollup: 'SUM' })).bars).toBe(true)
    expect(toChart(series({ rollup: 'LATEST' })).bars).toBe(false)
    expect(toChart(series({ rollup: 'AVERAGE' })).bars).toBe(false)
  })

  it('gives the household series a stable key rather than an index', () => {
    const shared = series({
      scope: 'SHARED',
      series: [{ memberId: null, displayName: 'Everyone', avatarImageId: null, values: [1] }],
      buckets: ['2026-11-01'],
    })
    expect(toChart(shared).lines[0].id).toBe('everyone')
  })

  it('labels the axis at the grain actually drawn, not the tracker’s own', () => {
    const monthly = series({
      by: 'MONTH',
      buckets: ['2026-10-01', '2026-11-01'],
      series: [{ memberId: 'id-ana', displayName: 'Ana', avatarImageId: null, values: [1, 2] }],
    })
    expect(toChart(monthly).labels).toEqual(['October 2026', 'November 2026'])
  })

  it('calls a series of nothing but gaps empty', () => {
    // Not "no buckets": the axis is always dense, so an all-null series is
    // what "nothing in this period" actually looks like.
    const blank = series({
      series: [
        { memberId: 'id-ana', displayName: 'Ana', avatarImageId: null, values: [null, null, null] },
      ],
    })
    expect(toChart(blank).empty).toBe(true)
    expect(toChart(series()).empty).toBe(false)
  })
})

describe('headline', () => {
  it('reads the latest recorded value and the one before it, skipping gaps', () => {
    const result = headline(series())
    expect(result.latest).toBe(72.4)
    // The 18th is a gap, so "before" is the 17th rather than null.
    expect(result.previous).toBe(72.9)
    expect(result.latestLabel).toBe('Thu, 19 Nov')
  })

  it('says there is nothing rather than guessing when every bucket is a gap', () => {
    const blank = series({
      series: [
        { memberId: 'id-ana', displayName: 'Ana', avatarImageId: null, values: [null, null, null] },
      ],
    })
    expect(headline(blank).latest).toBeNull()
  })

  it('says there is nothing when no series came back at all', () => {
    expect(headline(series({ series: [] })).latest).toBeNull()
  })
})

describe('againstTarget', () => {
  it('knows which side of an at-most target is good', () => {
    const under = againstTarget(69, { target: 70, targetDirection: 'AT_MOST' })
    expect(under).toEqual({ distance: 1, met: true })
    const over = againstTarget(72.4, { target: 70, targetDirection: 'AT_MOST' })
    // closeTo, not toEqual: 72.4 - 70 is 2.4000000000000057 in binary
    // floating point, and pinning that literal would make this test about
    // IEEE 754 rather than about which side of the target we are on. The
    // screen rounds to the tracker's decimals anyway.
    expect(over?.distance).toBeCloseTo(2.4, 5)
    expect(over?.met).toBe(false)
  })

  it('knows which side of an at-least target is good', () => {
    expect(againstTarget(45, { target: 30, targetDirection: 'AT_LEAST' })?.met).toBe(true)
    expect(againstTarget(20, { target: 30, targetDirection: 'AT_LEAST' })?.met).toBe(false)
  })

  it('counts sitting exactly on the target as met', () => {
    expect(againstTarget(70, { target: 70, targetDirection: 'AT_MOST' }))
      .toEqual({ distance: 0, met: true })
  })

  it('says nothing at all without both halves of a target', () => {
    expect(againstTarget(72, { target: null, targetDirection: null })).toBeNull()
    expect(againstTarget(null, { target: 70, targetDirection: 'AT_MOST' })).toBeNull()
  })
})

describe('formatAmount', () => {
  it('prints the places the tracker asked for', () => {
    expect(formatAmount(72.4, 1)).toBe('72.4')
    expect(formatAmount(72, 0)).toBe('72')
    expect(formatAmount(980, 2)).toBe('980.00')
  })
})
