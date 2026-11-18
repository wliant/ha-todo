package com.hatodo.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** How several numbers become one (requirements/0019 R163). */
class RollupTest {

    private static List<BigDecimal> values(String... amounts) {
        return List.of(amounts).stream().map(BigDecimal::new).toList();
    }

    @Test
    void sumAddsEveryReadingInTheBucket() {
        assertThat(Rollup.SUM.fold(values("142.00", "318.00", "520.00")))
                .isEqualByComparingTo("980.00");
    }

    @Test
    void averageDividesByHowManyThereWere() {
        assertThat(Rollup.AVERAGE.fold(values("72.6", "72.4")))
                .isEqualByComparingTo("72.5");
    }

    /**
     * LATEST takes the last of what it is given, which is why the ordering
     * lives in the query and in the fold rather than here: a rollup that sorted
     * for itself would be a second place the order is decided.
     */
    @Test
    void latestTakesTheLastValueItWasGiven() {
        assertThat(Rollup.LATEST.fold(values("72.6", "72.4")))
                .isEqualByComparingTo("72.4");
    }

    @Test
    void oneReadingFoldsToItselfWhateverTheRule() {
        for (Rollup rollup : Rollup.values()) {
            assertThat(rollup.fold(values("72.4"))).isEqualByComparingTo("72.4");
        }
    }

    /**
     * An empty bucket is a gap, and a gap is decided by the caller as null
     * (R167) rather than invented here as a zero - a missed weigh-in is not
     * 0 kg.
     */
    @Test
    void refusesToFoldNothingRatherThanInventAZero() {
        assertThatThrownBy(() -> Rollup.SUM.fold(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
