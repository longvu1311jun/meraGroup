package org.report.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Autowired
  private PasswordInterceptor passwordInterceptor;

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    // Redirect "/" to "/stats" - only /stats is accessible
    registry.addRedirectViewController("/", "/stats");
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    // Password interceptor disabled - no password requirement
    // registry.addInterceptor(passwordInterceptor)
    //     .addPathPatterns("/**")
    //     .excludePathPatterns(
    //         "/saleReport/**",
    //         "/stats/**",
    //         "/oauth/callback",
    //         "/password",
    //         "/password/verify",
    //         "/error",
    //         "/favicon.ico",
    //         "/css/**",
    //         "/js/**",
    //         "/images/**",
    //         "/static/**"
    //     );
  }
}

