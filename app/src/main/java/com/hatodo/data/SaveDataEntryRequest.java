package com.hatodo.data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Creating or editing an entry.
 *
 * <p>{@code version} is boxed and null when creating; {@code tags} is boxed
 * because Jackson rejects a missing primitive in a record, and because in a
 * request that replaces a set "not mentioned" is genuinely different from
 * "empty" - though here both are treated as empty, since an edit always sends
 * the whole entry.
 */
public record SaveDataEntryRequest(
        Long version,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String description,
        String detailHtml,
        List<String> tags) {

    public List<String> tagsOrEmpty() {
        return tags == null ? List.of() : tags;
    }
}
