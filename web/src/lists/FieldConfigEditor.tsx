import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Checkbox from '@mui/material/Checkbox'
import FormControlLabel from '@mui/material/FormControlLabel'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Switch from '@mui/material/Switch'
import Typography from '@mui/material/Typography'
import AddRounded from '@mui/icons-material/AddRounded'
import type { FieldKind, ListField } from '../api/lists'
import { CostFieldEditor } from './CostFieldEditor'
import { OPTIONAL_KINDS, emptyField, newCostField } from './fieldConfig'

/**
 * What a list's items will be asked for (R21).
 *
 * Enabling a field is the field existing; there is no separate flag, which is
 * also how the server stores it. Description is shown as a locked row rather
 * than hidden, so the shape of an item is complete on the screen.
 */
export function FieldConfigEditor({
  fields,
  onChange,
}: {
  fields: ListField[]
  onChange: (fields: ListField[]) => void
}) {
  const costs = fields.filter((field) => field.kind === 'COST')

  function replaceKind(kind: FieldKind, next: ListField | null) {
    const without = fields.filter((field) => field.kind !== kind)
    onChange(next ? [...without, next] : without)
  }

  function replaceCost(position: number, next: ListField | null) {
    const others = fields.filter((field) => field.kind !== 'COST')
    const updated = costs
      .map((cost, at) => (at === position ? next : cost))
      .filter((cost): cost is ListField => cost !== null)
    onChange([...others, ...updated])
  }

  return (
    <Stack spacing={2}>
      <Box>
        <Typography component="h3" variant="subtitle2" color="text.secondary">
          What an item looks like
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Every item has a description. Add whatever else this list needs.
        </Typography>
      </Box>

      <Paper variant="outlined" sx={{ p: 2 }}>
        <Stack spacing={1}>
          <FormControlLabel
            control={<Switch checked disabled />}
            label="Description"
            // Not a choice: an item with nothing written on it is not an item.
            slotProps={{ typography: { color: 'text.primary' } }}
          />
          <Typography variant="body2" color="text.secondary" sx={{ pl: 6 }}>
            Always on, always required.
          </Typography>
        </Stack>
      </Paper>

      {OPTIONAL_KINDS.map(({ kind, label, hint }) => {
        const field = fields.find((candidate) => candidate.kind === kind)
        return (
          <Paper key={kind} variant="outlined" sx={{ p: 2 }}>
            <Stack spacing={1}>
              <FormControlLabel
                control={
                  <Switch
                    checked={Boolean(field)}
                    onChange={(event) =>
                      replaceKind(kind, event.target.checked ? emptyField(kind) : null)
                    }
                  />
                }
                label={label}
              />
              <Typography variant="body2" color="text.secondary" sx={{ pl: 6 }}>
                {hint}
              </Typography>
              {field && (
                <FormControlLabel
                  sx={{ pl: 5 }}
                  control={
                    <Checkbox
                      checked={field.required}
                      onChange={(event) =>
                        replaceKind(kind, { ...field, required: event.target.checked })
                      }
                    />
                  }
                  label={`${label} is required`}
                />
              )}
            </Stack>
          </Paper>
        )
      })}

      {costs.map((cost, position) => (
        <CostFieldEditor
          key={cost.id ?? `new-cost-${position}`}
          field={cost}
          index={position}
          onChange={(next) => replaceCost(position, next)}
          onRemove={() => replaceCost(position, null)}
        />
      ))}

      <Box>
        <Button
          startIcon={<AddRounded />}
          onClick={() => onChange([...fields, newCostField('')])}
        >
          Add a cost
        </Button>
      </Box>
    </Stack>
  )
}
