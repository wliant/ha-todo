import { describe, expect, it } from 'vitest'
import { describeProblem, newCostField, withCostMode } from './fieldConfig'
import type { ListField } from '../api/lists'

function cost(overrides: Partial<ListField>): ListField {
  return {
    id: null,
    kind: 'COST',
    label: 'Time',
    required: false,
    costMode: 'FIXED',
    costUnitLabel: null,
    pointsPerUnit: null,
    choices: [],
    ...overrides,
  }
}

describe('describeProblem', () => {
  it('passes a configuration with no costs at all', () => {
    expect(describeProblem([{ ...cost({}), kind: 'DUE_AT', label: null }])).toBeNull()
  })

  it('names the cost that has nothing to pick from', () => {
    expect(describeProblem([cost({ label: 'Time', choices: [] })])).toContain('Time')
  })

  it('names the cost that is not measured in anything', () => {
    expect(
      describeProblem([cost({ label: 'Money', costMode: 'NUMERIC', costUnitLabel: ' ' })]),
    ).toContain('Money')
  })

  it('refuses two costs sharing a name, whatever the capitals', () => {
    const problem = describeProblem([
      cost({ label: 'Time', costMode: 'NUMERIC', costUnitLabel: 'hour' }),
      cost({ label: 'time', costMode: 'NUMERIC', costUnitLabel: 'hour' }),
    ])
    expect(problem).toContain('time')
  })

  it('refuses two choices on one cost sharing a name', () => {
    expect(
      describeProblem([
        cost({
          choices: [
            { id: null, label: '30 min', points: 1 },
            { id: null, label: '30 MIN', points: 2 },
          ],
        }),
      ]),
    ).toContain('30 MIN')
  })

  it('accepts a cost that records a number without scoring it', () => {
    expect(
      describeProblem([
        cost({ costMode: 'NUMERIC', costUnitLabel: 'hour', pointsPerUnit: null }),
      ]),
    ).toBeNull()
  })
})

describe('withCostMode', () => {
  it('drops the choices when a cost becomes a number', () => {
    // Otherwise the row would describe two different questions at once, which
    // is also what the database refuses.
    const switched = withCostMode(
      cost({ choices: [{ id: null, label: '5 min', points: 1 }] }),
      'NUMERIC',
    )

    expect(switched.costMode).toBe('NUMERIC')
    expect(switched.choices).toHaveLength(0)
  })

  it('drops the unit and rate when a number becomes a set of choices', () => {
    const switched = withCostMode(
      cost({ costMode: 'NUMERIC', costUnitLabel: 'hour', pointsPerUnit: 1000 }),
      'FIXED',
    )

    expect(switched.costUnitLabel).toBeNull()
    expect(switched.pointsPerUnit).toBeNull()
    expect(switched.choices.length).toBeGreaterThan(0)
  })

  it('leaves a cost alone when the mode has not changed', () => {
    const unchanged = cost({ choices: [{ id: null, label: 'x', points: 3 }] })
    expect(withCostMode(unchanged, 'FIXED')).toBe(unchanged)
  })

  it('keeps choices the member already edited rather than resetting them', () => {
    const edited = cost({
      costMode: 'NUMERIC',
      costUnitLabel: 'hour',
      choices: [],
    })
    const back = withCostMode(withCostMode(edited, 'FIXED'), 'NUMERIC')
    expect(back.choices).toHaveLength(0)
  })
})

describe('newCostField', () => {
  it('starts from the example the requirement itself uses', () => {
    expect(newCostField('Time').choices.map((choice) => choice.label)).toEqual([
      '5 min',
      '30 min',
      '60 min',
    ])
  })
})
