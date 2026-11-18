import type { Rollup, TrackerPeriod, TrackerScope } from '../api/trackers'

/**
 * How a tracker's declaration reads on screen.
 *
 * <p>One place, because the card, the detail heading and the settings screen
 * all describe the same four values and three descriptions that disagree is
 * how a member comes to think they are looking at different things.
 */
export function describeShape(tracker: {
  unit: string
  period: TrackerPeriod
  rollup: Rollup
}): string {
  return `${tracker.unit}, ${periodAdverb(tracker.period)}`
}

export function periodAdverb(period: TrackerPeriod): string {
  switch (period) {
    case 'DAY':
      return 'daily'
    case 'WEEK':
      return 'weekly'
    case 'MONTH':
      return 'monthly'
  }
}

/**
 * What the rollup means in words, for the form that asks for it.
 *
 * <p>Phrased as what happens to <em>several numbers in one bucket</em>, because
 * that is the question a member is actually answering - "what if I shop twice
 * in November?" - rather than as the name of an aggregate function.
 */
export function describeRollup(rollup: Rollup, period: TrackerPeriod): string {
  const bucket = period === 'DAY' ? 'day' : period === 'WEEK' ? 'week' : 'month'
  switch (rollup) {
    case 'SUM':
      return `Add them up — the ${bucket} shows the total`
    case 'AVERAGE':
      return `Average them — the ${bucket} shows the middle`
    case 'LATEST':
      return `Keep the last — the ${bucket} shows the most recent`
  }
}

export function describeScope(scope: TrackerScope): string {
  return scope === 'PER_MEMBER'
    ? 'Everyone keeps their own series, drawn together on one chart'
    : 'One series the household writes together'
}
