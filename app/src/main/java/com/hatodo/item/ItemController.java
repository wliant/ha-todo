package com.hatodo.item;

import jakarta.validation.Valid;
import java.time.Instant;
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
 * Items over HTTP.
 *
 * <p>Items hang off two paths on purpose: a collection under its list, and the
 * item itself by id. A member acting on one item already knows its id and
 * should not have to carry the list's as well.
 */
@RestController
@RequestMapping("/api")
public class ItemController {

    private final TodoItemService todoItems;

    public ItemController(TodoItemService todoItems) {
        this.todoItems = todoItems;
    }

    /**
     * The whole list, in the member's order.
     *
     * <p>No filter parameters: narrowing a list is done in the browser over
     * this payload (0013, adr/0044), so there is one implementation of what
     * "done" means and it is not split across the wire.
     */
    @GetMapping("/lists/{listId}/items")
    public List<ItemResponse> list(@PathVariable UUID listId) {
        return todoItems.list(listId);
    }

    @PostMapping("/lists/{listId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse create(@PathVariable UUID listId,
                               @Valid @RequestBody SaveItemRequest request) {
        return todoItems.create(listId, request);
    }

    /**
     * Under the list, not under /items: every item in one request must be on
     * one list, so the grant and the assignee's audience are checked once.
     */
    @PostMapping("/lists/{listId}/items/assignment")
    public AssignmentResponse assign(@PathVariable UUID listId,
                                     @Valid @RequestBody AssignmentRequest request) {
        return todoItems.assign(listId, request);
    }

    @GetMapping("/lists/{listId}/points")
    public List<ListPointsResponse> points(@PathVariable UUID listId) {
        return todoItems.points(listId);
    }

    /**
     * The window is the caller's own, as instants: only the browser knows
     * where that member's midnight is. Absent, the service uses a week.
     */
    @GetMapping("/lists/{listId}/activity")
    public ActivityResponse activity(@PathVariable UUID listId,
                                     @RequestParam(required = false) Instant from,
                                     @RequestParam(required = false) Instant to) {
        return todoItems.activity(listId, from, to);
    }

    @GetMapping("/items/{id}")
    public ItemResponse get(@PathVariable UUID id) {
        return todoItems.get(id);
    }

    @PatchMapping("/items/{id}")
    public ItemResponse update(@PathVariable UUID id,
                               @Valid @RequestBody SaveItemRequest request) {
        return todoItems.update(id, request);
    }

    @PutMapping("/items/{id}/completion")
    public ItemResponse setCompleted(@PathVariable UUID id,
                                     @Valid @RequestBody CompletionRequest request) {
        return todoItems.setCompleted(id, request);
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @RequestParam Long version) {
        todoItems.delete(id, version);
        return ResponseEntity.noContent().build();
    }
}
