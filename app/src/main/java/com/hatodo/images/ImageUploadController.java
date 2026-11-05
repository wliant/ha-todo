package com.hatodo.images;

import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.CurrentUserProvider;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Where a picture pasted into an item's detail field goes.
 *
 * <p>Uploaded before the item exists, so it arrives unclaimed and is readable
 * only by whoever uploaded it. Saving the item claims it, and from then on the
 * item's list decides who may see it.
 */
@RestController
@RequestMapping("/api/images")
public class ImageUploadController {

    private final ImageService images;
    private final AppUserRepository users;
    private final CurrentUserProvider currentUser;

    public ImageUploadController(ImageService images, AppUserRepository users,
                                 CurrentUserProvider currentUser) {
        this.images = images;
        this.users = users;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        UUID id = currentUser.requireUserId();
        AppUser uploader = users.findById(id).orElseThrow(
                () -> new IllegalStateException("Authenticated user " + id + " no longer exists"));
        StoredImage stored = images.store(file.getBytes(), ImagePurpose.DETAIL, uploader);
        // The client puts this straight into the editor, and the sanitizer
        // only keeps <img> whose src is exactly this shape.
        return Map.of("id", stored.getId(), "url", "/api/images/" + stored.getId());
    }
}
