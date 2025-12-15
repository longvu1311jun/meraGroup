package org.report.backend.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import org.report.backend.model.BitableTable;
import org.report.backend.model.TokenInfo;
import org.report.backend.model.UserConfigDto;
import org.report.backend.model.PosUser;
import org.report.backend.service.BitableService;
import org.report.backend.service.LarkTokenService;
import org.report.backend.service.LarkWikiService;
import org.report.backend.service.PosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class authenController {

  private static final Logger log = LoggerFactory.getLogger(authenController.class);

  @Value("${lark.app-id}")
  private String appId;

  @Value("${lark.redirect-uri}")
  private String redirectUri;

  private final LarkTokenService tokenService;
  private final PosService posService;
  private final LarkWikiService larkWikiService;
  private final BitableService bitableService;

  public authenController(LarkTokenService tokenService, PosService posService, LarkWikiService larkWikiService,
      BitableService bitableService) {
    this.tokenService = tokenService;
    this.posService = posService;
    this.larkWikiService = larkWikiService;
    this.bitableService = bitableService;
  }

  @GetMapping("/")
  public String index(Model model, HttpSession session) {
    String baseUrl = "https://open.larksuite.com/open-apis/authen/v1/index";

    String authUrl = baseUrl
        + "?app_id=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        + "&state=" + URLEncoder.encode("xyz", StandardCharsets.UTF_8);
    model.addAttribute("authUrl", authUrl);

    if (tokenService.hasToken(session)) {
      TokenInfo token = tokenService.getCurrentToken(session);
      model.addAttribute("isAuthenticated", true);
      model.addAttribute("tokenExpiresAt", token.getExpiresAt());
    } else {
      model.addAttribute("isAuthenticated", false);
    }

    return "index";
  }

  @GetMapping("/oauth/callback")
  public String oauthCallback(
      @RequestParam(value = "code", required = false) String code,
      @RequestParam(value = "state", required = false) String state,
      @RequestParam(value = "error", required = false) String error,
      HttpSession session,
      RedirectAttributes redirectAttributes) {

    if (error != null) {
      redirectAttributes.addFlashAttribute("error", "Authentication failed: " + error);
      return "redirect:/";
    }

    if (code != null) {
      try {
        TokenInfo tokenInfo = tokenService.exchangeCodeForToken(code, session);
        redirectAttributes.addFlashAttribute("success",
            "Authentication successful! Token expires at: " + tokenInfo.getExpiresAt());
      } catch (Exception e) {
        redirectAttributes.addFlashAttribute("error",
            "Failed to get token: " + e.getMessage());
      }
    }

    return "redirect:/";
  }

  private static final String SESSION_ALL_BASES = "SESSION_ALL_BASES";
  private static final String SESSION_USER_CONFIGS = "SESSION_USER_CONFIGS";
  private static final String SESSION_SALE_TABLES = "SESSION_SALE_TABLES";

  @GetMapping("/config")
  public String config(Model model, HttpSession session) {
    if (tokenService.hasToken(session)) {
      tokenService.autoRefreshTokenIfNeeded(session);

      TokenInfo token = tokenService.getCurrentToken(session);
      model.addAttribute("hasToken", true);
      model.addAttribute("accessToken", token.getAccessToken());
      model.addAttribute("refreshToken", token.getRefreshToken());
      model.addAttribute("tokenType", token.getTokenType());
      model.addAttribute("expiresIn", token.getExpiresIn());
      model.addAttribute("expiresAt", token.getExpiresAt());
      model.addAttribute("lastUpdated", token.getLastUpdated());
      model.addAttribute("isExpired", token.isExpired());

      @SuppressWarnings("unchecked")
      List<org.report.backend.model.LarkNode> cachedBases =
          (List<org.report.backend.model.LarkNode>) session.getAttribute(SESSION_ALL_BASES);

      @SuppressWarnings("unchecked")
      List<UserConfigDto> cachedUserConfigs =
          (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);

      @SuppressWarnings("unchecked")
      List<BitableTable> cachedSaleTables =
          (List<BitableTable>) session.getAttribute(SESSION_SALE_TABLES);

      if (cachedBases != null && cachedUserConfigs != null && cachedSaleTables != null) {
        log.info("Using cached data from session");
        model.addAttribute("allBases", cachedBases);
        model.addAttribute("userConfigs", cachedUserConfigs);
        model.addAttribute("saleTables", cachedSaleTables);
      } else {
        try {
          loadAndCacheData(session, model);
        } catch (Exception e) {
          log.error("Error loading config data: {}", e.getMessage(), e);
          model.addAttribute("allBases", new ArrayList<>());
          model.addAttribute("userConfigs", new ArrayList<>());
          model.addAttribute("saleTables", new ArrayList<>());
        }
      }
    } else {
      model.addAttribute("hasToken", false);
      model.addAttribute("allBases", new ArrayList<>());
      model.addAttribute("userConfigs", new ArrayList<>());
      model.addAttribute("saleTables", new ArrayList<>());
    }
    return "config";
  }

  @PostMapping("/config/refresh")
  public String refreshData(HttpSession session, RedirectAttributes redirectAttributes) {
    if (!tokenService.hasToken(session)) {
      redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập trước");
      return "redirect:/config";
    }

    try {
      TokenInfo token = tokenService.getCurrentToken(session);
      if (token != null && token.isExpired()) {
        try {
          tokenService.refreshToken(session);
          log.info("Token refreshed successfully before loading data");
        } catch (Exception tokenError) {
          log.error("Failed to refresh expired token: {}", tokenError.getMessage());
          redirectAttributes.addFlashAttribute("error",
              "Token đã hết hạn và không thể làm mới. Vui lòng <a href='/'>đăng nhập lại</a>");
          return "redirect:/config";
        }
      }

      session.removeAttribute(SESSION_ALL_BASES);
      session.removeAttribute(SESSION_USER_CONFIGS);
      session.removeAttribute(SESSION_SALE_TABLES);

      Model model = new org.springframework.ui.ExtendedModelMap();
      loadAndCacheData(session, model);

      redirectAttributes.addFlashAttribute("success", "Đã làm mới dữ liệu thành công!");
    } catch (Exception e) {
      log.error("Error refreshing data: {}", e.getMessage(), e);
      String errorMsg = e.getMessage();
      if (errorMsg != null && errorMsg.contains("invalid tenant access token")) {
        redirectAttributes.addFlashAttribute("error",
            "Token không hợp lệ. Vui lòng <a href='/'>đăng nhập lại</a>");
      } else {
        redirectAttributes.addFlashAttribute("error", "Lỗi khi làm mới dữ liệu: " + errorMsg);
      }
    }

    return "redirect:/config";
  }

  private void loadAndCacheData(HttpSession session, Model model) throws Exception {
    List<org.report.backend.model.LarkNode> allNodes = larkWikiService.getAllNodesWithChildren(session);

    List<PosUser> posUsers = posService.getUsers();
    Map<PosUser, org.report.backend.model.LarkNode> matchedMap =
        larkWikiService.matchUsersWithNodes(posUsers, session);

    List<UserConfigDto> userConfigs = new ArrayList<>();
    for (PosUser posUser : posUsers) {
      org.report.backend.model.LarkNode matchedNode = matchedMap.get(posUser);
      userConfigs.add(new UserConfigDto(posUser, matchedNode));
    }

    // ✅ Lấy table sale từ Bitable API
    List<BitableTable> saleTables = bitableService.getSaleTables(session);

    session.setAttribute(SESSION_ALL_BASES, allNodes);
    session.setAttribute(SESSION_USER_CONFIGS, userConfigs);
    session.setAttribute(SESSION_SALE_TABLES, saleTables);

    model.addAttribute("allBases", allNodes);
    model.addAttribute("userConfigs", userConfigs);
    model.addAttribute("saleTables", saleTables);

    log.info("Data loaded and cached to session");
  }
}
