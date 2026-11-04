import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Divider from '@mui/material/Divider'
import FormControlLabel from '@mui/material/FormControlLabel'
import IconButton from '@mui/material/IconButton'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import TextField from '@mui/material/TextField'
import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded'
import type { CostMode, ListField } from '../api/lists'
import { withCostMode } from './fieldConfig'

/**
 * One named cost (R22, R23): either a set of labelled choices each worth
 * points, or a number in a unit the owner names with a rate per unit.
 */
export function CostFieldEditor({
  field,
  index,
  onChange,
  onRemove,
}: {
  field: ListField
  index: number
  onChange: (field: ListField) => void
  onRemove: () => void
}) {
  const name = field.label ?? ''
  const described = name.trim() || `Cost ${index + 1}`
  const earnsPoints =
    field.costMode === 'NUMERIC'
      ? field.pointsPerUnit !== null
      : field.choices.some((choice) => choice.points > 0)

  function setChoice(position: number, changes: Partial<ListField['choices'][number]>) {
    onChange({
      ...field,
      choices: field.choices.map((choice, at) =>
        at === position ? { ...choice, ...changes } : choice,
      ),
    })
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
          <TextField
            label="Cost name"
            size="small"
            required
            value={name}
            onChange={(event) => onChange({ ...field, label: event.target.value })}
            slotProps={{ htmlInput: { maxLength: 30 } }}
            helperText="Time, Money, Effort…"
            sx={{ flex: 1 }}
          />
          <IconButton aria-label={`Remove ${described}`} onClick={onRemove}>
            <DeleteOutlineRounded />
          </IconButton>
        </Stack>

        <ToggleButtonGroup
          exclusive
          size="small"
          value={field.costMode ?? 'FIXED'}
          onChange={(_event, mode: CostMode | null) =>
            mode && onChange(withCostMode(field, mode))
          }
          aria-label={`How ${described} is answered`}
        >
          <ToggleButton value="FIXED">Fixed choices</ToggleButton>
          <ToggleButton value="NUMERIC">A number</ToggleButton>
        </ToggleButtonGroup>

        {field.costMode === 'NUMERIC' ? (
          <Stack spacing={2}>
            <TextField
              label="Measured in"
              size="small"
              required
              value={field.costUnitLabel ?? ''}
              onChange={(event) => onChange({ ...field, costUnitLabel: event.target.value })}
              slotProps={{ htmlInput: { maxLength: 20 } }}
              helperText="dollar, hour, kilometre…"
            />

            <FormControlLabel
              control={
                <Switch
                  checked={earnsPoints}
                  onChange={(event) =>
                    onChange({ ...field, pointsPerUnit: event.target.checked ? 0 : null })
                  }
                />
              }
              label={`Earn points for ${described}`}
            />

            {earnsPoints && (
              <TextField
                label="Points per unit"
                size="small"
                type="number"
                value={field.pointsPerUnit ?? 0}
                onChange={(event) =>
                  onChange({ ...field, pointsPerUnit: Number(event.target.value) })
                }
                slotProps={{ htmlInput: { min: 0, step: 'any' } }}
                helperText={`1000 here means one ${
                  (field.costUnitLabel ?? 'unit').trim() || 'unit'
                } is worth 1000 points.`}
              />
            )}
          </Stack>
        ) : (
          <Stack spacing={1.5}>
            <Typography variant="subtitle2" color="text.secondary">
              Choices
            </Typography>

            {field.choices.map((choice, position) => (
              <Stack
                key={choice.id ?? `new-${position}`}
                direction="row"
                spacing={1}
                sx={{ alignItems: 'center' }}
              >
                <TextField
                  label="Choice"
                  size="small"
                  required
                  value={choice.label}
                  onChange={(event) => setChoice(position, { label: event.target.value })}
                  slotProps={{ htmlInput: { maxLength: 30 } }}
                  sx={{ flex: 1 }}
                />
                <TextField
                  label="Points"
                  size="small"
                  type="number"
                  value={choice.points}
                  onChange={(event) =>
                    setChoice(position, { points: Number(event.target.value) })
                  }
                  slotProps={{ htmlInput: { min: 0 } }}
                  sx={{ width: '7rem' }}
                />
                <IconButton
                  aria-label={`Remove choice ${choice.label || position + 1}`}
                  onClick={() =>
                    onChange({
                      ...field,
                      choices: field.choices.filter((_, at) => at !== position),
                    })
                  }
                >
                  <DeleteOutlineRounded fontSize="small" />
                </IconButton>
              </Stack>
            ))}

            <Box>
              <Button
                size="small"
                startIcon={<AddRounded />}
                onClick={() =>
                  onChange({
                    ...field,
                    choices: [...field.choices, { id: null, label: '', points: 0 }],
                  })
                }
              >
                Add a choice
              </Button>
            </Box>

            <Divider />
            <Typography variant="body2" color="text.secondary">
              Leave every choice at zero points to record the cost without scoring it.
            </Typography>
          </Stack>
        )}
      </Stack>
    </Paper>
  )
}
