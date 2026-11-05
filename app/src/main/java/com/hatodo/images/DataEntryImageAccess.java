package com.hatodo.images;

import java.util.UUID;

/**
 * Whether the caller may read a picture that belongs to a collection entry
 * (requirements/0006 R61).
 *
 * <p>A second interface rather than a method on {@link ImageAccess}, for two
 * reasons. {@code ImageAccess} is implemented in {@code com.hatodo.item}, which
 * must not learn about collections. And {@link ImageController} injects
 * {@code ImageAccess} by type, so a second {@code @Component} implementing it
 * would be a {@code NoUniqueBeanDefinitionException} at startup rather than a
 * compile error. See adr/0032.
 */
public interface DataEntryImageAccess {

    boolean canReadEntry(UUID entryId);
}
