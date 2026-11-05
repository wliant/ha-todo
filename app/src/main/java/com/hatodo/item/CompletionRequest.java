package com.hatodo.item;

import jakarta.validation.constraints.NotNull;

/**
 * @param completed what the member is asking for, not a toggle of what is
 * @param noteHtml  how the work went (R37), carried on the completion itself
 *                  rather than by a second write - the gesture is one intent,
 *                  and a separate request would leave a window in which the
 *                  item is done with no note (adr/0023)
 */
public record CompletionRequest(@NotNull Long version, @NotNull Boolean completed,
                                String noteHtml) {
}
