package com.hatodo.calendar;

import com.hatodo.web.ValidationErrors;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CalendarFeedController.class)
class CalendarExceptionHandler {

    /** Also what a caller who does not own the feed gets, with the same body. */
    @ExceptionHandler(CalendarFeedNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(CalendarFeedNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "That calendar is no longer subscribed."));
    }

    @ExceptionHandler(CalendarFeedLabelTakenException.class)
    ResponseEntity<Map<String, String>> labelTaken(CalendarFeedLabelTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "You already have a calendar with that name."));
    }

    @ExceptionHandler(DuplicateCalendarFeedException.class)
    ResponseEntity<Map<String, String>> duplicate(DuplicateCalendarFeedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "You have already subscribed that calendar."));
    }

    @ExceptionHandler(UnknownCalendarMemberException.class)
    ResponseEntity<Map<String, String>> unknownMember(UnknownCalendarMemberException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "One of those people no longer exists."));
    }

    /**
     * The member's own input, so 400 - and the message is the guard's, which
     * deliberately says nothing about what is at the address.
     */
    @ExceptionHandler(FeedUrlNotAllowedException.class)
    ResponseEntity<Map<String, String>> notAllowed(FeedUrlNotAllowedException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    /**
     * 502 rather than 400: a provider outage is not the member's fault, and the
     * distinction is what tells them whether to re-check the address or wait.
     */
    @ExceptionHandler(FeedFetchException.class)
    ResponseEntity<Map<String, String>> fetchFailed(FeedFetchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(PreviewTooOftenException.class)
    ResponseEntity<Map<String, String>> tooOften(PreviewTooOftenException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "One at a time - try that again in a moment."));
    }

    /** The owner/label and owner/url unique indexes, despite the service's checks. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "You already have a calendar like that."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ValidationErrors.firstMessage(ex)));
    }
}
