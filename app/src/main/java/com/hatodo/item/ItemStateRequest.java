package com.hatodo.item;

import jakarta.validation.constraints.NotNull;

/**
 * Asking an item to become something (0015 R131).
 *
 * <p>Replaced {@code CompletionRequest}'s boolean when lapsing arrived, rather
 * than growing a second flag beside it. Two booleans can describe a state that
 * does not exist, and the old {@code completed: false} would have had to mean
 * both "reopen this" and "give up on this" depending on a field somewhere else.
 * Renaming the record and the endpoint together is what forced every caller to
 * be revisited.
 *
 * @param state    what the member is asking for, not a toggle of what is
 * @param noteHtml how the work went (R37), carried on the completion itself
 *                 rather than by a second write - the gesture is one intent,
 *                 and a separate request would leave a window in which the
 *                 item is done with no note (adr/0023). Only ever valid
 *                 alongside {@link ItemState#COMPLETED}: there is no note on a
 *                 lapse (R134) and none on a reopen.
 */
public record ItemStateRequest(@NotNull Long version, @NotNull ItemState state,
                               String noteHtml) {
}
