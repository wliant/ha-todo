import { describe, expect, it } from 'vitest'
import { civilFromKey, describePeriod, periodKey, startOfPeriod, stepPeriod } from './period'

/**
 * Bucket maths in the browser (requirements/0019 R166).
 *
 * <p>Every case here fixes the date explicitly rather than reading the clock:
 * a test that computed "today" would pass in any zone and prove nothing about
 * the one bug this is guarding - a week or a month landing on the wrong side
 * of a boundary.
 */
describe('period', () => {
  it('leaves a day alone', () => {
    expect(periodKey(new Date(2026, 10, 19), 'DAY')).toBe('2026-11-19')
  })

  it('maps a Sunday back to the Monday its week began on', () => {
    // 2026-11-22 is a Sunday. Getting this wrong puts a Sunday reading in next
    // week, which nobody notices until a chart has two short weeks in it.
    expect(periodKey(new Date(2026, 10, 22), 'WEEK')).toBe('2026-11-16')
  })

  it('maps any day of a month to the first', () => {
    expect(periodKey(new Date(2026, 10, 30), 'MONTH')).toBe('2026-11-01')
    expect(periodKey(new Date(2026, 10, 1), 'MONTH')).toBe('2026-11-01')
  })

  it('steps by whole buckets, including across a year', () => {
    expect(periodKey(stepPeriod(new Date(2026, 10, 19), 'DAY', -1), 'DAY')).toBe('2026-11-18')
    expect(periodKey(stepPeriod(new Date(2026, 10, 16), 'WEEK', 1), 'WEEK')).toBe('2026-11-23')
    expect(periodKey(stepPeriod(new Date(2026, 11, 1), 'MONTH', 1), 'MONTH')).toBe('2027-01-01')
    // 31 -> 30, where a plusDays(31) implementation would land on 1 March.
    expect(periodKey(stepPeriod(new Date(2026, 0, 31), 'MONTH', 1), 'MONTH')).toBe('2026-02-01')
  })

  /**
   * `new Date('2026-11-19')` parses as UTC midnight, which is the 18th
   * everywhere west of Greenwich - the device-zone trap in its other form.
   * This is the function that exists so nothing in the feature writes it.
   */
  it('parses a bucket key as a local civil date, not as UTC midnight', () => {
    const civil = civilFromKey('2026-11-19')
    expect(civil.getFullYear()).toBe(2026)
    expect(civil.getMonth()).toBe(10)
    expect(civil.getDate()).toBe(19)
  })

  it('round-trips a key through a civil date unchanged', () => {
    expect(periodKey(civilFromKey('2026-11-19'), 'DAY')).toBe('2026-11-19')
  })

  it('names a week by its span rather than by its Monday', () => {
    // "16 Nov" for a week that includes the 22nd is a date somebody will read
    // as a day.
    expect(describePeriod(new Date(2026, 10, 16), 'WEEK')).toBe('16-22 Nov')
  })

  it('names a week that crosses a month with both months', () => {
    // 2026-11-30 is a Monday; the week runs into December.
    expect(describePeriod(new Date(2026, 10, 30), 'WEEK')).toBe('30 Nov-6 Dec')
  })

  it('names a month without a day in it', () => {
    expect(describePeriod(new Date(2026, 10, 1), 'MONTH')).toBe('November 2026')
  })

  it('starts a bucket without mutating what it was given', () => {
    const day = new Date(2026, 10, 22)
    startOfPeriod(day, 'WEEK')
    expect(day.getDate()).toBe(22)
  })
})
