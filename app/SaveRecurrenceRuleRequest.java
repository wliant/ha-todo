package com.hatodo.recurrence;

import com.hatodo.item.ItemCostValueRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Writing a standing instruction (R41).
 *
 * <p>Boxed {@code Integer}/{@code Boolean} with defaulting accessors, because
 * Jackson rejects a missing primitive in a record and "not mentioned" is
 * genuinely different from zero here - the same reason the field configuration
 * requests are shaped this way.
 *
 * @param version null when creating; the rule's own version when editing
 * @param daysOfWeek required for WEEKLY and meaningless otherwise
 * @param dayOfMonth required for MONTHLY and meaningless otherwise
 */
public record SaveRecurrenceRuleRequest(
        Long version,
        @NotBlank @Size(max = 300) String description,
        String detailHtml,
        UUID assigneeId,
        @NotNull Frequency frequency,
        @Min(1) @Max(52) Integer intervalCount,
        List<java.time.DayOfWeek> daysOfWeek,
        @Min(1) @Max(31) Integer dayOfMonth,
        LocalTime dueTime,
        @Min(0) @Max(30) Integer leadDays,
        LocalDate startsOn,
        LocalDate endsOn,
        Boolean paused,
        @Valid List<ItemCostValueRequest> costValues) {

    public int intervalOrOne() {
        return intervalCount == null ? 1 : intervalCount;
    }

    public int leadDaysOrZero() {
        return leadDays == null ? 0 : leadDays;
    }

    public boolean isPaused() {
        return Boolean.TRUE.equals(paused);
    }

    public List<ItemCostValueRequest> costValuesOrEmpty() {
        return costValues == null ? List.of() : costValues;
    }

    public List<java.time.DayOfWeek> daysOrEmpty() {
        return daysOfWeek == null ? List.of() : daysOfWeek;
    }
}
