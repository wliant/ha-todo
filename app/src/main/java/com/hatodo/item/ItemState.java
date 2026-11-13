package com.hatodo.item;

/**
 * What an item currently is (0015 R131).
 *
 * <p>Three values, not a pair of booleans: the two terminal states are
 * mutually exclusive as a reading even though the columns behind them are not
 * (an item completed, reopened and then lapsed carries both stamps), and a
 * caller that has to combine two flags to find out is a caller that will
 * eventually combine them wrongly.
 *
 * <p>This is deliberately <strong>not</strong> a per-list declared field -
 * {@code adr/0046} declined that and still does. It is one more state the
 * product itself models, and {@code adr/0048} is where its relationship to
 * completion is written down.
 */
public enum ItemState {

    /** Still work. The only state the agenda and the overdue strip show. */
    OPEN,

    /** Done, by somebody, who was credited the points it was worth. */
    COMPLETED,

    /**
     * Planned, and nobody got to it. Terminal, worth nothing, and reopenable
     * exactly as a completion is.
     */
    LAPSED
}
