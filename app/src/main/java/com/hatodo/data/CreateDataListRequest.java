package com.hatodo.data;

import com.hatodo.list.ListIcon;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * @param icon typed as the enum, so an unknown token is a 400 from Jackson
 *             before any service runs
 * @param memberIds who else may see it. The owner is added regardless and need
 *                  not appear here.
 */
public record CreateDataListRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 500) String description,
        @NotNull ListIcon icon,
        List<UUID> memberIds) {

    public List<UUID> memberIdsOrEmpty() {
        return memberIds == null ? List.of() : memberIds;
    }
}
