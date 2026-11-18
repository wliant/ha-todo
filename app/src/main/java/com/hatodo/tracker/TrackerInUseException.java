package com.hatodo.tracker;

/**
 * An attempt to change a tracker's period or scope after it holds a reading
 * (requirements/0019 R161).
 *
 * <p>Refused rather than migrated: month-to-day would strand every stored
 * bucket at a boundary that no longer means anything, and per-member-to-shared
 * would silently merge several people's numbers into one line. Both are
 * irreversible and neither has an obviously right answer.
 */
public class TrackerInUseException extends RuntimeException {

    public TrackerInUseException(String message) {
        super(message);
    }
}
