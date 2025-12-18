package org.report.backend.controller;

import jakarta.servlet.http.HttpSession;
import org.report.backend.config.PasswordInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordController {

  private static final Logger log = LoggerFactory.getLogger(PasswordController.class);
  private static final String CORRECT_PASSWORD = "131102";

  @GetMapping("/password")
  public String passwordPage(
      @RequestParam(value = "redirect", required = false, defaultValue = "/") String redirect,
      Model model) {
    model.addAttribute("redirect", redirect);
    return "password";
  }

  @PostMapping("/password/verify")
  public String verifyPassword(
      @RequestParam("password") String password,
      @RequestParam(value = "redirect", required = false, defaultValue = "/") String redirect,
      HttpSession session,
      RedirectAttributes redirectAttributes) {
    
    if (CORRECT_PASSWORD.equals(password)) {
      PasswordInterceptor.setPasswordVerified(session);
      log.info("✅ Password verified successfully, redirecting to: {}", redirect);
      return "redirect:" + redirect;
    } else {
      log.warn("❌ Invalid password attempt");
      redirectAttributes.addFlashAttribute("error", "Mật khẩu không đúng. Vui lòng thử lại.");
      return "redirect:/password?redirect=" + 
          java.net.URLEncoder.encode(redirect, java.nio.charset.StandardCharsets.UTF_8);
    }
  }
}

