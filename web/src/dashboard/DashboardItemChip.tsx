import ButtonBase from '@mui/material/ButtonBase'
import Typography from '@mui/material/Typography'
import type { DashboardItem } from '../api/dashboard'

/**
 * One item in the week or month view: its description, and nothing else.
 *
 * <p>R44 is explicit that these views show descriptions only - a date grid with
 * chips in it is unreadable on a phone. Everything else is one tap away in the
 * popup.
 */
export function DashboardItemChip({
  item,
  onOpen,
}: {
  item: DashboardItem
  onOpen: () => void
}) {
  return (
    <ButtonBase
      onClick={onOpen}
      aria-label={`${item.description}, on ${item.listName}`}
      sx={{
        width: '100%',
        justifyContent: 'flex-start',
        textAlign: 'left',
        borderRadius: 1,
        px: 0.5,
        py: 0.25,
        bgcolor: item.selfAssigned ? 'selfClaimed.main' : 'action.hover',
        color: item.selfAssigned ? 'selfClaimed.contrastText' : 'text.primary',
        '&:hover': { filter: 'brightness(0.95)' },
      }}
    >
      <Typography
        variant="caption"
        sx={{
          display: 'block',
          width: '100%',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        {item.description}
      </Typography>
    </ButtonBase>
  )
}
