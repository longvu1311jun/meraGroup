package org.report.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.report.backend.model.TokenInfo;
import org.report.backend.service.LarkTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * This interceptor ensures that if a token exists in the persistent storage,
 * it is automatically loaded into the user's HttpSession.
 * This provides a "remember-me" functionality across application restarts.
 */
@Component
public class TokenPersistenceInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TokenPersistenceInterceptor.class);
    private static final String SESSION_TOKEN_INFO = "LARK_TOKEN_INFO";

    @Autowired
    private LarkTokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(true);

        // Check if session has token, but persistent storage doesn't (e.g., token file deleted manually)
        if (session.getAttribute(SESSION_TOKEN_INFO) != null && !tokenService.hasToken()) {
            session.removeAttribute(SESSION_TOKEN_INFO);
            log.info("Cleared session token because persistent token is missing.");
            return true;
        }

        // Check if persistent storage has a token but the session doesn't
        if (tokenService.hasToken() && session.getAttribute(SESSION_TOKEN_INFO) == null) {
            TokenInfo persistedToken = tokenService.getCurrentToken();
            if (persistedToken != null) {
                session.setAttribute(SESSION_TOKEN_INFO, persistedToken);
                log.info("✅ Automatically logged in user by loading token from persistent storage into session.");
                
                // Trigger an auto-refresh check in case the loaded token is stale
                try {
                    tokenService.autoRefreshTokenIfNeeded(session);
                } catch (Exception e) {
                    log.error("Failed to auto-refresh token after loading from persistence. User may need to log in manually.", e);
                    // Clear the session and storage to force a clean login
                    session.removeAttribute(SESSION_TOKEN_INFO);
                    tokenService.getCurrentToken().setRefreshToken(null); // Prevent further refresh attempts
                }
            }
        }

        return true; // Continue the request chain
    }
}
