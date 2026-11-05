package com.hatodo.data;

import com.hatodo.list.ListIcon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** @param version the version the caller drew their view from (R60's promise). */
public record UpdateDataListRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 80) String name,
        @Size(max = 500) String description,
        @NotNull ListIcon icon) {
}
