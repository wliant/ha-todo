package com.hatodo.data;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Entries over HTTP.
 *
 * <p>Mapped at {@code /api} and holding both path families - entries nested
 * under their collection, entries addressed on their own - which is
 * {@code ItemController}'s shape and keeps one {@code @RestControllerAdvice}
 * per controller.
 *
 * <p>Scoped and global search are the same handler with the collection named or
 * not, so there is one statement behind both and nothing to drift.
 */
@RestController
@RequestMapping("/api")
public class DataEntryController {

    private final DataEntryService entries;

    public DataEntryController(DataEntryService entries) {
        this.entries = entries;
    }

    /** One collection: its entries, or a search within it (R53). */
    @GetMapping("/data-lists/{listId}/entries")
    public List<DataEntryHit> list(@PathVariable UUID listId,
                                   @RequestParam(required = false) String q,
                                   @RequestParam(required = false) List<String> tag,
                                   @RequestParam(required = false) Integer limit) {
        return entries.list(listId, q, tag, limit);
    }

    @PostMapping("/data-lists/{listId}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public DataEntryResponse create(@PathVariable UUID listId,
                                    @Valid @RequestBody SaveDataEntryRequest request) {
        return entries.create(listId, request);
    }

    /** Across every collection the caller can see (R54). */
    @GetMapping("/data-entries")
    public List<DataEntryHit> searchEverywhere(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) List<String> tag,
                                               @RequestParam(required = false) Integer limit) {
        return entries.searchEverywhere(q, tag, limit);
    }

    @GetMapping("/data-entries/{id}")
    public DataEntryResponse get(@PathVariable UUID id) {
        return entries.get(id);
    }

    @PatchMapping("/data-entries/{id}")
    public DataEntryResponse update(@PathVariable UUID id,
                                    @Valid @RequestBody SaveDataEntryRequest request) {
        return entries.update(id, request);
    }

    /**
     * @param version the view the caller is deleting from, so two members
     *                cannot both think they removed the same entry
     */
    @DeleteMapping("/data-entries/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id,
                                       @RequestParam(required = false) Long version) {
        entries.delete(id, version);
        return ResponseEntity.noContent().build();
    }
}
