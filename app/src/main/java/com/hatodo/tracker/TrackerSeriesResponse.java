package com.hatodo.tracker;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the chart reads (requirements/0019 R167).
 *
 * <p>{@code buckets} is a <strong>dense</strong> axis the server computed, so
 * the client never iterates dates looking for holes - and a bucket nobody
 * recorded appears in every series as a {@code null} value, never a zero. A
 * missed weigh-in is not 0 kg, and a chart that drew it as one would invent a
 * crash.
 *
 * <p>{@code by} is the grain actually drawn, which may be coarser than the
 * tracker's own period when a long range is on screen. Never finer: folding
 * cannot invent days.
 */
public record TrackerSeriesResponse(UUID trackerId, String unit, TrackerPeriod period,
                                    Rollup rollup, TrackerScope scope, short decimals,
                                    BigDecimal target, TargetDirection targetDirection,
                                    TrackerPeriod by, LocalDate from, LocalDate to,
                                    List<LocalDate> buckets, List<Series> series) {

    /**
     * One line on the chart: a member's, or - on a shared tracker - the
     * household's, which carries a null {@code memberId} and a name the client
     * can print.
     */
    public record Series(UUID memberId, String displayName, UUID avatarImageId,
                         List<BigDecimal> values) {
    }
}
