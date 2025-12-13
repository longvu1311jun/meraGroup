package org.report.backend.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.HttpSession;
import org.report.backend.model.TokenInfo;
import org.report.backend.model.UserConfigDto;
import org.report.backend.model.PosUser;
import org.report.backend.service.LarkTokenService;
import org.report.backend.service.LarkWikiService;
import org.report.backend.service.PosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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
  
  public authenController(LarkTokenService tokenService, PosService posService, LarkWikiService larkWikiService) {
    this.tokenService = tokenService;
    this.posService = posService;
    this.larkWikiService = larkWikiService;
  }
  
  @GetMapping("/")
  public String index(Model model, HttpSession session) {
    // build URL login Lark
    String baseUrl = "https://open.larksuite.com/open-apis/authen/v1/index";

    String authUrl = baseUrl
        + "?app_id=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
        + "&state=" + URLEncoder.encode("xyz", StandardCharsets.UTF_8);
    model.addAttribute("authUrl", authUrl);
    
    // Check if user is authenticated
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
    
    // Xử lý callback từ Lark
    if (error != null) {
      // Nếu có lỗi từ Lark
      redirectAttributes.addFlashAttribute("error", "Authentication failed: " + error);
      return "redirect:/";
    }
    
    if (code != null) {
      try {
        // Exchange authorization code for access token and refresh token
        TokenInfo tokenInfo = tokenService.exchangeCodeForToken(code, session);
        redirectAttributes.addFlashAttribute("success", 
            "Authentication successful! Token expires at: " + tokenInfo.getExpiresAt());
      } catch (Exception e) {
        redirectAttributes.addFlashAttribute("error", 
            "Failed to get token: " + e.getMessage());
      }
    }
    
    // Redirect về trang index
    return "redirect:/";
  }
  
  @GetMapping("/config")
  public String config(Model model, HttpSession session) {
    // Get token info from session
    if (tokenService.hasToken(session)) {
      TokenInfo token = tokenService.getCurrentToken(session);
      model.addAttribute("hasToken", true);
      model.addAttribute("accessToken", token.getAccessToken());
      model.addAttribute("refreshToken", token.getRefreshToken());
      model.addAttribute("tokenType", token.getTokenType());
      model.addAttribute("expiresIn", token.getExpiresIn());
      model.addAttribute("expiresAt", token.getExpiresAt());
      model.addAttribute("isExpired", token.isExpired());
      
      try {
        // Get all nodes (bases) with their child nodes from Lark
        List<org.report.backend.model.LarkNode> allNodes = larkWikiService.getAllNodesWithChildren(session);
        model.addAttribute("allBases", allNodes);
        
        // Get POS users and match with Lark nodes
        List<PosUser> posUsers = posService.getUsers();
        Map<PosUser, org.report.backend.model.LarkNode> matchedMap = 
            larkWikiService.matchUsersWithNodes(posUsers, session);
        
        // Create DTO list for display
        List<UserConfigDto> userConfigs = new ArrayList<>();
        for (PosUser posUser : posUsers) {
          org.report.backend.model.LarkNode matchedNode = matchedMap.get(posUser);
          userConfigs.add(new UserConfigDto(posUser, matchedNode));
        }
        
        model.addAttribute("userConfigs", userConfigs);
      } catch (Exception e) {
        log.error("Error getting nodes: {}", e.getMessage(), e);
        model.addAttribute("allBases", new ArrayList<>());
        model.addAttribute("userConfigs", new ArrayList<>());
      }
    } else {
      model.addAttribute("hasToken", false);
      model.addAttribute("allBases", new ArrayList<>());
      model.addAttribute("userConfigs", new ArrayList<>());
    }
    return "config";
  }
}
