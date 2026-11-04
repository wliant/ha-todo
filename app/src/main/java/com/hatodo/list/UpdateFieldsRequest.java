package com.hatodo.list;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The whole configuration, in the order the fields should be asked for.
 *
 * @param version the version the caller's view was drawn from
 */
public record UpdateFieldsRequest(
        @NotNull Long version,
        @Valid @NotNull List<ListFieldRequest> fields) {
}
