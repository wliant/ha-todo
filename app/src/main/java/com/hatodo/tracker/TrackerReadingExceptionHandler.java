package com.hatodo.tracker;

import com.hatodo.web.ValidationErrors;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * One advice per controller, as the collections package has: the two answer
 * about different things and share no exception type.
 */
@RestControllerAdvice(assignableTypes = TrackerReadingController.class)
class TrackerReadingExceptionHandler {

    @ExceptionHandler({TrackerReadingNotFoundException.class, TrackerNotFoundException.class})
    ResponseEntity<Map<String, String>> notFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "That reading no longer exists."));
    }

    /**
     * The caller can see this number - it is on the chart in front of them -
     * and may not change it, so 403 rather than the 404 a missing grant gets.
     * The two refusals are not interchangeable (R165).
     */
    @ExceptionHandler(ReadingNotWritableException.class)
    ResponseEntity<Map<String, String>> notWritable(ReadingNotWritableException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "That number is in somebody else's series."));
    }

    @ExceptionHandler({StaleReadingException.class, ObjectOptimisticLockingFailureException.class})
    ResponseEntity<Map<String, String>> stale(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "Someone else changed this number while you were editing. "
                        + "Reopen it and try again.",
                "code", "STALE_TRACKER_READING"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ValidationErrors.firstMessage(ex)));
    }
}
