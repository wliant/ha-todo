package com.hatodo.list;

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
 * Lists over HTTP. Translation only - every access decision is in the service,
 * because a list id in a URL is never proof of access.
 *
 * <p>No security annotations and no new filter-chain rule: the chain's
 * {@code anyRequest().authenticated()} already covers these, and a
 * {@code hasRole} line here would wrongly imply the admin has reach into lists.
 */
@RestController
@RequestMapping("/api/lists")
public class ListController {

    private final TodoListService todoLists;
    private final ListFieldService listFields;

    public ListController(TodoListService todoLists, ListFieldService listFields) {
        this.todoLists = todoLists;
        this.listFields = listFields;
    }

    @GetMapping
    public List<ListSummaryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ListIcon icon,
            @RequestParam(required = false, defaultValue = "all") String scope,
            @RequestParam(required = false, defaultValue = "false") boolean pinned) {
        return todoLists.search(q, icon, scope, pinned);
    }

    /** Both sidebar sections at once; the shell draws them together. */
    @GetMapping("/sidebar")
    public SidebarResponse sidebar() {
        return todoLists.sidebar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListDetailResponse create(@Valid @RequestBody CreateListRequest request) {
        return todoLists.create(request);
    }

    @GetMapping("/{id}")
    public ListDetailResponse get(@PathVariable UUID id) {
        return todoLists.get(id);
    }

    @PatchMapping("/{id}")
    public ListDetailResponse update(@PathVariable UUID id,
                                     @Valid @RequestBody UpdateListRequest request) {
        return todoLists.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        todoLists.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/access")
    public List<ListMemberResponse> members(@PathVariable UUID id) {
        return todoLists.members(id);
    }

    @PutMapping("/{id}/access")
    public List<ListMemberResponse> replaceMembers(@PathVariable UUID id,
                                                   @Valid @RequestBody UpdateAccessRequest request) {
        return todoLists.replaceMembers(id, request);
    }

    @GetMapping("/{id}/fields")
    public List<ListFieldResponse> fields(@PathVariable UUID id) {
        return listFields.get(id);
    }

    /**
     * Replaces the whole configuration. Existing ids come back in the body so
     * the service can merge rather than recreate - item values will reference
     * these rows by id.
     */
    @PutMapping("/{id}/fields")
    public List<ListFieldResponse> replaceFields(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdateFieldsRequest request) {
        return listFields.replace(id, request);
    }

    @PutMapping("/{id}/pin")
    public ResponseEntity<Void> pin(@PathVariable UUID id) {
        todoLists.pin(id, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/pin")
    public ResponseEntity<Void> unpin(@PathVariable UUID id) {
        todoLists.pin(id, false);
        return ResponseEntity.noContent().build();
    }

    /**
     * "I opened this list." A POST rather than a side effect of the GET, so a
     * read stays a read - and so CSRF applies and a prefetch cannot reorder
     * somebody's sidebar.
     */
    @PostMapping("/{id}/touch")
    public ResponseEntity<Void> touch(@PathVariable UUID id) {
        todoLists.touch(id);
        return ResponseEntity.noContent().build();
    }
}
