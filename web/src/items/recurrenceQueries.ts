import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createRule,
  deleteRule,
  fetchRules,
  runRules,
  updateRule,
  type SaveRecurrenceRule,
} from '../api/recurrence'
import { listKeys } from '../lists/queries'
import { itemKeys } from './queries'

export const ruleKeys = {
  all: (listId: string) => ['lists', 'detail', listId, 'recurrence'] as const,
}

export function useRules(listId: string | undefined) {
  return useQuery({
    queryKey: ruleKeys.all(listId ?? 'none'),
    queryFn: () => fetchRules(listId!),
    enabled: Boolean(listId),
  })
}

/**
 * Writing a rule invalidates the rules; running one invalidates the items too.
 *
 * <p>And the rules themselves after a run, because a run records when it last
 * happened - which moves the rule's version, making whatever the screen is
 * holding stale for the next edit.
 */
function useRuleInvalidation(listId: string) {
  const queryClient = useQueryClient()
  return (alsoItems = false) => {
    void queryClient.invalidateQueries({ queryKey: ruleKeys.all(listId) })
    if (alsoItems) {
      void queryClient.invalidateQueries({ queryKey: itemKeys.all(listId) })
      void queryClient.invalidateQueries({ queryKey: listKeys.all })
    }
  }
}

export function useCreateRule(listId: string) {
  const invalidate = useRuleInvalidation(listId)
  return useMutation({
    mutationFn: (rule: SaveRecurrenceRule) => createRule(listId, rule),
    onSuccess: () => invalidate(),
  })
}

export function useUpdateRule(listId: string) {
  const invalidate = useRuleInvalidation(listId)
  return useMutation({
    mutationFn: ({ id, ...rule }: SaveRecurrenceRule & { id: string }) => updateRule(id, rule),
    onSuccess: () => invalidate(),
  })
}

export function useDeleteRule(listId: string) {
  const invalidate = useRuleInvalidation(listId)
  return useMutation({
    mutationFn: ({ id, version }: { id: string; version: number }) => deleteRule(id, version),
    onSuccess: () => invalidate(),
  })
}

export function useRunRules(listId: string) {
  const invalidate = useRuleInvalidation(listId)
  return useMutation({
    mutationFn: () => runRules(listId),
    onSuccess: () => invalidate(true),
  })
}
