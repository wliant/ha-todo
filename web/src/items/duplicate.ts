import type { DashboardItem } from '../api/dashboard'
import type { Item, SaveItem } from '../api/items'

/**
 * A new item that starts as a copy of an existing one (0018 R153).
 *
 * <p>It carries **the description and the detail** and nothing else. Those two
 * are the writing - a paragraph of instructions, a shopping list in the detail
 * field - and they are what makes somebody re-type instead of adding the chore
 * again. Everything else is what makes one occurrence *this* occurrence: when
 * it is due, whose it is, what it cost. Copying those would produce a second
 * chore already dated for a day that has gone, assigned to whoever did the
 * first one (R155).
 *
 * <p>No `id` and no `version`, so the caller's save is a create. One function
 * for both surfaces, because "what a duplicate copies" must be one answer: a
 * second copy here is how the list and the agenda come to disagree about it.
 */
export function duplicateOf(item: Item | DashboardItem): SaveItem {
  return {
    description: item.description,
    detailHtml: item.detailHtml,
    assigneeId: null,
    dueAt: null,
    costValues: [],
  }
}
