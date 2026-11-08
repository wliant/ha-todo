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

    /**
     * The sidebar's collections group (0012 R98). Its own endpoint rather than a
     * second component on the lists' sidebar response - adr/0042, and adr/0029
     * for why a DataList read does not happen inside ListController.
     *
     * <p>A literal path beside {@code /{id}}: Spring prefers the literal, so
     * "sidebar" is never parsed as a UUID.
     */
    @GetMapping("/sidebar")
    public DataListSidebarResponse sidebar() {
        return collections.sidebar();
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

    /**
     * Pins or unpins a collection for the caller (0012 R104). PUT and DELETE
     * rather than a body, as on a list: the pin is the resource.
     */
    @PutMapping("/{id}/pin")
    public ResponseEntity<Void> pin(@PathVariable UUID id) {
        collections.pin(id, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/pin")
    public ResponseEntity<Void> unpin(@PathVariable UUID id) {
        collections.pin(id, false);
        return ResponseEntity.noContent().build();
    }

    /**
     * "I opened this collection." A POST, not a side effect of the GET, so a
     * read stays a read and a prefetch cannot reorder somebody's sidebar.
     */
    @PostMapping("/{id}/touch")
    public ResponseEntity<Void> touch(@PathVariable UUID id) {
        collections.touch(id);
        return ResponseEntity.noContent().build();
    }
}
