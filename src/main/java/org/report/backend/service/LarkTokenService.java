package org.report.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import org.report.backend.model.TokenInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class LarkTokenService {

  private static final Logger log = LoggerFactory.getLogger(LarkTokenService.class);
  private static final String SESSION_TOKEN_INFO = "LARK_TOKEN_INFO";

  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${lark.app-id}")
  private String appId;

  @Value("${lark.app-secret}")
  private String appSecret;

  public boolean hasToken(HttpSession session) {
    return session.getAttribute(SESSION_TOKEN_INFO) != null;
  }

  public TokenInfo getCurrentToken(HttpSession session) {
    Object v = session.getAttribute(SESSION_TOKEN_INFO);
    return (v instanceof TokenInfo) ? (TokenInfo) v : null;
  }

  public String getAccessToken(HttpSession session, boolean forceRefresh) throws Exception {
    if (!hasToken(session)) {
      throw new IllegalStateException("No token in session. Please login first.");
    }

    if (forceRefresh) {
      refreshToken(session);
    } else {
      autoRefreshTokenIfNeeded(session);
    }

    TokenInfo token = getCurrentToken(session);
    if (token == null || token.getAccessToken() == null) {
      throw new IllegalStateException("Token not available.");
    }
    return token.getAccessToken();
  }

  /** 
   * Tự động refresh token nếu:
   * - Token đã hết hạn (expired)
   * - Token sắp hết hạn trong 60 giây
   * - Token đã được cập nhật hơn 1 giờ trước (để đảm bảo refresh định kỳ)
   */
  public void autoRefreshTokenIfNeeded(HttpSession session) {
    TokenInfo token = getCurrentToken(session);
    if (token == null) {
      log.warn("⚠️ No token found in session");
      return;
    }
    
    if (token.getExpiresAt() == null) {
      log.warn("⚠️ Token has no expiresAt, cannot check expiration");
      return;
    }

    long now = Instant.now().toEpochMilli();
    long expiresAtMs = token.getExpiresAt()
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli();

    long remainMs = expiresAtMs - now;
    long remainSeconds = remainMs / 1000;
    
    // Kiểm tra nếu token đã được cập nhật hơn 1 giờ trước
    boolean needsRefreshByTime = false;
    if (token.getLastUpdated() != null) {
      long lastUpdatedMs = token.getLastUpdated()
          .atZone(ZoneId.systemDefault())
          .toInstant()
          .toEpochMilli();
      long timeSinceUpdateMs = now - lastUpdatedMs;
      long timeSinceUpdateHours = timeSinceUpdateMs / (1000 * 60 * 60);
      
      if (timeSinceUpdateHours >= 1) {
        needsRefreshByTime = true;
        log.info("🕐 Token was updated {} hours ago, will refresh", timeSinceUpdateHours);
      }
    }

    // Refresh nếu: đã hết hạn, sắp hết hạn (< 60s), hoặc đã được cập nhật > 1 giờ
    boolean isExpired = remainMs <= 0;
    boolean isExpiringSoon = remainMs <= 60_000;
    
    if (isExpired || isExpiringSoon || needsRefreshByTime) {
      log.info("🔄 Auto-refreshing token - Expired: {}, Expiring soon (<60s): {}, Needs refresh by time (>1h): {}, Remaining: {} seconds",
          isExpired, isExpiringSoon, needsRefreshByTime, remainSeconds);
      
      try {
        TokenInfo oldToken = token;
        TokenInfo newToken = refreshToken(session);
        
        // Log chi tiết để kiểm tra
        log.info("✅ AUTO REFRESH SUCCESSFUL (FULL):");
        log.info("   📅 Old Token - AccessToken: {}, RefreshToken: {}, ExpiresAt: {}, LastUpdated: {}", 
            oldToken.getAccessToken(), 
            oldToken.getRefreshToken(),
            oldToken.getExpiresAt(),
            oldToken.getLastUpdated());
        log.info("   📅 New Token - AccessToken: {}, RefreshToken: {}, ExpiresAt: {}, LastUpdated: {}, ExpiresIn: {} seconds",
            newToken.getAccessToken(),
            newToken.getRefreshToken(),
            newToken.getExpiresAt(),
            newToken.getLastUpdated(),
            newToken.getExpiresIn());
        log.info("   ⏰ Refresh completed at: {}", LocalDateTime.now());
        
      } catch (Exception e) {
        log.error("❌ Auto refresh token failed: {}", e.getMessage(), e);
        throw new RuntimeException("Failed to auto-refresh token: " + e.getMessage(), e);
      }
    } else {
      log.debug("✓ Token still valid - Remaining: {} seconds, LastUpdated: {}", 
          remainSeconds, token.getLastUpdated());
    }
  }

  /** Exchange code -> user access_token / refresh_token */
  public TokenInfo exchangeCodeForToken(String code, HttpSession session) throws Exception {
    String appAccessToken = getAppAccessToken();

    String url = "https://open.larksuite.com/open-apis/authen/v1/access_token";

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(appAccessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    ExchangeReq req = new ExchangeReq();
    req.code = code;
    req.grantType = "authorization_code";

    HttpEntity<ExchangeReq> entity = new HttpEntity<>(req, headers);

    ResponseEntity<ExchangeResp> resp;
    try {
      resp = restTemplate.exchange(url, HttpMethod.POST, entity, ExchangeResp.class);
    } catch (RestClientException e) {
      throw new RuntimeException("Exchange token failed: " + e.getMessage(), e);
    }

    if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
      throw new RuntimeException("Exchange token HTTP error: " + resp.getStatusCode());
    }

    ExchangeResp body = resp.getBody();
    if (body.code != 0 || body.data == null) {
      throw new RuntimeException("Exchange token error: " + body.code + " - " + body.msg);
    }

    TokenInfo tokenInfo = buildTokenInfo(
        body.data.accessToken,
        body.data.refreshToken,
        body.data.tokenType,
        body.data.expiresIn
    );

    session.setAttribute(SESSION_TOKEN_INFO, tokenInfo);

    // In FULL token ra log để debug (chỉ nên dùng trong môi trường dev/test)
    log.info("✅ Token saved (FULL):");
    log.info("   accessToken = {}", tokenInfo.getAccessToken());
    log.info("   refreshToken = {}", tokenInfo.getRefreshToken());
    log.info("   tokenType = {}", tokenInfo.getTokenType());
    log.info("   expiresIn = {} seconds", tokenInfo.getExpiresIn());
    log.info("   expiresAt = {}", tokenInfo.getExpiresAt());
    log.info("   lastUpdated = {}", tokenInfo.getLastUpdated());

    return tokenInfo;
  }

  /** Refresh token + log token mới sau khi refresh xong */
  public TokenInfo refreshToken(HttpSession session) throws Exception {
    TokenInfo current = getCurrentToken(session);
    if (current == null || current.getRefreshToken() == null || current.getRefreshToken().isBlank()) {
      throw new IllegalStateException("No refresh_token available. Please login again.");
    }

    String url = "https://open.larksuite.com/open-apis/authen/v1/refresh_access_token";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    RefreshReq req = new RefreshReq();
    req.appId = appId;
    req.appSecret = appSecret;
    req.grantType = "refresh_token";
    req.refreshToken = current.getRefreshToken();

    HttpEntity<RefreshReq> entity = new HttpEntity<>(req, headers);

    ResponseEntity<RefreshResp> resp;
    try {
      resp = restTemplate.exchange(url, HttpMethod.POST, entity, RefreshResp.class);
    } catch (RestClientException e) {
      throw new RuntimeException("Refresh token failed: " + e.getMessage(), e);
    }

    if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
      throw new RuntimeException("Refresh token HTTP error: " + resp.getStatusCode());
    }

    RefreshResp body = resp.getBody();
    if (body.code != 0 || body.data == null) {
      throw new RuntimeException("Refresh token error: " + body.code + " - " + body.msg);
    }

    TokenInfo newToken = buildTokenInfo(
        body.data.accessToken,
        body.data.refreshToken,
        body.data.tokenType,
        body.data.expiresIn
    );

    session.setAttribute(SESSION_TOKEN_INFO, newToken);

    // Log chi tiết token mới (FULL để kiểm tra refresh)
    log.info("🔄 REFRESH TOKEN COMPLETED (FULL):");
    log.info("   📝 New AccessToken: {}", newToken.getAccessToken());
    log.info("   📝 New RefreshToken: {}", newToken.getRefreshToken());
    log.info("   ⏰ ExpiresAt: {}", newToken.getExpiresAt());
    log.info("   ⏰ LastUpdated: {}", newToken.getLastUpdated());
    log.info("   ⏱️ ExpiresIn: {} seconds ({} minutes)", 
        newToken.getExpiresIn(), 
        newToken.getExpiresIn() / 60);
    log.info("   📊 TokenType: {}", newToken.getTokenType());
    
    // Log thời gian hiện tại để so sánh
    LocalDateTime now = LocalDateTime.now();
    log.info("   🕐 Current Time: {}", now);
    log.info("   ⏳ Token will expire in: {} seconds", newToken.getExpiresIn());

    return newToken;
  }

  // ===================== helpers =====================

  private String getAppAccessToken() {
    String url = "https://open.larksuite.com/open-apis/auth/v3/app_access_token/internal";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    AppTokenReq req = new AppTokenReq();
    req.appId = appId;
    req.appSecret = appSecret;

    HttpEntity<AppTokenReq> entity = new HttpEntity<>(req, headers);

    ResponseEntity<AppTokenResp> resp;
    try {
      resp = restTemplate.exchange(url, HttpMethod.POST, entity, AppTokenResp.class);
    } catch (RestClientException e) {
      throw new RuntimeException("Get app_access_token failed: " + e.getMessage(), e);
    }

    if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
      throw new RuntimeException("Get app_access_token HTTP error: " + resp.getStatusCode());
    }

    AppTokenResp body = resp.getBody();
    if (body.code != 0 || body.appAccessToken == null) {
      throw new RuntimeException("Get app_access_token error: " + body.code + " - " + body.msg);
    }

    return body.appAccessToken;
  }

  /**
   * ✅ FIX chỗ này: TokenInfo.setExpiresIn(...) nhận int => convert long -> int an toàn
   */
  private TokenInfo buildTokenInfo(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
    TokenInfo t = new TokenInfo();
    t.setAccessToken(accessToken);
    t.setRefreshToken(refreshToken);
    t.setTokenType(tokenType);

    int expiresInt = toIntExactSafe(expiresInSeconds);
    t.setExpiresIn(expiresInt);

    long nowMs = Instant.now().toEpochMilli();
    long expiresAtMs = nowMs + (expiresInSeconds * 1000L);

    LocalDateTime expiresAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(expiresAtMs), ZoneId.systemDefault());
    LocalDateTime lastUpdated = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault());

    t.setExpiresAt(expiresAt);
    t.setLastUpdated(lastUpdated);

    return t;
  }

  private int toIntExactSafe(long v) {
    try {
      return Math.toIntExact(v);
    } catch (ArithmeticException ex) {
      // Lark thường 7200s, nên gần như không bao giờ vào đây.
      log.warn("expires_in too large for int: {}", v);
      return Integer.MAX_VALUE;
    }
  }

  private String mask(String token) {
    if (token == null) return "null";
    String s = token.trim();
    if (s.length() <= 12) return "****";
    return s.substring(0, 6) + "****" + s.substring(s.length() - 6);
  }

  // ===================== DTOs =====================

  private static class AppTokenReq {
    @JsonProperty("app_id") String appId;
    @JsonProperty("app_secret") String appSecret;
  }

  private static class AppTokenResp {
    @JsonProperty("code") int code;
    @JsonProperty("msg") String msg;
    @JsonProperty("app_access_token") String appAccessToken;
  }

  private static class ExchangeReq {
    @JsonProperty("code") String code;
    @JsonProperty("grant_type") String grantType;
  }

  private static class ExchangeResp {
    @JsonProperty("code") int code;
    @JsonProperty("msg") String msg;
    @JsonProperty("data") ExchangeData data;
  }

  private static class ExchangeData {
    @JsonProperty("access_token") String accessToken;
    @JsonProperty("refresh_token") String refreshToken;
    @JsonProperty("token_type") String tokenType;
    @JsonProperty("expires_in") long expiresIn;
  }

  private static class RefreshReq {
    @JsonProperty("app_id") String appId;
    @JsonProperty("app_secret") String appSecret;
    @JsonProperty("grant_type") String grantType;
    @JsonProperty("refresh_token") String refreshToken;
  }

  private static class RefreshResp {
    @JsonProperty("code") int code;
    @JsonProperty("msg") String msg;
    @JsonProperty("data") RefreshData data;
  }

  private static class RefreshData {
    @JsonProperty("access_token") String accessToken;
    @JsonProperty("refresh_token") String refreshToken;
    @JsonProperty("token_type") String tokenType;
    @JsonProperty("expires_in") long expiresIn;
  }
}
