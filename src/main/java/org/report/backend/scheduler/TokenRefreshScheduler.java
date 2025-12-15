package org.report.backend.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Token refresh scheduler.
 * Tokens are automatically refreshed when getAccessToken() is called
 * if they are expired, about to expire (within 5 minutes), or last updated more than 1 hour ago.
 */
@Component
public class TokenRefreshScheduler {
  
  private static final Logger logger = LoggerFactory.getLogger(TokenRefreshScheduler.class);
  
  /**
   * Token refresh is handled per-session when getAccessToken() is called.
   * Tokens will be automatically refreshed:
   * - If expired
   * - If expiring within 5 minutes
   * - If last updated more than 1 hour ago
   * 
   * This scheduler runs every hour as a reminder/logging mechanism.
   */
  @Scheduled(fixedRate = 3600000) // 1 hour = 3600000 ms
  public void refreshTokenScheduler() {
    logger.info("Token refresh scheduler running - tokens are refreshed automatically when needed (every 1 hour or on-demand)");
  }
}

