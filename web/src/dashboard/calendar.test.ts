import { describe, expect, it } from 'vitest'
import {
  addDays,
  civilIn,
  instantOf,
  windowOf,
  addMonths,
  byDay,
  dayKey,
  dayRange,
  isSameMonth,
  monthGrid,
  monthRange,
  startOfWeek,
  weekDays,
  weekRange,
} from './calendar'

describe('calendar', () => {
  it('starts a week on the Monday', () => {
    // 2026-11-05 is a Thursday.
    expect(dayKey(startOfWeek(new Date(2026, 10, 5)))).toBe('2026-11-02')
  })

  it('starts a Sunday’s week on the Monday six days earlier', () => {
    // The off-by-one every (getDay() - 1) implementation gets wrong, because
    // JavaScript numbers Sunday as 0.
    expect(dayKey(startOfWeek(new Date(2026, 10, 8)))).toBe('2026-11-02')
  })

  it('leaves a Monday where it is', () => {
    expect(dayKey(startOfWeek(new Date(2026, 10, 2)))).toBe('2026-11-02')
  })

  it('lays out seven days, Monday first', () => {
    const days = weekDays(new Date(2026, 10, 5)).map(dayKey)

    expect(days).toEqual([
      '2026-11-02',
      '2026-11-03',
      '2026-11-04',
      '2026-11-05',
      '2026-11-06',
      '2026-11-07',
      '2026-11-08',
    ])
  })

  it('builds a month grid of exactly 42 cells, with the right edges', () => {
    // Always 42, so the grid does not change height between months and make
    // the page jump as somebody steps through them.
    const grid = monthGrid(new Date(2026, 10, 15))

    expect(grid).toHaveLength(42)
    // November 2026 begins on a Sunday, so the grid opens on 26 October.
    expect(dayKey(grid[0])).toBe('2026-10-26')
    expect(dayKey(grid[41])).toBe('2026-12-06')
    expect(grid.filter((day) => isSameMonth(day, new Date(2026, 10, 1)))).toHaveLength(30)
  })

  it('adds a day across the spring-forward boundary without losing an hour', () => {
    // The reason every function here uses setDate rather than adding
    // 86_400_000: the millisecond version is an hour out twice a year, and it
    // shows up as a week column that is mysteriously empty.
    //
    // Europe/London springs forward on 2027-03-28. Asserted through the local
    // calendar date, which is what the views group by.
    const before = new Date(2027, 2, 27, 12, 0, 0)
    const after = addDays(before, 1)

    expect(dayKey(after)).toBe('2027-03-28')
    expect(dayKey(addDays(after, 1))).toBe('2027-03-29')
  })

  it('steps months from the 31st without skidding past one', () => {
    // Naively adding a month to 31 January lands on 31 February, which the
    // platform silently rolls into March.
    expect(dayKey(addMonths(new Date(2027, 0, 31), 1))).toBe('2027-02-01')
  })

  it('makes every range half-open and contiguous', () => {
    const week = weekRange(new Date(2026, 10, 5))

    expect(dayKey(week.from)).toBe('2026-11-02')
    // Exclusive: the Monday after, not the Sunday.
    expect(dayKey(week.to)).toBe('2026-11-09')

    const day = dayRange(new Date(2026, 10, 5))
    expect(dayKey(day.from)).toBe('2026-11-05')
    expect(dayKey(day.to)).toBe('2026-11-06')
  })

  it('widens a month range to whole weeks so the grid’s edges are covered', () => {
    const month = monthRange(new Date(2026, 10, 15))
    const grid = monthGrid(new Date(2026, 10, 15))

    expect(dayKey(month.from)).toBe(dayKey(grid[0]))
    expect(dayKey(month.to)).toBe(dayKey(addDays(grid[41], 1)))
  })

  it('groups by the household’s day, not by UTC’s and not by the device’s', () => {
    // 00:30 on the 5th in Singapore is still the 4th in UTC, so slicing an ISO
    // string files it under the wrong column - and so does anything that reads
    // the machine running this test (adr/0036).
    const grouped = byDay(
      [{ dueAt: '2026-11-04T16:30:00.000Z' }],
      (item) => item.dueAt,
      'Asia/Singapore',
    )

    expect([...grouped.keys()]).toEqual(['2026-11-05'])
  })

  it('groups the same instant into a different day in a different zone', () => {
    const grouped = byDay([{ dueAt: '2026-11-04T16:30:00.000Z' }], (item) => item.dueAt, 'UTC')

    expect([...grouped.keys()]).toEqual(['2026-11-04'])
  })

  it('leaves undated items out of the grouping entirely', () => {
    const grouped = byDay([{ dueAt: null }], (item) => item.dueAt, 'Asia/Singapore')

    expect(grouped.size).toBe(0)
  })

  describe('the household’s zone', () => {
    it('reads an instant as the household’s wall clock', () => {
      const civil = civilIn(new Date('2026-11-04T16:30:00.000Z'), 'Asia/Singapore')

      expect(dayKey(civil)).toBe('2026-11-05')
      expect(civil.getHours()).toBe(0)
      expect(civil.getMinutes()).toBe(30)
    })

    it('reads midnight as hour zero, not as hour 24', () => {
      // Some locales render the start of a day as 24:00 of the day before, and
      // a formatter that does puts every midnight event on the wrong date.
      const civil = civilIn(new Date('2026-11-04T16:00:00.000Z'), 'Asia/Singapore')

      expect(civil.getHours()).toBe(0)
      expect(dayKey(civil)).toBe('2026-11-05')
    })

    it('turns a household civil time back into the instant it happened at', () => {
      const instant = instantOf(new Date(2026, 10, 5, 0, 30), 'Asia/Singapore')

      expect(instant.toISOString()).toBe('2026-11-04T16:30:00.000Z')
    })

    it('round-trips an instant through the household’s clock', () => {
      const original = new Date('2026-06-15T09:17:00.000Z')

      expect(instantOf(civilIn(original, 'Asia/Singapore'), 'Asia/Singapore')).toEqual(original)
    })

    it('survives a zone that does observe daylight saving', () => {
      // London is +01:00 in July, so the correction pass is what makes this
      // right rather than an hour out.
      const instant = instantOf(new Date(2026, 6, 15, 12, 0), 'Europe/London')

      expect(instant.toISOString()).toBe('2026-07-15T11:00:00.000Z')
    })

    it('asks the API for the household’s midnight, not the device’s', () => {
      const window = windowOf(dayRange(new Date(2026, 10, 5)), 'Asia/Singapore')

      expect(window.from).toBe('2026-11-04T16:00:00.000Z')
      expect(window.to).toBe('2026-11-05T16:00:00.000Z')
    })
  })
})
