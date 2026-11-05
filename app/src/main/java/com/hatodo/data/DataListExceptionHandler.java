package com.hatodo.data;

import com.hatodo.web.ValidationErrors;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DataListController.class)
class DataListExceptionHandler {

    /**
     * Also what a caller with no grant gets, with the same body as a collection
     * that genuinely does not exist. Telling the two apart is the disclosure
     * R50 forbids.
     */
    @ExceptionHandler(DataListNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(DataListNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "That collection no longer exists."));
    }

    @ExceptionHandler(DataListNotManagedException.class)
    ResponseEntity<Map<String, String>> notManaged(DataListNotManagedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Only the person who created this collection can change it."));
    }

    @ExceptionHandler(DataListNameTakenException.class)
    ResponseEntity<Map<String, String>> nameTaken(DataListNameTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "You already have a collection with that name."));
    }

    @ExceptionHandler(UnknownCollectionMemberException.class)
    ResponseEntity<Map<String, String>> unknownMember(UnknownCollectionMemberException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "One of those people no longer exists."));
    }

    /**
     * Both the explicit precondition and a genuine concurrent flush, because
     * they mean the same thing to the member and want the same reconciliation.
     */
    @ExceptionHandler({StaleDataListException.class,
            ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Map<String, String>> stale(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Someone else changed this collection while you were editing. "
                        + "Reopen it and try again.",
                "code", "STALE_DATA_LIST"));
    }

    /** The owner/name unique index, reached despite the service's own check. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "You already have a collection with that name."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ValidationErrors.firstMessage(ex)));
    }
}
