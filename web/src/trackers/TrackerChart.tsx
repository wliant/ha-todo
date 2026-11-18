import Box from '@mui/material/Box'
import { useTheme } from '@mui/material/styles'
import { BarChart } from '@mui/x-charts/BarChart'
import { ChartsReferenceLine } from '@mui/x-charts/ChartsReferenceLine'
import { LineChart } from '@mui/x-charts/LineChart'
import type { TrackerSeries } from '../api/trackers'
import { toChart } from './series'

/**
 * The shape the numbers make (requirements/0019 R167, R168).
 *
 * <p><strong>This is the only module in the app that may import
 * `@mui/x-charts`</strong>, and every consumer reaches it through
 * `React.lazy`. The library and its bundled d3 are the heaviest dependency
 * here, most sessions never open a tracker, and a second import site anywhere
 * drags the whole thing into the main chunk - where nothing fails, no test goes
 * red, and every member pays for it on first load. See adr/0053.
 *
 * <p>Bars when the tracker sums and a line otherwise, derived from the tracker
 * rather than configured: monthly spend is bars, a weight trend is a line.
 *
 * <p>Colours come from the theme (adr/0014), so the chart follows the app into
 * dark mode without knowing anything about it.
 */
export function TrackerChart({
  series,
  height = 260,
}: {
  series: TrackerSeries
  height?: number
}) {
  const theme = useTheme()
  const chart = toChart(series)
  const palette = [
    theme.palette.primary.main,
    theme.palette.secondary.main,
    theme.palette.success.main,
    theme.palette.warning.main,
  ]

  const target =
    series.target === null ? null : (
      <ChartsReferenceLine
        y={series.target}
        label={`Target ${series.target}`}
        labelAlign="end"
        lineStyle={{
          stroke: theme.palette.text.secondary,
          strokeDasharray: '4 4',
        }}
      />
    )

  // One accessible name for the whole figure. Tests assert on this rather than
  // on the SVG, which x-charts rewrites between minor versions.
  const label = `${chart.bars ? 'Bar' : 'Line'} chart of ${series.unit}`

  return (
    <Box role="img" aria-label={label} sx={{ width: '100%' }}>
      {chart.bars ? (
        <BarChart
          height={height}
          colors={palette}
          xAxis={[{ scaleType: 'band', data: chart.labels }]}
          series={chart.lines.map((line) => ({
            id: line.id,
            label: line.label,
            // x-charts wants numbers in a bar series; a gap is a bar that is
            // not there, which `null` renders as nothing rather than as zero.
            data: line.data,
          }))}
        >
          {target}
        </BarChart>
      ) : (
        <LineChart
          height={height}
          colors={palette}
          xAxis={[{ scaleType: 'point', data: chart.labels }]}
          series={chart.lines.map((line) => ({
            id: line.id,
            label: line.label,
            data: line.data,
            showMark: true,
            // A gap is a break in the line, never a straight segment drawn
            // across days nobody recorded (R167).
            connectNulls: false,
          }))}
        >
          {target}
        </LineChart>
      )}
    </Box>
  )
}
