import Box from '@mui/material/Box'
import Chip from '@mui/material/Chip'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Tooltip from '@mui/material/Tooltip'
import Typography from '@mui/material/Typography'
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded'
import EditRounded from '@mui/icons-material/EditRounded'
import type { Reading } from '../api/trackerReadings'
import type { TrackerPeriod, TrackerScope } from '../api/trackers'
import { MemberAvatar } from '../app/MemberAvatar'
import { civilFromKey, describePeriod } from './period'
import { formatAmount } from './series'

/**
 * The numbers underneath the chart, newest first (requirements/0019 R167).
 *
 * <p>Every row's controls come from the server's `editable` flag and are never
 * recomputed from the scope plus the signed-in member (R165): one predicate,
 * one answer. A row somebody may not correct simply has no buttons, rather
 * than buttons that 403.
 */
export function ReadingsTable({
  readings,
  period,
  scope,
  unit,
  decimals,
  onEdit,
  onDelete,
}: {
  readings: Reading[]
  period: TrackerPeriod
  scope: TrackerScope
  unit: string
  decimals: number
  onEdit: (reading: Reading) => void
  onDelete: (reading: Reading) => void
}) {
  if (readings.length === 0) {
    return (
      <Typography role="status" color="text.secondary">
        Nothing recorded yet.
      </Typography>
    )
  }

  return (
    <Stack spacing={1}>
      {readings.map((reading) => (
        <Paper key={reading.id} variant="outlined" sx={{ p: 1.5 }}>
          <Stack
            direction="row"
            spacing={1.5}
            sx={{ alignItems: 'center', flexWrap: 'wrap', gap: 1 }}
          >
            <Box sx={{ minWidth: '5rem' }}>
              <Typography sx={{ fontVariantNumeric: 'tabular-nums' }}>
                {formatAmount(reading.amount, decimals)} {unit}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {describePeriod(civilFromKey(reading.periodStart), period)}
              </Typography>
            </Box>

            {/* Whose number it is, on a per-member tracker. A shared tracker's
                series belongs to the household, so naming a member on every
                row would imply a distinction that does not exist - the author
                is shown as a caption instead. */}
            {scope === 'PER_MEMBER' && reading.memberDisplayName && (
              <Chip
                size="small"
                avatar={
                  <MemberAvatar
                    displayName={reading.memberDisplayName}
                    avatarImageId={reading.memberAvatarImageId}
                    size={24}
                  />
                }
                label={reading.memberDisplayName}
              />
            )}

            {reading.note && (
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ flex: 1, minWidth: '6rem', overflowWrap: 'anywhere' }}
              >
                {reading.note}
              </Typography>
            )}

            {scope === 'SHARED' && reading.recordedByDisplayName && (
              <Typography variant="caption" color="text.secondary">
                by {reading.recordedByDisplayName}
              </Typography>
            )}

            {reading.editable && (
              <Stack direction="row" spacing={0.5} sx={{ ml: 'auto' }}>
                <Tooltip title="Edit this number">
                  <IconButton
                    size="small"
                    aria-label={`Edit the reading of ${formatAmount(reading.amount, decimals)}`}
                    onClick={() => onEdit(reading)}
                  >
                    <EditRounded fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Tooltip title="Delete this number">
                  <IconButton
                    size="small"
                    aria-label={`Delete the reading of ${formatAmount(reading.amount, decimals)}`}
                    onClick={() => onDelete(reading)}
                  >
                    <DeleteOutlineRounded fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Stack>
            )}
          </Stack>
        </Paper>
      ))}
    </Stack>
  )
}
