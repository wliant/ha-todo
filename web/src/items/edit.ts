import type { DashboardItem } from '../api/dashboard'
import type { Item, SaveItem } from '../api/items'

/**
 * The form's starting value for an item that already exists (0020 R174).
 *
 * <p>The mirror of `duplicateOf`, and deliberately its opposite: an edit
 * carries **everything** - the date, the assignee, the costs - because a form
 * that opened missing one of them would write the blank back over it on the
 * next save. The version rides along for the same reason every other write
 * carries it: the save is refused rather than silently overwriting whoever
 * changed the item in between.
 *
 * <p>One function for both surfaces, as `duplicateOf` is. The list screen and
 * the agenda ask the same question - "what is this item, as a form" - and two
 * answers to it is how one of them comes to drop a field nobody notices.
 */
export function editableOf(item: Item | DashboardItem): SaveItem & { id: string } {
  return {
    id: item.id,
    version: item.version,
    description: item.description,
    detailHtml: item.detailHtml,
    assigneeId: item.assignee?.id ?? null,
    dueAt: item.dueAt,
    costValues: item.costValues,
  }
}
