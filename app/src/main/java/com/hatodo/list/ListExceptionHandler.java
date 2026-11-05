package com.hatodo.list;

import com.hatodo.web.ValidationErrors;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ListController.class)
class ListExceptionHandler {

    /**
     * Also what a caller with no grant gets, with the same body as a list that
     * genuinely does not exist. Telling the two apart is the disclosure R19
     * forbids.
     */
    @ExceptionHandler(ListNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(ListNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "That list no longer exists."));
    }

    @ExceptionHandler(ListNotManagedException.class)
    ResponseEntity<Map<String, String>> notManaged(ListNotManagedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Only the person who created this list can change it."));
    }

    @ExceptionHandler(ListNameTakenException.class)
    ResponseEntity<Map<String, String>> nameTaken(ListNameTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "You already have a list with that name."));
    }

    @ExceptionHandler(UnknownMemberException.class)
    ResponseEntity<Map<String, String>> unknownMember(UnknownMemberException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "One of those people no longer exists."));
    }

    /**
     * Both the explicit precondition and a genuine concurrent flush, because
     * they mean the same thing to the member and want the same reconciliation.
     */
    @ExceptionHandler({StaleListException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Map<String, String>> stale(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Someone else changed this list while you were editing. "
                        + "Reopen it and try again.",
                "code", "STALE_LIST"));
    }

    /** Carries the service's own sentence: "invalid" is not an answer. */
    @ExceptionHandler(InvalidFieldConfigurationException.class)
    ResponseEntity<Map<String, String>> invalidConfiguration(InvalidFieldConfigurationException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    /**
     * A cost choice something has already picked.
     *
     * <p>Both item_cost_value and recurrence_cost_value reference a choice ON
     * DELETE RESTRICT, deliberately: emptying somebody's cost silently is worse
     * than refusing. Unhandled, though, that refusal arrived as a 500 - which
     * was true before recurrence existed and became twice as reachable once a
     * standing instruction could hold a choice too.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, Object>> inUse(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Something on this list still uses one of those choices. "
                        + "Change those items first."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ValidationErrors.firstMessage(ex)));
    }
}
