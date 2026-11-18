package com.hatodo.tracker;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One number, and which bucket it belongs to.
 *
 * <p>{@code periodStart} is a civil date - {@code YYYY-MM-DD} - and never an
 * instant. The service canonicalises it to the tracker's bucket start, so a
 * monthly tracker sent the 18th stores the 1st; a client cannot open a second
 * November by sending a different day of it (adr/0052).
 *
 * <p>There is deliberately <strong>no member id</strong>. Which series a
 * reading lands in is derived from the tracker's scope and the caller, so there
 * is no parameter to tamper with (R165) - the same shape as {@code /api/me/*}
 * carrying no user id.
 *
 * <p>{@code version} is sent when editing and ignored when creating.
 */
public record SaveReadingRequest(
        Long version,
        @NotNull LocalDate periodStart,
        @NotNull BigDecimal amount,
        @Size(max = 200) String note) {
}
