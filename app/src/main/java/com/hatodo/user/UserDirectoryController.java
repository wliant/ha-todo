package com.hatodo.user;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who is in the household, for the access and assignee pickers.
 *
 * <p>Authenticated, unlike the sign-in roster at /api/auth/users - which is
 * permitAll and must never grow a field (adr/0009). Keeping them as separate
 * endpoints is what lets this one answer a different question.
 */
@RestController
@RequestMapping("/api/users")
public class UserDirectoryController {

    private final AppUserRepository users;

    public UserDirectoryController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<MemberResponse> members() {
        return users.findAllByEnabledTrueOrderByDisplayNameAsc().stream()
                .map(MemberResponse::of)
                .toList();
    }
}
