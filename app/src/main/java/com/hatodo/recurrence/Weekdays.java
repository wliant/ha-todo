package com.hatodo.recurrence;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/**
 * The seven weekday flags of a WEEKLY rule, as one small integer.
 *
 * <p>Monday is bit 0 through Sunday bit 6, which is {@link DayOfWeek}'s own
 * ordering shifted by one - so the mapping is arithmetic rather than a table.
 * A child table would be three joins to answer "does this fire on Thursday",
 * for a set that is fixed at seven booleans and never queried by day.
 */
final class Weekdays {

    static final int NONE = 0;
    static final int ALL = 0b111_1111;

    private Weekdays() {
    }

    static int bit(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }

    static boolean includes(int mask, DayOfWeek day) {
        return (mask & bit(day)) != 0;
    }

    static int of(Set<DayOfWeek> days) {
        int mask = NONE;
        for (DayOfWeek day : days) {
            mask |= bit(day);
        }
        return mask;
    }

    static Set<DayOfWeek> toSet(int mask) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (includes(mask, day)) {
                days.add(day);
            }
        }
        return days;
    }

    static boolean isValid(int mask) {
        return mask > NONE && mask <= ALL;
    }
}
