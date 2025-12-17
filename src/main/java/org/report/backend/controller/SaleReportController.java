package org.report.backend.controller;

import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.report.backend.model.BitableTable;
import org.report.backend.model.SaleReportCacheEntry;
import org.report.backend.model.SaleSummaryRow;
import org.report.backend.service.BitableService;
import org.report.backend.service.LarkTokenService;
import org.report.backend.service.SaleReportCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SaleReportController {

  private static final Logger log = LoggerFactory.getLogger(SaleReportController.class);

  private static final String SESSION_SALE_SUMMARY = "SESSION_SALE_SUMMARY";
  private static final String SESSION_SALE_RANGE = "SESSION_SALE_RANGE";
  private static final String SESSION_SALE_FETCHED_AT = "SESSION_SALE_FETCHED_AT";

  private final LarkTokenService tokenService;
  private final BitableService bitableService;
  private final SaleReportCacheService cacheService;

  public SaleReportController(LarkTokenService tokenService, BitableService bitableService, SaleReportCacheService cacheService) {
    this.tokenService = tokenService;
    this.bitableService = bitableService;
    this.cacheService = cacheService;
  }

  @GetMapping("/saleReport")
  public String saleReport(
      @RequestParam(value = "range", required = false, defaultValue = "CurrentMonth") String range,
      Model model,
      HttpSession session
  ) {
    if (!tokenService.hasToken(session)) {
      model.addAttribute("hasToken", false);
      model.addAttribute("rows", List.of());
      model.addAttribute("range", range);
      return "saleReport";
    }

    log.info("🔍 Checking token status for /saleReport endpoint");
    tokenService.autoRefreshTokenIfNeeded(session);

    // 1) ✅ session cache
    @SuppressWarnings("unchecked")
    List<SaleSummaryRow> cached = (List<SaleSummaryRow>) session.getAttribute(SESSION_SALE_SUMMARY);
    String cachedRange = (String) session.getAttribute(SESSION_SALE_RANGE);
    LocalDateTime fetchedAt = (LocalDateTime) session.getAttribute(SESSION_SALE_FETCHED_AT);

    if (cached != null && cachedRange != null && cachedRange.equals(range) && fetchedAt != null) {
      model.addAttribute("hasToken", true);
      model.addAttribute("rows", cached);
      model.addAttribute("range", cachedRange);
      model.addAttribute("fetchedAt", fetchedAt);
      model.addAttribute("totalAgents", cached.size());
      model.addAttribute("fromCache", "SESSION");
      return "saleReport";
    }

    // 2) ✅ disk cache
    Optional<SaleReportCacheEntry> disk = cacheService.get(range);
    if (disk.isPresent()) {
      SaleReportCacheEntry entry = disk.get();
      LocalDateTime dt = LocalDateTime.ofInstant(
          Instant.ofEpochMilli(entry.getFetchedAtEpochMs()),
          ZoneId.systemDefault()
      );

      session.setAttribute(SESSION_SALE_SUMMARY, entry.getRows());
      session.setAttribute(SESSION_SALE_RANGE, range);
      session.setAttribute(SESSION_SALE_FETCHED_AT, dt);

      model.addAttribute("hasToken", true);
      model.addAttribute("rows", entry.getRows());
      model.addAttribute("range", range);
      model.addAttribute("fetchedAt", dt);
      model.addAttribute("totalAgents", entry.getRows() != null ? entry.getRows().size() : 0);
      model.addAttribute("fromCache", "DISK");
      return "saleReport";
    }

    // 3) ❌ cache miss -> thống kê thật
    try {
      List<BitableTable> saleTables = bitableService.getSaleTables(session);
      List<SaleSummaryRow> rows = new ArrayList<>();

      for (BitableTable t : saleTables) {
        rows.add(bitableService.buildSaleSummaryForTable(session, t, range));
      }

      long nowMs = Instant.now().toEpochMilli();
      LocalDateTime nowDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault());

      // save to session
      session.setAttribute(SESSION_SALE_SUMMARY, rows);
      session.setAttribute(SESSION_SALE_RANGE, range);
      session.setAttribute(SESSION_SALE_FETCHED_AT, nowDt);

      // save to disk
      cacheService.put(new SaleReportCacheEntry(range, nowMs, rows));

      model.addAttribute("hasToken", true);
      model.addAttribute("rows", rows);
      model.addAttribute("range", range);
      model.addAttribute("fetchedAt", nowDt);
      model.addAttribute("totalAgents", rows.size());
      model.addAttribute("fromCache", "LIVE");

    } catch (Exception e) {
      log.error("Error loading sale report: {}", e.getMessage(), e);
      model.addAttribute("hasToken", true);
      model.addAttribute("rows", List.of());
      model.addAttribute("range", range);
      model.addAttribute("error", e.getMessage());
    }

    return "saleReport";
  }

  @PostMapping("/saleReport/refresh")
  public String refreshSaleReport(
      @RequestParam(value = "range", required = false, defaultValue = "CurrentMonth") String range,
      HttpSession session
  ) {
    // clear session
    session.removeAttribute(SESSION_SALE_SUMMARY);
    session.removeAttribute(SESSION_SALE_RANGE);
    session.removeAttribute(SESSION_SALE_FETCHED_AT);

    // clear disk
    cacheService.clear(range);

    return "redirect:/saleReport?range=" + range;
  }
}
