package com.hatodo.data;

import com.hatodo.list.ListIcon;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Collections over HTTP. Translation only - every access decision is in the
 * service, because a collection id in a URL is never proof of access.
 *
 * <p>No security annotations and no new filter-chain rule: the chain's
 * {@code anyRequest().authenticated()} already covers these, and a
 * {@code hasRole} line here would wrongly imply the admin has reach into
 * collections.
 */
@RestController
@RequestMapping("/api/data-lists")
public class DataListController {

    private final DataListService collections;
    private final DataEntryService entries;

    public DataListController(DataListService collections, DataEntryService entries) {
        this.collections = collections;
        this.entries = entries;
    }

    @GetMapping
    public List<DataListSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ListIcon icon,
            @RequestParam(required = false, defaultValue = "all") String scope) {
        return collections.search(q, icon, scope);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataListDetailResponse create(@Valid @RequestBody CreateDataListRequest request) {
        return collections.create(request);
    }

    @GetMapping("/{id}")
    public DataListDetailResponse get(@PathVariable UUID id) {
        return collections.get(id);
    }

    @PatchMapping("/{id}")
    public DataListDetailResponse update(@PathVariable UUID id,
                                         @Valid @RequestBody UpdateDataListRequest request) {
        return collections.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        collections.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/access")
    public List<DataListMemberResponse> members(@PathVariable UUID id) {
        return collections.members(id);
    }

    @PutMapping("/{id}/access")
    public List<DataListMemberResponse> replaceMembers(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDataListAccessRequest request) {
        return collections.replaceMembers(id, request);
    }

    /** What this collection already uses, so a member converges on it (R52). */
    @GetMapping("/{id}/tags")
    public List<TagSuggestionResponse> tags(@PathVariable UUID id,
                                            @RequestParam(required = false) String q) {
        return entries.tagSuggestions(id, q);
    }
}
