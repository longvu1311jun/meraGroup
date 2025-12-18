package org.report.backend.scheduler;

import org.report.backend.model.TokenInfo;
import org.report.backend.service.LarkTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Global token refresh scheduler (project 1 style).
 *
 * - Every 1 hour: refresh access token using refresh_token (if available)
 * - All services will then use this global token
 */
@Component
public class TokenRefreshScheduler {

  private static final Logger logger = LoggerFactory.getLogger(TokenRefreshScheduler.class);

  private final LarkTokenService tokenService;

  public TokenRefreshScheduler(LarkTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Scheduled(
      fixedRateString = "${lark.token.refresh-rate-ms:3600000}",
      initialDelayString = "${lark.token.refresh-initial-delay-ms:60000}"
  )
  public void refreshTokenScheduler() {
    try {
      TokenInfo current = tokenService.getCurrentToken(null);
      if (current == null || current.getRefreshToken() == null || current.getRefreshToken().isBlank()) {
        logger.info("⏳ Scheduler: no refresh_token yet (need login first)");
        return;
      }

      TokenInfo newToken = tokenService.refreshToken();
      logger.info("✅ Scheduler: refreshed token. expiresAt={}, lastUpdated={}",
          newToken.getExpiresAt(), newToken.getLastUpdated());
    } catch (Exception e) {
      logger.error("❌ Scheduler: refresh token failed: {}", e.getMessage(), e);
    }
  }
}
