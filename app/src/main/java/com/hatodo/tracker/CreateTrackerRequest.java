package com.hatodo.tracker;

import com.hatodo.list.ListIcon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * @param icon typed as the enum, so an unknown token is a 400 from Jackson
 *             before any service runs - and the same for period, scope and
 *             rollup
 * @param decimals boxed, because Jackson rejects a missing primitive in a
 *                 record and "not mentioned" has to mean the default
 * @param memberIds who else may see it, and therefore every series on it
 *                  (R164). The owner is added regardless.
 */
public record CreateTrackerRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 500) String description,
        @NotNull ListIcon icon,
        @NotBlank @Size(max = 16) String unit,
        @NotNull TrackerPeriod period,
        @NotNull TrackerScope scope,
        @NotNull Rollup rollup,
        Integer decimals,
        BigDecimal target,
        TargetDirection targetDirection,
        List<UUID> memberIds) {

    public short decimalsOrDefault() {
        return decimals == null ? 1 : decimals.shortValue();
    }

    public List<UUID> memberIdsOrEmpty() {
        return memberIds == null ? List.of() : memberIds;
    }
}
