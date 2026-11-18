package com.hatodo.tracker;

/**
 * The caller can see this reading but may not correct it: it belongs to
 * somebody else's series on a per-member tracker (requirements/0019 R165).
 *
 * <p>403 rather than 404, because the reading is not hidden from them - they
 * are looking at it on the chart. The 404 case is the tracker being invisible,
 * and the two are asked in that order.
 */
public class ReadingNotWritableException extends RuntimeException {
}
