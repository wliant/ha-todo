package com.hatodo.me;

import com.hatodo.user.AdminPasswordProtectedException;
import com.hatodo.web.ValidationErrors;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MeController.class)
class MeExceptionHandler {

    @ExceptionHandler(AdminPasswordProtectedException.class)
    ResponseEntity<Map<String, String>> adminPassword(AdminPasswordProtectedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "The admin password is set from the deployment configuration."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ValidationErrors.firstMessage(ex)));
    }
}
