package com.hatodo.me;

import com.hatodo.user.SetPasswordRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final MeService me;

    public MeController(MeService me) {
        this.me = me;
    }

    @GetMapping("/preferences")
    public PreferencesResponse preferences() {
        return me.preferences();
    }

    @PatchMapping("/preferences")
    public PreferencesResponse updatePreferences(@Valid @RequestBody PreferencesRequest request) {
        return me.updatePreferences(request);
    }

    /** No user id in the path: this acts on the caller, always. */
    @PutMapping("/password")
    public ResponseEntity<Void> setPassword(@Valid @RequestBody SetPasswordRequest request) {
        me.setPassword(request);
        return ResponseEntity.noContent().build();
    }
}
