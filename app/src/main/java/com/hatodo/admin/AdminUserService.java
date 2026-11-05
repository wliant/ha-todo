package com.hatodo.admin;

import com.hatodo.images.ImageService;
import com.hatodo.images.StoredImage;
import com.hatodo.user.AdminPasswordProtectedException;
import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
import com.hatodo.user.SetPasswordRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration. The filter chain already restricts /api/admin/** to
 * ROLE_ADMIN; the rule this layer owns is R12 - the admin account is
 * undeletable regardless of who asks - because that is an invariant of the
 * data, not a permission of the caller.
 */
@Service
public class AdminUserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ImageService images;

    public AdminUserService(AppUserRepository users, PasswordEncoder passwordEncoder,
                            ImageService images) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.images = images;
    }

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        return users.findAllByOrderByDisplayNameAsc().stream()
                .map(AdminUserResponse::of)
                .toList();
    }

    @Transactional
    public AdminUserResponse create(CreateUserRequest request) {
        String username = request.username().trim();
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new UsernameTakenException(username);
        }

        String hash = request.wantsPassword() ? passwordEncoder.encode(request.password()) : null;
        AppUser created = users.save(new AppUser(username, request.displayName().trim(), hash,
                com.hatodo.user.UserRole.MEMBER));
        return AdminUserResponse.of(created);
    }

    /**
     * The recovery path for R33. A member can set their own password with no
     * old password, which means a passwordless account can be captured and
     * locked - so the admin must be able to clear it again. A null or blank
     * password clears the requirement entirely.
     *
     * <p>Refuses to target the admin row: that password is reconciled from the
     * environment on every startup (adr/0006), so setting it here would be
     * undone by the next deploy.
     */
    @Transactional
    public void setPassword(UUID id, SetPasswordRequest request) {
        AppUser user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (user.isAdmin()) {
            throw new AdminPasswordProtectedException();
        }
        user.setPasswordHash(request.wantsPassword() ? passwordEncoder.encode(request.password()) : null);
    }

    @Transactional
    public void delete(UUID id) {
        AppUser user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (user.isAdmin()) {
            throw new AdminAccountProtectedException();
        }

        // Their picture goes with them. The foreign keys alone would not do
        // it: deleting a user only nulls stored_image.uploaded_by, leaving the
        // row and its bytes behind for good. Found by deleting throwaway
        // accounts on dev and then counting what was left in the bucket.
        StoredImage avatar = user.getAvatarImage();
        user.setAvatarImage(null);
        users.delete(user);
        if (avatar != null) {
            images.forget(avatar);
        }
    }
}
