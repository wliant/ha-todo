import type { TrackerSeries } from '../api/trackers'
import { civilFromKey, describePeriod } from './period'

/**
 * Server series into chart props (requirements/0019 R167).
 *
 * <p>Pure, and tested rather than the chart: `@mui/x-charts` changes its markup
 * between minor versions, so an assertion on rendered SVG is a time bomb
 * (adr/0053). Everything worth asserting about what the chart will draw is
 * decided here.
 */

export interface ChartLine {
  id: string
  label: string
  /** Aligned with `labels`; null is a gap the chart draws as a break. */
  data: (number | null)[]
}

export interface ChartData {
  labels: string[]
  lines: ChartLine[]
  /** Bars when the tracker sums, a line otherwise - spend is bars, weight is a trend. */
  bars: boolean
  empty: boolean
}

export function toChart(series: TrackerSeries): ChartData {
  return {
    labels: series.buckets.map((bucket) => describePeriod(civilFromKey(bucket), series.by)),
    lines: series.series.map((line) => ({
      // A shared tracker's one line has no member id; 'everyone' is a stable
      // key for it rather than an index, which would change identity whenever
      // another line appears.
      id: line.memberId ?? 'everyone',
      label: line.displayName,
      data: line.values,
    })),
    bars: series.rollup === 'SUM',
    // Not "no buckets": the axis is always dense, so an empty chart is one
    // where every bucket in it is a gap (R167).
    empty: series.series.every((line) => line.values.every((value) => value === null)),
  }
}

export interface Headline {
  /** The most recent recorded value across the series, or null if there is none. */
  latest: number | null
  /** The bucket that value is in, as a label. */
  latestLabel: string | null
  /** The one before it, so the screen can say which way it moved. */
  previous: number | null
}

/**
 * The figure at the top of the screen.
 *
 * <p>Read from the **first** line, which is the household's on a shared
 * tracker and the first member with readings on a per-member one. A headline
 * that averaged several people's weights would be a number describing nobody.
 */
export function headline(series: TrackerSeries): Headline {
  const line = series.series[0]
  if (!line) {
    return { latest: null, latestLabel: null, previous: null }
  }
  const recorded: { value: number; index: number }[] = []
  line.values.forEach((value, index) => {
    if (value !== null) {
      recorded.push({ value, index })
    }
  })
  const last = recorded[recorded.length - 1]
  const before = recorded[recorded.length - 2]
  return {
    latest: last ? last.value : null,
    latestLabel: last
      ? describePeriod(civilFromKey(series.buckets[last.index]), series.by)
      : null,
    previous: before ? before.value : null,
  }
}

/**
 * How far the latest figure is from the target, and whether that is good news.
 *
 * <p>Needs both halves of the target, which the schema guarantees arrive
 * together: a figure with no direction cannot be read.
 */
export function againstTarget(
  latest: number | null,
  series: Pick<TrackerSeries, 'target' | 'targetDirection'>,
): { distance: number; met: boolean } | null {
  if (latest === null || series.target === null || series.targetDirection === null) {
    return null
  }
  const distance = latest - series.target
  return {
    distance: Math.abs(distance),
    met: series.targetDirection === 'AT_MOST' ? distance <= 0 : distance >= 0,
  }
}

/** Formats a number the way its tracker asked to be read. */
export function formatAmount(amount: number, decimals: number): string {
  return amount.toLocaleString(undefined, {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })
}
