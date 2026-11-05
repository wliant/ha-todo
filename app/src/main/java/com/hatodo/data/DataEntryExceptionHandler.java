package com.hatodo.data;

import com.hatodo.web.ValidationErrors;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DataEntryController.class)
class DataEntryExceptionHandler {

    private final DataEntryService entries;

    DataEntryExceptionHandler(DataEntryService entries) {
        this.entries = entries;
    }

    /** The same body for "deleted" and "you cannot see its collection". */
    @ExceptionHandler({DataEntryNotFoundException.class, DataListNotFoundException.class})
    ResponseEntity<Map<String, String>> notFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "That entry no longer exists."));
    }

    /**
     * Carries the entry as it actually is, so the client can take the server's
     * copy outright rather than guess what changed (R60).
     *
     * <p>Read in a <em>fresh</em> transaction: the one that threw is marked
     * rollback-only, and reading through it would turn this 409 into a 500 on
     * exactly the path that exists to avoid one.
     */
    @ExceptionHandler(StaleDataEntryException.class)
    ResponseEntity<Map<String, Object>> stale(StaleDataEntryException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Someone else changed this entry while you were editing. "
                + "This is what it says now.");
        body.put("code", "STALE_DATA_ENTRY");
        DataEntryResponse current = entries.readFresh(ex.getEntryId());
        if (current != null) {
            body.put("entry", current);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /**
     * A concurrent flush rather than the explicit precondition. There is no id
     * to re-read from here, so this one carries the code without the entry -
     * the client refetches.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ResponseEntity<Map<String, Object>> concurrent(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Someone else changed this entry while you were editing. "
                        + "Reopen it and try again.",
                "code", "STALE_DATA_ENTRY"));
    }

    @ExceptionHandler(InvalidTagException.class)
    ResponseEntity<Map<String, String>> invalidTag(InvalidTagException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ValidationErrors.firstMessage(ex)));
    }
}
