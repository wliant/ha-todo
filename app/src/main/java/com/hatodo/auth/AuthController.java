package com.hatodo.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explicit JSON login rather than Spring Security's form-login filter, so the
 * React client can post and parse JSON on the one endpoint it needs before it
 * holds a session.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final LoginService loginService;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public AuthController(LoginService loginService,
                          SecurityContextRepository securityContextRepository,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.loginService = loginService;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    /**
     * The roster the sign-in screen is drawn from. Necessarily readable by
     * anonymous callers, which is a trade the requirement document records
     * explicitly.
     */
    @GetMapping("/auth/users")
    public List<LoginUserResponse> loginUsers() {
        return loginService.roster();
    }

    @PostMapping("/auth/login")
    public ResponseEntity<CurrentUserResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse httpResponse) {
        Authentication authentication;
        try {
            authentication = loginService.authenticate(request.username(), request.password());
        } catch (AuthenticationException ex) {
            // Deliberately opaque: never reveal whether the username exists,
            // whether it needed a password, or which half was wrong.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Rotate the session id so a pre-login session cannot be fixated. Calling
        // changeSessionId() directly would throw when no session exists yet.
        sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);

        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ResponseEntity.ok(loginService.currentUser(authentication.getName()));
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Principal principal) {
        return loginService.currentUser(principal.getName());
    }
}
