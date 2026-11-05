package com.hatodo.list;

/**
 * The curated icon set (R18). A list is recognised at a glance on a phone, so
 * the icon is required and chosen from a fixed set rather than typed.
 *
 * <p>Typing the request component as this enum means an unknown token is a 400
 * from Jackson before any service runs, so the column can only ever hold
 * something the frontend has a component for. The frontend mirrors these
 * tokens in {@code web/src/lists/listIcons.tsx} and falls back to the default
 * for anything it does not recognise, so the two drifting degrades to a
 * generic icon rather than a crash.
 */
public enum ListIcon {
    CHECKLIST,
    SHOPPING_CART,
    GROCERIES,
    RESTAURANT,
    PAYMENTS,
    SAVINGS,
    RECEIPT,
    HEALTH,
    MEDICATION,
    FITNESS,
    RUNNING,
    SCHOOL,
    READING,
    HOME,
    CLEANING,
    REPAIRS,
    GARDEN,
    PETS,
    CHILDREN,
    TRAVEL
}
