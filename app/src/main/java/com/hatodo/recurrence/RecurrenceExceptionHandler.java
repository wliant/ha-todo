package com.hatodo.recurrence;

import com.hatodo.item.AssigneeWithoutAccessException;
import com.hatodo.list.ListNotFoundException;
import com.hatodo.list.ListNotManagedException;
import com.hatodo.web.ValidationErrors;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RecurrenceController.class)
class RecurrenceExceptionHandler {

    /**
     * A rule that is gone, and a list the caller holds no grant on, answer the
     * same way: the existence of the list is itself what R19 hides.
     */
    @ExceptionHandler({RecurrenceRuleNotFoundException.class, ListNotFoundException.class})
    ResponseEntity<Map<String, Object>> notFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "That recurring item is no longer here."));
    }

    /** Readable but not owned: a standing instruction is the owner's to write. */
    @ExceptionHandler(ListNotManagedException.class)
    ResponseEntity<Map<String, Object>> notManaged(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Only the list's owner can change what recurs on it."));
    }

    @ExceptionHandler({StaleRecurrenceRuleException.class,
            ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Map<String, Object>> stale(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "STALE_RULE",
                "error", "That recurring item changed while you were looking at it."));
    }

    @ExceptionHandler({InvalidRecurrenceException.class, AssigneeWithoutAccessException.class})
    ResponseEntity<Map<String, Object>> invalid(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> notValid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ValidationErrors.firstMessage(ex)));
    }
}
