package com.hatodo.recurrence;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Which dates a rule falls on, inside a window.
 *
 * <p>Pure: no clock, no repository, no Spring state. This is where the
 * arithmetic that is easy to get wrong lives - interval anchoring, a 31st in
 * February, a daylight-saving boundary - so it is the piece that can be
 * exhaustively unit tested without a database.
 */
@Component
public class RecurrenceCalculator {

    /**
     * Every date the rule falls on in {@code [from, to]}, inclusive both ends.
     *
     * <p>Bounded by the rule's own window as well: nothing before
     * {@code startsOn}, nothing after {@code endsOn}, nothing at all while it
     * is paused.
     */
    public List<LocalDate> occurrences(RecurrenceRule rule, LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        if (rule.isPaused() || from.isAfter(to)) {
            return dates;
        }

        // The rule's window narrows the request; it never widens it.
        LocalDate first = from.isBefore(rule.getStartsOn()) ? rule.getStartsOn() : from;
        LocalDate last = rule.getEndsOn() != null && rule.getEndsOn().isBefore(to)
                ? rule.getEndsOn()
                : to;
        if (first.isAfter(last)) {
            return dates;
        }

        switch (rule.getFrequency()) {
            case DAILY -> daily(rule, first, last, dates);
            case WEEKLY -> weekly(rule, first, last, dates);
            case MONTHLY -> monthly(rule, first, last, dates);
        }
        return dates;
    }

    private void daily(RecurrenceRule rule, LocalDate first, LocalDate last,
                       List<LocalDate> dates) {
        int interval = rule.getIntervalCount();
        // Anchored on startsOn, so "every third day" means every third day from
        // the day the member chose - not from an arbitrary epoch, and not from
        // whenever the job happens to run.
        long offset = ChronoUnit.DAYS.between(rule.getStartsOn(), first);
        long skip = offset % interval == 0 ? 0 : interval - (offset % interval);
        for (LocalDate date = first.plusDays(skip); !date.isAfter(last);
                date = date.plusDays(interval)) {
            dates.add(date);
        }
    }

    private void weekly(RecurrenceRule rule, LocalDate first, LocalDate last,
                        List<LocalDate> dates) {
        int interval = rule.getIntervalCount();
        // Anchored on the Monday of startsOn's week. Using the ISO week number
        // instead would make "every other week" flip at the turn of a year,
        // where week 52 can be followed by week 1 of a 53-week year.
        LocalDate anchor = rule.getStartsOn().with(DayOfWeek.MONDAY);
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (!Weekdays.includes(rule.getDaysOfWeek(), date.getDayOfWeek())) {
                continue;
            }
            long weeks = ChronoUnit.WEEKS.between(anchor, date.with(DayOfWeek.MONDAY));
            if (weeks % interval == 0) {
                dates.add(date);
            }
        }
    }

    private void monthly(RecurrenceRule rule, LocalDate first, LocalDate last,
                         List<LocalDate> dates) {
        int interval = rule.getIntervalCount();
        YearMonth anchor = YearMonth.from(rule.getStartsOn());
        for (YearMonth month = YearMonth.from(first);
                !month.isAfter(YearMonth.from(last));
                month = month.plusMonths(1)) {
            if (ChronoUnit.MONTHS.between(anchor, month) % interval != 0) {
                continue;
            }
            // "The 31st" in February means the last day of February, which is
            // what a person means. Skipping the month is the other defensible
            // reading, and silently choosing it is how a missed rent reminder
            // happens - so the choice is written down here and in adr/0025.
            int day = Math.min(rule.getDayOfMonth(), month.lengthOfMonth());
            LocalDate date = month.atDay(day);
            if (!date.isBefore(first) && !date.isAfter(last)) {
                dates.add(date);
            }
        }
    }
}
