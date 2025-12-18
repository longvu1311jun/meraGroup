package org.report.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PasswordInterceptor implements HandlerInterceptor {

  private static final Logger log = LoggerFactory.getLogger(PasswordInterceptor.class);
  private static final String SESSION_PASSWORD_VERIFIED = "SESSION_PASSWORD_VERIFIED";
  private static final String CORRECT_PASSWORD = "131102";

  // Các path không cần bảo vệ
  private static final String[] PUBLIC_PATHS = {
      "/saleReport",
      "/stats",
      "/saleReport/refresh",
      "/stats/refresh",
      "/oauth/callback", // OAuth callback cần public
      "/error",
      "/favicon.ico",
      "/css/",
      "/js/",
      "/images/",
      "/static/"
  };

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    
    // Loại bỏ context path nếu có
    if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }
    
    // Kiểm tra xem path có trong danh sách public không
    if (isPublicPath(path)) {
      return true;
    }

    HttpSession session = request.getSession();
    Boolean verified = (Boolean) session.getAttribute(SESSION_PASSWORD_VERIFIED);

    if (verified == null || !verified) {
      log.info("🔒 Password required for path: {}", path);
      response.sendRedirect(request.getContextPath() + "/password?redirect=" + 
          java.net.URLEncoder.encode(path, "UTF-8"));
      return false;
    }

    return true;
  }

  private boolean isPublicPath(String path) {
    if (path == null || path.isEmpty()) {
      return false;
    }
    
    for (String publicPath : PUBLIC_PATHS) {
      if (path.equals(publicPath) || path.startsWith(publicPath)) {
        return true;
      }
    }
    return false;
  }

  public static void setPasswordVerified(HttpSession session) {
    session.setAttribute(SESSION_PASSWORD_VERIFIED, true);
  }

  public static void clearPasswordVerified(HttpSession session) {
    session.removeAttribute(SESSION_PASSWORD_VERIFIED);
  }
}

