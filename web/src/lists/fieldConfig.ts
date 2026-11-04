import type { CostChoice, CostMode, FieldKind, ListField } from '../api/lists'

/**
 * The optional fields, in the order the editor offers them. Description is
 * absent on purpose: it is always on and always required, so it is not a
 * choice anybody makes.
 */
export const OPTIONAL_KINDS: { kind: FieldKind; label: string; hint: string }[] = [
  {
    kind: 'DETAIL',
    label: 'Detail',
    hint: 'Room for steps, notes and pictures',
  },
  { kind: 'ASSIGNEE', label: 'Assignee', hint: 'Who it is for' },
  { kind: 'DUE_AT', label: 'Due date and time', hint: 'When it needs doing by' },
]

/** The example the requirement itself opens with, so a cost starts usable. */
export const STARTER_CHOICES: CostChoice[] = [
  { id: null, label: '5 min', points: 0 },
  { id: null, label: '30 min', points: 0 },
  { id: null, label: '60 min', points: 0 },
]

export function emptyField(kind: FieldKind): ListField {
  return {
    id: null,
    kind,
    label: null,
    required: false,
    costMode: null,
    costUnitLabel: null,
    pointsPerUnit: null,
    choices: [],
  }
}

export function newCostField(label: string): ListField {
  return {
    ...emptyField('COST'),
    label,
    costMode: 'FIXED',
    choices: STARTER_CHOICES.map((choice) => ({ ...choice })),
  }
}

/** Keeps the two cost modes from leaving each other's settings behind. */
export function withCostMode(field: ListField, mode: CostMode): ListField {
  if (mode === field.costMode) {
    return field
  }
  return mode === 'FIXED'
    ? {
        ...field,
        costMode: 'FIXED',
        costUnitLabel: null,
        pointsPerUnit: null,
        choices: field.choices.length > 0 ? field.choices : STARTER_CHOICES.map((c) => ({ ...c })),
      }
    : { ...field, costMode: 'NUMERIC', choices: [] }
}

/**
 * The same rules the server enforces, so the member is told before a round
 * trip. The server is still the authority - this only saves them a refusal.
 */
export function describeProblem(fields: ListField[]): string | null {
  const names = new Set<string>()
  for (const field of fields) {
    if (field.kind !== 'COST') {
      continue
    }
    const label = (field.label ?? '').trim()
    if (!label) {
      return 'Every cost needs a name.'
    }
    if (names.has(label.toLowerCase())) {
      return `Two costs cannot both be called ${label}.`
    }
    names.add(label.toLowerCase())

    if (field.costMode === 'NUMERIC') {
      if (!(field.costUnitLabel ?? '').trim()) {
        return `Say what ${label} is measured in - dollars, hours, kilometres.`
      }
    } else {
      if (field.choices.length === 0) {
        return `${label} needs at least one choice to pick from.`
      }
      const choiceNames = new Set<string>()
      for (const choice of field.choices) {
        const choiceLabel = choice.label.trim()
        if (!choiceLabel) {
          return `Every choice on ${label} needs a name.`
        }
        if (choiceNames.has(choiceLabel.toLowerCase())) {
          return `${label} has two choices called ${choiceLabel}.`
        }
        choiceNames.add(choiceLabel.toLowerCase())
      }
    }
  }
  return null
}
