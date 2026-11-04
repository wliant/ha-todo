package com.hatodo.admin;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Keeps HTTP status mapping out of the service layer, which throws domain
 * exceptions rather than web ones.
 */
@RestControllerAdvice(assignableTypes = AdminUserController.class)
class AdminExceptionHandler {

    @ExceptionHandler(UsernameTakenException.class)
    ResponseEntity<Map<String, String>> usernameTaken(UsernameTakenException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "That login name is already taken."));
    }

    @ExceptionHandler(UserNotFoundException.class)
    ResponseEntity<Map<String, String>> notFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "That user no longer exists."));
    }

    @ExceptionHandler(AdminAccountProtectedException.class)
    ResponseEntity<Map<String, String>> adminProtected(AdminAccountProtectedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "The admin account cannot be deleted."));
    }
}
