package org.report.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpSession;
import org.report.backend.model.TokenInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class LarkTokenService {
  
  private static final Logger log = LoggerFactory.getLogger(LarkTokenService.class);
  private static final String SESSION_TOKEN_KEY = "LARK_TOKEN_INFO";
  
  @Value("${lark.app-id}")
  private String appId;
  
  @Value("${lark.app-secret}")
  private String appSecret;
  
  private final RestTemplate restTemplate;
  private String cachedAppAccessToken;
  private LocalDateTime appTokenExpiresAt;
  
  public LarkTokenService() {
    this.restTemplate = new RestTemplate();
  }
  
  /**
   * Get app access token from Lark
   */
  private String getAppAccessToken() throws Exception {
    // Return cached token if still valid (with 5 minutes buffer)
    if (cachedAppAccessToken != null && appTokenExpiresAt != null &&
        LocalDateTime.now().plusMinutes(5).isBefore(appTokenExpiresAt)) {
      return cachedAppAccessToken;
    }
    
    String url = "https://open.larksuite.com/open-apis/auth/v3/app_access_token/internal";
    
    Map<String, String> body = new HashMap<>();
    body.put("app_id", appId);
    body.put("app_secret", appSecret);
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    
    HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
    
    try {
      ResponseEntity<AppTokenResponse> response = restTemplate.postForEntity(
          url, entity, AppTokenResponse.class);
      
      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        AppTokenResponse appTokenResponse = response.getBody();
        if (appTokenResponse != null && appTokenResponse.getCode() == 0 && 
            appTokenResponse.getAppAccessToken() != null) {
          cachedAppAccessToken = appTokenResponse.getAppAccessToken();
          // App access token typically expires in 2 hours
          appTokenExpiresAt = LocalDateTime.now().plusHours(2);
          return cachedAppAccessToken;
        } else {
          String errorMsg = appTokenResponse != null ? 
              (appTokenResponse.getCode() + " - " + appTokenResponse.getMsg()) : "Unknown error";
          throw new RuntimeException("Failed to get app access token: " + errorMsg);
        }
      } else {
        throw new RuntimeException("Failed to get app access token: HTTP " + response.getStatusCode());
      }
    } catch (RestClientException e) {
      log.error("Error calling getAppAccessToken API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to get app access token: " + e.getMessage(), e);
    }
  }
  
  /**
   * Get token from session
   */
  private TokenInfo getTokenFromSession(HttpSession session) {
    if (session != null) {
      return (TokenInfo) session.getAttribute(SESSION_TOKEN_KEY);
    }
    return null;
  }
  
  /**
   * Save token to session
   */
  private void saveTokenToSession(HttpSession session, TokenInfo tokenInfo) {
    if (session != null) {
      session.setAttribute(SESSION_TOKEN_KEY, tokenInfo);
    }
  }
  
  /**
   * Exchange authorization code for user access token and refresh token
   */
  public TokenInfo exchangeCodeForToken(String code, HttpSession session) throws Exception {
    String appAccessToken = getAppAccessToken();
    
    String url = "https://open.larksuite.com/open-apis/authen/v1/access_token";
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (appAccessToken != null) {
      headers.setBearerAuth(appAccessToken);  // Authorization: Bearer <app_access_token>
    }
    
    Map<String, String> body = Map.of(
        "grant_type", "authorization_code",
        "code", code
    );
    
    HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
    
    ResponseEntity<LarkTokenResponse> resp;
    try {
      resp = restTemplate.postForEntity(url, entity, LarkTokenResponse.class);
    } catch (RestClientException e) {
      log.error("Error calling exchangeCodeForUserToken API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to exchange code for user token: " + e.getMessage(), e);
    }
    
    LarkTokenResponse result = resp.getBody();
    if (result == null) {
      throw new RuntimeException("Empty response from Lark");
    }
    if (result.getCode() != 0) {
      throw new RuntimeException("Lark error: " + result.getCode() + " - " + result.getMsg());
    }
    
    // Convert LarkTokenResponse to TokenInfo
    TokenInfo tokenInfo = new TokenInfo(
        result.getData().getAccessToken(),
        result.getData().getRefreshToken(),
        result.getData().getExpiresIn(),
        result.getData().getTokenType()
    );
    saveTokenToSession(session, tokenInfo);
    return tokenInfo;
  }
  
  /**
   * Refresh access token using refresh token
   */
  public TokenInfo refreshToken(HttpSession session) throws Exception {
    TokenInfo currentToken = getTokenFromSession(session);
    if (currentToken == null || currentToken.getRefreshToken() == null) {
      throw new RuntimeException("No refresh token available");
    }
    
    String appAccessToken = getAppAccessToken();
    String url = "https://open.larksuite.com/open-apis/authen/v1/refresh_access_token";
    
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(appAccessToken);
    
    Map<String, String> body = Map.of(
        "grant_type", "refresh_token",
        "refresh_token", currentToken.getRefreshToken()
    );
    
    HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);
    
    try {
      ResponseEntity<LarkTokenResponse> response = restTemplate.postForEntity(
          url, entity, LarkTokenResponse.class);
      
      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        LarkTokenResponse tokenResponse = response.getBody();
        if (tokenResponse != null && tokenResponse.getCode() == 0 && tokenResponse.getData() != null) {
          LarkTokenData data = tokenResponse.getData();
          TokenInfo tokenInfo = new TokenInfo(
              data.getAccessToken(),
              data.getRefreshToken() != null ? 
                  data.getRefreshToken() : currentToken.getRefreshToken(),
              data.getExpiresIn(),
              data.getTokenType()
          );
          saveTokenToSession(session, tokenInfo);
          return tokenInfo;
        } else {
          throw new RuntimeException("Failed to refresh token: " + 
              tokenResponse.getCode() + " - " + tokenResponse.getMsg());
        }
      } else {
        throw new RuntimeException("Failed to refresh token: HTTP " + response.getStatusCode());
      }
    } catch (RestClientException e) {
      log.error("Error calling refreshToken API: {}", e.getMessage(), e);
      throw new RuntimeException("Error refreshing token: " + e.getMessage(), e);
    }
  }
  
  /**
   * Get current access token (refresh if needed)
   */
  public String getAccessToken(HttpSession session) throws Exception {
    TokenInfo currentToken = getTokenFromSession(session);
    if (currentToken == null) {
      throw new RuntimeException("No token available. Please authenticate first.");
    }
    
    // Refresh if expired or about to expire in 5 minutes
    if (currentToken.isExpired() || 
        LocalDateTime.now().plusMinutes(5).isAfter(currentToken.getExpiresAt())) {
      refreshToken(session);
      currentToken = getTokenFromSession(session);
    }
    
    return currentToken.getAccessToken();
  }
  
  /**
   * Get current token info from session
   */
  public TokenInfo getCurrentToken(HttpSession session) {
    return getTokenFromSession(session);
  }
  
  /**
   * Check if token is available in session
   */
  public boolean hasToken(HttpSession session) {
    TokenInfo token = getTokenFromSession(session);
    return token != null && token.getAccessToken() != null;
  }
  
  // Inner classes for JSON response mapping
  
  /**
   * Response class for app access token
   */
  private static class AppTokenResponse {
    @JsonProperty("code")
    private int code;
    
    @JsonProperty("msg")
    private String msg;
    
    @JsonProperty("app_access_token")
    private String appAccessToken;
    
    @JsonProperty("expire")
    private int expire;
    
    public int getCode() {
      return code;
    }
    
    public String getMsg() {
      return msg;
    }
    
    public String getAppAccessToken() {
      return appAccessToken;
    }
    
    public int getExpire() {
      return expire;
    }
  }
  
  /**
   * Response class for user token (LarkTokenResponse)
   */
  public static class LarkTokenResponse {
    @JsonProperty("code")
    private int code;
    
    @JsonProperty("msg")
    private String msg;
    
    @JsonProperty("data")
    private LarkTokenData data;
    
    public int getCode() {
      return code;
    }
    
    public String getMsg() {
      return msg;
    }
    
    public LarkTokenData getData() {
      return data;
    }
  }
  
  /**
   * Data class for user token
   */
  public static class LarkTokenData {
    @JsonProperty("access_token")
    private String accessToken;
    
    @JsonProperty("refresh_token")
    private String refreshToken;
    
    @JsonProperty("expires_in")
    private int expiresIn;
    
    @JsonProperty("token_type")
    private String tokenType;
    
    public String getAccessToken() {
      return accessToken;
    }
    
    public String getRefreshToken() {
      return refreshToken;
    }
    
    public int getExpiresIn() {
      return expiresIn;
    }
    
    public String getTokenType() {
      return tokenType;
    }
  }
}

