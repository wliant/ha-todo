package com.hatodo.tracker;

/**
 * Whose numbers a tracker holds (requirements/0019 R160).
 *
 * <p>{@link #PER_MEMBER}: everyone with access keeps a series of their own, and
 * the chart overlays them. {@link #SHARED}: one series the household writes
 * together, stored as a NULL {@code member_id} on each reading - NULL
 * <em>is</em> "the household's", with no companion boolean (adr/0052).
 *
 * <p>Fixed once a tracker holds a reading (R161): PER_MEMBER to SHARED would
 * silently merge four people's weights into one line.
 */
public enum TrackerScope {
    PER_MEMBER,
    SHARED
}
