package com.hatodo.me;

import com.hatodo.images.ImageTooLargeException;
import com.hatodo.images.UnsupportedImageTypeException;
import com.hatodo.user.AdminPasswordProtectedException;
import com.hatodo.web.ValidationErrors;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MeController.class)
class MeExceptionHandler {

    @ExceptionHandler(AdminPasswordProtectedException.class)
    ResponseEntity<Map<String, String>> adminPassword(AdminPasswordProtectedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "The admin password is set from the deployment configuration."));
    }

    @ExceptionHandler(UnsupportedImageTypeException.class)
    ResponseEntity<Map<String, String>> unsupportedImage(UnsupportedImageTypeException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Map.of("error", "Pictures must be a PNG, JPEG or WebP."));
    }

    @ExceptionHandler({ImageTooLargeException.class, MaxUploadSizeExceededException.class})
    ResponseEntity<Map<String, String>> tooLarge(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(Map.of("error", "That picture is too large - 5 MB at most."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ValidationErrors.firstMessage(ex)));
    }
}
