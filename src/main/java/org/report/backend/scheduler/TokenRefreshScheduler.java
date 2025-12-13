package org.report.backend.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Note: Token refresh is now handled per-session when getAccessToken() is called.
 * This scheduler is kept for potential future use with a shared token storage.
 * For now, tokens are stored in individual user sessions and refreshed on-demand.
 */
@Component
public class TokenRefreshScheduler {
  
  private static final Logger logger = LoggerFactory.getLogger(TokenRefreshScheduler.class);
  
  /**
   * Token refresh is now handled per-session.
   * Tokens are automatically refreshed when getAccessToken() is called
   * if they are expired or about to expire (within 5 minutes).
   * 
   * If you need to refresh tokens for all active sessions,
   * you would need to implement a session registry to track all active sessions.
   */
  @Scheduled(fixedRate = 3600000) // 1 hour = 3600000 ms
  public void refreshToken() {
    // Token refresh is now handled per-session on-demand
    // This method can be used for logging or other maintenance tasks
    logger.debug("Token refresh scheduler running - tokens are refreshed on-demand per session");
  }
}

