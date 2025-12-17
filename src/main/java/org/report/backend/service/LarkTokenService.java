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

  /** refresh trước 60s cho chắc */
  public void autoRefreshTokenIfNeeded(HttpSession session) {
    TokenInfo token = getCurrentToken(session);
    if (token == null || token.getExpiresAt() == null) return;

    long now = Instant.now().toEpochMilli();
    long expiresAtMs = token.getExpiresAt()
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli();

    long remainMs = expiresAtMs - now;

    if (remainMs <= 60_000) {
      try {
        refreshToken(session);
      } catch (Exception e) {
        log.error("Auto refresh token failed: {}", e.getMessage(), e);
      }
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

    log.info("✅ Token saved. accessToken={}, refreshToken={}, expiresAt={}",
        mask(tokenInfo.getAccessToken()),
        mask(tokenInfo.getRefreshToken()),
        tokenInfo.getExpiresAt()
    );

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

    log.info("🔄 REFRESH DONE ✅ newAccessToken={}, newRefreshToken={}, expiresAt={}",
        mask(newToken.getAccessToken()),
        mask(newToken.getRefreshToken()),
        newToken.getExpiresAt()
    );

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
