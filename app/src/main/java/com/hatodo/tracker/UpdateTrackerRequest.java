package com.hatodo.tracker;

import com.hatodo.list.ListIcon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * What an owner may change about a tracker.
 *
 * <p>{@code period} and {@code scope} are here and are accepted only while the
 * tracker holds no reading (R161) - the service refuses otherwise, with 409
 * {@code TRACKER_IN_USE}. They are not simply omitted from this record because
 * a member who picks the wrong period before recording anything needs a way
 * out, and because a request that silently dropped them would be worse than one
 * that refuses.
 */
public record UpdateTrackerRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 80) String name,
        @Size(max = 500) String description,
        @NotNull ListIcon icon,
        @NotBlank @Size(max = 16) String unit,
        @NotNull TrackerPeriod period,
        @NotNull TrackerScope scope,
        @NotNull Rollup rollup,
        Integer decimals,
        BigDecimal target,
        TargetDirection targetDirection) {

    public short decimalsOrDefault() {
        return decimals == null ? 1 : decimals.shortValue();
    }
}
