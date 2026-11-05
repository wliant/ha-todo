package com.hatodo.recurrence;

import static org.assertj.core.api.Assertions.assertThat;

import com.hatodo.list.ListIcon;
import com.hatodo.list.TodoList;
import com.hatodo.user.AppUser;
import com.hatodo.user.UserRole;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic, which is where the bugs in a feature like this actually live.
 *
 * <p>Pure, so every case is a plain assertion: no database, no clock, no
 * Spring. Anchoring an interval, a 31st in February, and a rule's own window
 * narrowing the request are all easy to get subtly wrong and impossible to
 * notice from the screen until a chore appears on the wrong day.
 */
class RecurrenceCalculatorTest {

    private final RecurrenceCalculator calculator = new RecurrenceCalculator();
    private final AppUser ana = new AppUser("ana", "Ana", null, UserRole.MEMBER);
    private final TodoList list = new TodoList(ana, "Chores", null, ListIcon.CHECKLIST);

    private RecurrenceRule rule(LocalDate startsOn) {
        RecurrenceRule rule = new RecurrenceRule(list, "Bins", ana, (short) 0);
        rule.setStartsOn(startsOn);
        return rule;
    }

    private RecurrenceRule daily(LocalDate startsOn, int interval) {
        RecurrenceRule rule = rule(startsOn);
        rule.schedule(Frequency.DAILY, interval, null, null);
        return rule;
    }

    private RecurrenceRule weekly(LocalDate startsOn, int interval, DayOfWeek... days) {
        RecurrenceRule rule = rule(startsOn);
        rule.schedule(Frequency.WEEKLY, interval, Weekdays.of(Set.of(days)), null);
        return rule;
    }

    private RecurrenceRule monthly(LocalDate startsOn, int interval, int dayOfMonth) {
        RecurrenceRule rule = rule(startsOn);
        rule.schedule(Frequency.MONTHLY, interval, null, dayOfMonth);
        return rule;
    }

    private static LocalDate on(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    void dailyFallsOnEveryDay() {
        List<LocalDate> dates = calculator.occurrences(daily(on("2026-11-01"), 1),
                on("2026-11-03"), on("2026-11-05"));

        assertThat(dates).containsExactly(on("2026-11-03"), on("2026-11-04"), on("2026-11-05"));
    }

    @Test
    void everyThirdDayIsCountedFromTheDayItStarts() {
        // Not from an arbitrary epoch, and not from whenever the job runs: the
        // member chose the first day, so that is the anchor.
        List<LocalDate> dates = calculator.occurrences(daily(on("2026-11-01"), 3),
                on("2026-11-01"), on("2026-11-10"));

        assertThat(dates).containsExactly(on("2026-11-01"), on("2026-11-04"),
                on("2026-11-07"), on("2026-11-10"));
    }

    @Test
    void everyThirdDayStaysOnItsAnchorWhenTheWindowStartsMidCycle() {
        List<LocalDate> dates = calculator.occurrences(daily(on("2026-11-01"), 3),
                on("2026-11-05"), on("2026-11-08"));

        assertThat(dates).containsExactly(on("2026-11-07"));
    }

    @Test
    void weeklyFallsOnEachChosenDay() {
        // 2026-11-02 is a Monday.
        List<LocalDate> dates = calculator.occurrences(
                weekly(on("2026-11-02"), 1, DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
                on("2026-11-02"), on("2026-11-15"));

        assertThat(dates).containsExactly(on("2026-11-02"), on("2026-11-05"),
                on("2026-11-09"), on("2026-11-12"));
    }

    @Test
    void everyOtherWeekSkipsTheWeekBetween() {
        List<LocalDate> dates = calculator.occurrences(
                weekly(on("2026-11-02"), 2, DayOfWeek.MONDAY),
                on("2026-11-02"), on("2026-11-30"));

        assertThat(dates).containsExactly(on("2026-11-02"), on("2026-11-16"),
                on("2026-11-30"));
    }

    @Test
    void everyOtherWeekIsAnchoredOnItsOwnStartNotTheIsoWeekNumber() {
        // Across a new year, where week 52 can be followed by week 1 of a
        // 53-week year - which would flip the parity of an ISO-week scheme and
        // silently move every other week's chore.
        RecurrenceRule rule = weekly(on("2026-12-21"), 2, DayOfWeek.MONDAY);

        List<LocalDate> dates = calculator.occurrences(rule,
                on("2026-12-21"), on("2027-02-01"));

        assertThat(dates).containsExactly(on("2026-12-21"), on("2027-01-04"),
                on("2027-01-18"), on("2027-02-01"));
    }

    @Test
    void monthlyFallsOnItsDay() {
        List<LocalDate> dates = calculator.occurrences(monthly(on("2026-11-01"), 1, 15),
                on("2026-11-01"), on("2027-01-31"));

        assertThat(dates).containsExactly(on("2026-11-15"), on("2026-12-15"),
                on("2027-01-15"));
    }

    @Test
    void theThirtyFirstClampsToTheLastDayOfAShorterMonth() {
        // What a person means by "the 31st". Skipping the month is the other
        // defensible reading, and choosing it silently is how a missed rent
        // reminder happens - so it is asserted rather than assumed.
        List<LocalDate> dates = calculator.occurrences(monthly(on("2027-01-01"), 1, 31),
                on("2027-01-01"), on("2027-04-30"));

        assertThat(dates).containsExactly(on("2027-01-31"), on("2027-02-28"),
                on("2027-03-31"), on("2027-04-30"));
    }

    @Test
    void theThirtyFirstFindsTheTwentyNinthInALeapFebruary() {
        List<LocalDate> dates = calculator.occurrences(monthly(on("2028-02-01"), 1, 31),
                on("2028-02-01"), on("2028-02-29"));

        assertThat(dates).containsExactly(on("2028-02-29"));
    }

    @Test
    void everyOtherMonthIsCountedFromTheMonthItStarts() {
        List<LocalDate> dates = calculator.occurrences(monthly(on("2026-11-01"), 2, 1),
                on("2026-11-01"), on("2027-04-01"));

        assertThat(dates).containsExactly(on("2026-11-01"), on("2027-01-01"),
                on("2027-03-01"));
    }

    @Test
    void nothingFallsBeforeTheRuleStarts() {
        List<LocalDate> dates = calculator.occurrences(daily(on("2026-11-10"), 1),
                on("2026-11-01"), on("2026-11-12"));

        assertThat(dates).containsExactly(on("2026-11-10"), on("2026-11-11"),
                on("2026-11-12"));
    }

    @Test
    void theLastDayIsIncludedAndNothingAfterIt() {
        RecurrenceRule rule = daily(on("2026-11-01"), 1);
        rule.setEndsOn(on("2026-11-03"));

        List<LocalDate> dates = calculator.occurrences(rule, on("2026-11-01"), on("2026-11-10"));

        assertThat(dates).containsExactly(on("2026-11-01"), on("2026-11-02"),
                on("2026-11-03"));
    }

    @Test
    void aSpentRuleFallsOnNothing() {
        RecurrenceRule rule = daily(on("2026-11-01"), 1);
        rule.setEndsOn(on("2026-11-03"));

        assertThat(calculator.occurrences(rule, on("2026-11-04"), on("2026-11-10"))).isEmpty();
    }

    @Test
    void aPausedRuleFallsOnNothing() {
        RecurrenceRule rule = daily(on("2026-11-01"), 1);
        rule.setPaused(true);

        assertThat(calculator.occurrences(rule, on("2026-11-01"), on("2026-11-10"))).isEmpty();
    }

    @Test
    void anInsideOutWindowFallsOnNothing() {
        assertThat(calculator.occurrences(daily(on("2026-11-01"), 1),
                on("2026-11-10"), on("2026-11-01"))).isEmpty();
    }

    @Test
    void theWeekdayMaskRoundTrips() {
        Set<DayOfWeek> days = Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY);

        assertThat(Weekdays.toSet(Weekdays.of(days))).isEqualTo(days);
        assertThat(Weekdays.isValid(Weekdays.of(days))).isTrue();
        assertThat(Weekdays.isValid(Weekdays.NONE)).isFalse();
    }
}
