package com.hatodo.live;

import com.hatodo.user.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Signing out closes the streams.
 *
 * <p>Nothing re-checks authorization on an emitter that is already open, so
 * without this a signed-out tab would keep receiving chore descriptions until
 * its stream timed out. The timeout bounds the other case - a session that
 * simply expires, which has no event to hook - and that is the argument for it
 * being half an hour rather than a day. See adr/0054.
 */
@Component
public class LiveLogoutHandler implements LogoutHandler {

    private final LiveBroker broker;

    public LiveLogoutHandler(LiveBroker broker) {
        this.broker = broker;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response,
                       Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            broker.disconnect(principal.getId());
        }
    }
}
