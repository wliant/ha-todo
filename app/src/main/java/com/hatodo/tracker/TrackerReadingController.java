package com.hatodo.tracker;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Correcting a number that is already recorded (requirements/0019 R165).
 *
 * <p>A path of its own rather than one nested under the tracker, as
 * {@code /api/data-entries/{id}} is: a reading's id identifies it, and the
 * tracker it belongs to is a fact about it rather than a parameter the caller
 * should be able to disagree with.
 */
@RestController
@RequestMapping("/api/tracker-readings")
public class TrackerReadingController {

    private final TrackerReadingService readings;

    public TrackerReadingController(TrackerReadingService readings) {
        this.readings = readings;
    }

    @PatchMapping("/{id}")
    public TrackerReadingResponse update(@PathVariable UUID id,
                                         @Valid @RequestBody SaveReadingRequest request) {
        return readings.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        readings.delete(id);
        return ResponseEntity.noContent().build();
    }
}
