package org.report.backend.service;

import java.util.concurrent.atomic.AtomicReference;
import org.report.backend.model.TokenInfo;
import org.springframework.stereotype.Service;

/**
 * Global in-memory token storage ("project 1 style").
 *
 * ⚠️ This stores ONE token set for the whole application.
 * If multiple people login, the last login will overwrite the token.
 */
@Service
public class TokenStorageService {

  private final AtomicReference<TokenInfo> tokenRef = new AtomicReference<>();

  public boolean hasToken() {
    TokenInfo t = tokenRef.get();
    return t != null && t.getAccessToken() != null && !t.getAccessToken().isBlank();
  }

  public TokenInfo get() {
    return tokenRef.get();
  }

  public void save(TokenInfo tokenInfo) {
    tokenRef.set(tokenInfo);
  }

  public void clear() {
    tokenRef.set(null);
  }
}
