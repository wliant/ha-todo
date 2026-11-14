package com.hatodo.recurrence;

import com.hatodo.item.ItemCostValueResponse;
import com.hatodo.item.ItemMemberResponse;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * @param lastError why the last run produced nothing, so the tab can say so
 *                  rather than leaving a rule silently broken - which is the
 *                  whole risk of something that runs unattended (adr/0025)
 * @param createdBy whoever wrote it, null once their account is gone. On screen
 *                  since 0016 R142: a list can now hold rules by four different
 *                  members, and "who decided this" is a real question
 * @param editable  whether <em>this</em> caller may write it (R144), so the
 *                  client renders a flag rather than keeping a second copy of
 *                  {@link RecurrenceRule#isWritableBy} in another language
 */
public record RecurrenceRuleResponse(
        UUID id, UUID listId, String description, String detailHtml,
        ItemMemberResponse assignee, boolean selfAssigned,
        Frequency frequency, int intervalCount, List<DayOfWeek> daysOfWeek,
        Integer dayOfMonth, LocalTime dueTime, int leadDays,
        LocalDate startsOn, LocalDate endsOn, boolean paused,
        LocalDate lastMaterialisedOn, Instant lastRunAt, String lastError,
        List<ItemCostValueResponse> costValues, ItemMemberResponse createdBy,
        boolean editable, long version) {

    /**
     * @param callerId whose view this is. Required rather than defaulted, so
     *                 every call site had to be revisited when it was added.
     */
    public static RecurrenceRuleResponse of(RecurrenceRule rule, UUID callerId) {
        return new RecurrenceRuleResponse(rule.getId(), rule.getListId(),
                rule.getDescription(), rule.getDetailHtml(),
                ItemMemberResponse.of(rule.getAssignee()), isSelfAssigned(rule),
                rule.getFrequency(), rule.getIntervalCount(),
                rule.getFrequency() == Frequency.WEEKLY
                        ? List.copyOf(Weekdays.toSet(rule.getDaysOfWeek()))
                        : List.of(),
                rule.getFrequency() == Frequency.MONTHLY ? rule.getDayOfMonth() : null,
                rule.getDueTime(), rule.getLeadDays(), rule.getStartsOn(), rule.getEndsOn(),
                rule.isPaused(), rule.getLastMaterialisedOn(), rule.getLastRunAt(),
                rule.getLastError(),
                rule.getCostValues().stream().map(value -> new ItemCostValueResponse(
                        value.getField().getId(),
                        value.getChoice() == null ? null : value.getChoice().getId(),
                        value.getAmount())).toList(),
                ItemMemberResponse.of(rule.getCreatedBy()),
                rule.isWritableBy(callerId),
                rule.getVersion());
    }

    private static boolean isSelfAssigned(RecurrenceRule rule) {
        return rule.getAssignee() != null && rule.getAssignedBy() != null
                && rule.getAssignee().getId().equals(rule.getAssignedBy().getId());
    }
}
