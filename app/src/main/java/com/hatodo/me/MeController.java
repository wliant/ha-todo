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
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

    @PutMapping("/avatar")
    public Map<String, UUID> setAvatar(@RequestParam("file") MultipartFile file) throws IOException {
        return Map.of("avatarImageId", me.setAvatar(file.getBytes()));
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<Void> removeAvatar() {
        me.removeAvatar();
        return ResponseEntity.noContent().build();
    }

    /** No user id in the path: this acts on the caller, always. */
    @PutMapping("/password")
    public ResponseEntity<Void> setPassword(@Valid @RequestBody SetPasswordRequest request) {
        me.setPassword(request);
        return ResponseEntity.noContent().build();
    }
}
