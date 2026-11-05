import Box from '@mui/material/Box'
import ToggleButton from '@mui/material/ToggleButton'
import Typography from '@mui/material/Typography'
import type { ListIcon as ListIconToken } from '../api/lists'
import { ICON_TOKENS, iconLabel } from './listIconTokens'
import { ListIcon } from './listIcons'

/**
 * A grid of icon-only buttons. Each carries its label as its accessible name,
 * so the only way to pick one - by sight, by screen reader, or by a test - is
 * the same way.
 */
export function IconPicker({
  value,
  onChange,
}: {
  value: ListIconToken
  onChange: (icon: ListIconToken) => void
}) {
  return (
    <Box>
      <Typography component="h3" variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
        Icon
      </Typography>
      <Box
        role="radiogroup"
        aria-label="Icon"
        sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}
      >
        {ICON_TOKENS.map((token) => (
          <ToggleButton
            key={token}
            value={token}
            selected={token === value}
            onChange={() => onChange(token)}
            aria-label={iconLabel(token)}
            size="small"
            sx={{ borderRadius: 2 }}
          >
            <ListIcon token={token} />
          </ToggleButton>
        ))}
      </Box>
    </Box>
  )
}
