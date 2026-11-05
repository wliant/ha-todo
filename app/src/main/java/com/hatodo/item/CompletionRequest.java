package com.hatodo.item;

import jakarta.validation.constraints.NotNull;

/** @param completed what the member is asking for, not a toggle of what is */
public record CompletionRequest(@NotNull Long version, @NotNull Boolean completed) {
}
