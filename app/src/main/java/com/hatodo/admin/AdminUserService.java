package com.hatodo.admin;

import com.hatodo.user.AppUser;
import com.hatodo.user.AppUserRepository;
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

    public AdminUserService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
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

    @Transactional
    public void delete(UUID id) {
        AppUser user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (user.isAdmin()) {
            throw new AdminAccountProtectedException();
        }
        users.delete(user);
    }
}
