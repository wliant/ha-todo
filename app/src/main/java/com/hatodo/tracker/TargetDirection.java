package com.hatodo.tracker;

/**
 * Which side of a target is good (requirements/0019 R168).
 *
 * <p>Stored beside the figure under an all-or-nothing check, because "70 kg" is
 * not a goal until you know whether you are trying to stay under it or reach
 * it - and a headline that said "2.4 away" without knowing would be as likely
 * to congratulate as to warn.
 */
public enum TargetDirection {
    AT_MOST,
    AT_LEAST
}
