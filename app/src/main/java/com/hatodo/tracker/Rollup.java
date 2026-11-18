package com.hatodo.tracker;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * How several numbers become one (requirements/0019 R163).
 *
 * <p>Used in two places and implemented once, which is the whole reason it is a
 * value rather than two switches: it folds the readings <em>inside</em> one
 * bucket (three shopping trips make one November), and it folds whole buckets
 * when a long range is drawn coarsely (a year of a daily weight, drawn by
 * month). A second implementation of either is how the chart and the headline
 * come to disagree about the same tracker.
 *
 * <p>{@link #LATEST} takes the last by the order it is given, so callers hand
 * it values already ordered by {@code recorded_at} within a bucket and by
 * bucket start across them. That ordering lives in the query and in the fold,
 * never here.
 */
public enum Rollup {
    SUM,
    AVERAGE,
    LATEST;

    /**
     * Folds ordered values into one.
     *
     * @param ordered oldest first; never empty - an empty bucket is a gap and a
     *                gap is {@code null}, decided by the caller rather than
     *                invented here as a zero (R167).
     */
    public BigDecimal fold(List<BigDecimal> ordered) {
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("A fold needs at least one value");
        }
        return switch (this) {
            case SUM -> ordered.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case AVERAGE -> ordered.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(ordered.size()), MathContext.DECIMAL64);
            case LATEST -> ordered.get(ordered.size() - 1);
        };
    }
}
