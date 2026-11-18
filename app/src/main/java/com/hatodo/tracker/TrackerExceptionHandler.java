package com.hatodo.tracker;

import com.hatodo.web.ValidationErrors;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TrackerController.class)
class TrackerExceptionHandler {

    /**
     * Also what a caller with no grant gets, with the same body as a tracker
     * that genuinely does not exist. Telling the two apart is the disclosure
     * R164 forbids.
     */
    @ExceptionHandler(TrackerNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(TrackerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "That tracker no longer exists."));
    }

    @ExceptionHandler(TrackerNotManagedException.class)
    ResponseEntity<Map<String, String>> notManaged(TrackerNotManagedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "error", "Only the person who created this tracker can change it."));
    }

    /**
     * Recording is not this - R165 lets anyone with access record, and lets
     * them correct their own series. This is somebody else's number on a
     * per-member tracker.
     */
    @ExceptionHandler(ReadingNotWritableException.class)
    ResponseEntity<Map<String, String>> notWritable(ReadingNotWritableException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "That number is in somebody else's series."));
    }

    @ExceptionHandler(TrackerNameTakenException.class)
    ResponseEntity<Map<String, String>> nameTaken(TrackerNameTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "You already have a tracker with that name."));
    }

    @ExceptionHandler(UnknownTrackerMemberException.class)
    ResponseEntity<Map<String, String>> unknownMember(UnknownTrackerMemberException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "One of those people no longer exists."));
    }

    /**
     * R161. Its own code, because the client's answer is to re-render the
     * settings screen read-only rather than to refetch and retry - which is
     * what every other 409 here means.
     */
    @ExceptionHandler(TrackerInUseException.class)
    ResponseEntity<Map<String, String>> inUse(TrackerInUseException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage(), "code", "TRACKER_IN_USE"));
    }

    @ExceptionHandler(UnsupportedGrainException.class)
    ResponseEntity<Map<String, String>> grain(UnsupportedGrainException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    /**
     * Both the explicit precondition and a genuine concurrent flush, because
     * they mean the same thing to the member and want the same reconciliation.
     */
    @ExceptionHandler({StaleTrackerException.class,
            ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Map<String, String>> stale(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Someone else changed this tracker while you were editing. "
                        + "Reopen it and try again.",
                "code", "STALE_TRACKER"));
    }

    /** The owner/name unique index, reached despite the service's own check. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "You already have a tracker with that name."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ValidationErrors.firstMessage(ex)));
    }
}
