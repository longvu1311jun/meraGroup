package org.report.backend.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jakarta.annotation.PreDestroy;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.text.SimpleDateFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpSession;
import org.report.backend.model.BitableTable;
import org.report.backend.model.BitableRecord;
import org.report.backend.model.TokenInfo;
import org.report.backend.model.UserConfigDto;
import org.report.backend.model.EmployeeStatsDto;
import org.report.backend.model.PosUser;
import org.report.backend.service.BitableService;
import org.report.backend.service.LarkTokenService;
import org.report.backend.service.LarkWikiService;
import org.report.backend.service.PosService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.HashMap;

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
  private final ExecutorService executorService;

  public authenController(LarkTokenService tokenService, PosService posService, LarkWikiService larkWikiService,
      BitableService bitableService) {
    this.tokenService = tokenService;
    this.posService = posService;
    this.larkWikiService = larkWikiService;
    this.bitableService = bitableService;
    // Tạo thread pool với 5 threads để xử lý stats song song
    this.executorService = Executors.newFixedThreadPool(5);
  }

  @PreDestroy
  public void destroy() {
    if (executorService != null) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
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
  private static final String SESSION_EMPLOYEE_STATS = "SESSION_EMPLOYEE_STATS";
  private static final String SESSION_EMPLOYEE_STATS_FETCHED_AT = "SESSION_EMPLOYEE_STATS_FETCHED_AT";
  // Cache riêng cho LastMonth để khi người dùng chuyển qua lại không phải load lại
  private static final String SESSION_EMPLOYEE_STATS_LAST = "SESSION_EMPLOYEE_STATS_LAST";
  private static final String SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT = "SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT";

  @GetMapping("/config")
  public String config(Model model, HttpSession session) {
    if (tokenService.hasToken(session)) {
      log.info("🔍 Checking token status for /config endpoint");
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
      UserConfigDto userConfig = new UserConfigDto(posUser, matchedNode);
      
      // ✅ Lấy Table ID cho ba bảng: Khách Hàng, Lịch Hẹn, Trao Đổi
      String baseId = userConfig.getBaseId();
      if (baseId != null && !baseId.isBlank()) {
        try {
          List<BitableTable> tables = bitableService.getTablesByBaseId(session, baseId);
          for (BitableTable table : tables) {
            String tableName = table.getName();
            String tableId = table.getTableId();
            
            if (tableName != null && tableId != null) {
              if (tableName.equals("Khách Hàng")) {
                userConfig.setKhachHangTableId(tableId);
              } else if (tableName.equals("Lịch Hẹn")) {
                userConfig.setLichHenTableId(tableId);
              } else if (tableName.equals("Trao Đổi")) {
                userConfig.setTraoDoiTableId(tableId);
              }
            }
          }
        } catch (Exception e) {
          log.warn("Failed to get tables for baseId {}: {}", baseId, e.getMessage());
        }
      }
      
      userConfigs.add(userConfig);
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

  @GetMapping("/stats")
  public String stats(
      @RequestParam(value = "customerMonth", required = false, defaultValue = "CurrentMonth") String customerMonth,
      Model model, 
      HttpSession session) {
    if (!tokenService.hasToken(session)) {
      return "redirect:/";
    }

    // ✅ Render view ngay, không chờ data
    model.addAttribute("customerMonth", customerMonth);
    return "stats";
  }

  @GetMapping("/api/stats/data")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> getStatsData(
      @RequestParam(value = "customerMonth", required = false, defaultValue = "CurrentMonth") String customerMonth,
      HttpSession session) {
    Map<String, Object> response = new HashMap<>();
    
    if (!tokenService.hasToken(session)) {
      response.put("error", "Vui lòng đăng nhập trước");
      return ResponseEntity.ok(response);
    }

    try {
      log.info("🔍 Loading stats data for customerMonth: {}", customerMonth);
      tokenService.autoRefreshTokenIfNeeded(session);

      // Validate customerMonth parameter
      if (!customerMonth.equals("CurrentMonth") && !customerMonth.equals("LastMonth")) {
        customerMonth = "CurrentMonth";
      }

      // 1) ✅ Kiểm tra cache trong session cho cả CurrentMonth và LastMonth
      @SuppressWarnings("unchecked")
      List<EmployeeStatsDto> cachedStatsCurrent =
          (List<EmployeeStatsDto>) session.getAttribute(SESSION_EMPLOYEE_STATS);
      LocalDateTime fetchedAtCurrent =
          (LocalDateTime) session.getAttribute(SESSION_EMPLOYEE_STATS_FETCHED_AT);

      @SuppressWarnings("unchecked")
      List<EmployeeStatsDto> cachedStatsLast =
          (List<EmployeeStatsDto>) session.getAttribute(SESSION_EMPLOYEE_STATS_LAST);
      LocalDateTime fetchedAtLast =
          (LocalDateTime) session.getAttribute(SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT);

      List<EmployeeStatsDto> cachedStatsToUse = null;
      LocalDateTime fetchedAtToUse = null;
      if (customerMonth.equals("CurrentMonth")) {
        cachedStatsToUse = cachedStatsCurrent;
        fetchedAtToUse = fetchedAtCurrent;
      } else if (customerMonth.equals("LastMonth")) {
        cachedStatsToUse = cachedStatsLast;
        fetchedAtToUse = fetchedAtLast;
      }

      if (cachedStatsToUse != null && fetchedAtToUse != null) {
        log.info("Using cached employee stats from session for {}", customerMonth);
        response.put("statsList", cachedStatsToUse);
        response.put("fetchedAt", fetchedAtToUse.toString());
        response.put("fromCache", true);
        response.put("customerMonth", customerMonth);

        long totalKhach = cachedStatsToUse.stream().mapToLong(EmployeeStatsDto::getTongKhach).sum();
        long totalLich = cachedStatsToUse.stream().mapToLong(EmployeeStatsDto::getTongLich).sum();
        long totalHoanThanh = cachedStatsToUse.stream().mapToLong(EmployeeStatsDto::getHoanThanh).sum();
        response.put("totalKhach", totalKhach);
        response.put("totalLich", totalLich);
        response.put("totalHoanThanh", totalHoanThanh);
        return ResponseEntity.ok(response);
      }

      // 2) ❌ Cache miss -> Lấy dữ liệu mới
      @SuppressWarnings("unchecked")
      List<UserConfigDto> cachedUserConfigs =
          (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);

      if (cachedUserConfigs == null) {
        // Nếu chưa có config, load config trước
        Model tempModel = new org.springframework.ui.ExtendedModelMap();
        loadAndCacheData(session, tempModel);
        cachedUserConfigs = (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);
      }

      // Tính toán stats với customerMonth được chọn
      List<EmployeeStatsDto> statsList = calculateEmployeeStats(session, cachedUserConfigs, customerMonth);
      
      // Lưu vào cache theo từng tháng
      long nowMs = Instant.now().toEpochMilli();
      LocalDateTime nowDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault());
      if (customerMonth.equals("CurrentMonth")) {
        session.setAttribute(SESSION_EMPLOYEE_STATS, statsList);
        session.setAttribute(SESSION_EMPLOYEE_STATS_FETCHED_AT, nowDt);
      } else {
        session.setAttribute(SESSION_EMPLOYEE_STATS_LAST, statsList);
        session.setAttribute(SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT, nowDt);
      }
      response.put("fetchedAt", nowDt.toString());
      response.put("fromCache", false);
      
      response.put("statsList", statsList);
      response.put("customerMonth", customerMonth);
      
      // Tính tổng
      long totalKhach = statsList.stream().mapToLong(EmployeeStatsDto::getTongKhach).sum();
      long totalLich = statsList.stream().mapToLong(EmployeeStatsDto::getTongLich).sum();
      long totalHoanThanh = statsList.stream().mapToLong(EmployeeStatsDto::getHoanThanh).sum();
      response.put("totalKhach", totalKhach);
      response.put("totalLich", totalLich);
      response.put("totalHoanThanh", totalHoanThanh);

    } catch (Exception e) {
      log.error("Error loading stats: {}", e.getMessage(), e);
      response.put("statsList", new ArrayList<EmployeeStatsDto>());
      response.put("error", "Lỗi khi tải thống kê: " + e.getMessage());
      response.put("customerMonth", customerMonth);
    }

    return ResponseEntity.ok(response);
  }

  @PostMapping("/stats/refresh")
  public String refreshStats(
      @RequestParam(value = "customerMonth", required = false, defaultValue = "CurrentMonth") String customerMonth,
      HttpSession session, 
      RedirectAttributes redirectAttributes) {
    if (!tokenService.hasToken(session)) {
      redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập trước");
      return "redirect:/stats";
    }

    try {
      // Xóa cache
      session.removeAttribute(SESSION_EMPLOYEE_STATS);
      session.removeAttribute(SESSION_EMPLOYEE_STATS_FETCHED_AT);
      session.removeAttribute(SESSION_EMPLOYEE_STATS_LAST);
      session.removeAttribute(SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT);
      
      redirectAttributes.addFlashAttribute("success", "Đã làm mới dữ liệu thống kê thành công!");
    } catch (Exception e) {
      log.error("Error refreshing stats: {}", e.getMessage(), e);
      redirectAttributes.addFlashAttribute("error", "Lỗi khi làm mới dữ liệu: " + e.getMessage());
    }

    return "redirect:/stats?customerMonth=" + customerMonth;
  }

  @GetMapping("/stats/export")
  public ResponseEntity<byte[]> exportStatsToExcel(
      @RequestParam(value = "customerMonth", required = false, defaultValue = "CurrentMonth") String customerMonth,
      HttpSession session) throws IOException {

    if (!tokenService.hasToken(session)) {
      return ResponseEntity.badRequest().build();
    }

    try {
      tokenService.autoRefreshTokenIfNeeded(session);

      // Validate customerMonth parameter
      if (!customerMonth.equals("CurrentMonth") && !customerMonth.equals("LastMonth")) {
        customerMonth = "CurrentMonth";
      }

      // Lấy stats từ cache nếu có (cho cả CurrentMonth và LastMonth)
      @SuppressWarnings("unchecked")
      List<EmployeeStatsDto> cachedStatsCurrent =
          (List<EmployeeStatsDto>) session.getAttribute(SESSION_EMPLOYEE_STATS);
      @SuppressWarnings("unchecked")
      List<EmployeeStatsDto> cachedStatsLast =
          (List<EmployeeStatsDto>) session.getAttribute(SESSION_EMPLOYEE_STATS_LAST);

      List<EmployeeStatsDto> statsList = null;
      if (customerMonth.equals("CurrentMonth") && cachedStatsCurrent != null) {
        statsList = cachedStatsCurrent;
      } else if (customerMonth.equals("LastMonth") && cachedStatsLast != null) {
        statsList = cachedStatsLast;
      } else {
        // Nếu không có cache, tính lại và lưu cache như getStatsData
        @SuppressWarnings("unchecked")
        List<UserConfigDto> cachedUserConfigs =
            (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);
        if (cachedUserConfigs == null) {
          Model tempModel = new org.springframework.ui.ExtendedModelMap();
          loadAndCacheData(session, tempModel);
          cachedUserConfigs = (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);
        }
        statsList = calculateEmployeeStats(session, cachedUserConfigs, customerMonth);

        long nowMs = Instant.now().toEpochMilli();
        LocalDateTime nowDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault());
        if (customerMonth.equals("CurrentMonth")) {
          session.setAttribute(SESSION_EMPLOYEE_STATS, statsList);
          session.setAttribute(SESSION_EMPLOYEE_STATS_FETCHED_AT, nowDt);
        } else {
          session.setAttribute(SESSION_EMPLOYEE_STATS_LAST, statsList);
          session.setAttribute(SESSION_EMPLOYEE_STATS_LAST_FETCHED_AT, nowDt);
        }
      }

      if (statsList == null || statsList.isEmpty()) {
        return ResponseEntity.badRequest().build();
      }

      // Xác định tháng hiển thị
      LocalDateTime nowDtLabel = LocalDateTime.now(ZoneId.systemDefault());
      int currentMonthNum = nowDtLabel.getMonthValue();
      int targetMonthNum = "CurrentMonth".equals(customerMonth)
          ? currentMonthNum
          : (currentMonthNum == 1 ? 12 : currentMonthNum - 1);
      String monthLabel = "Tháng " + targetMonthNum;

      // Tạo Excel
      Workbook workbook = new XSSFWorkbook();
      Sheet sheet = workbook.createSheet("Stats");

      // Style title
      CellStyle titleStyle = workbook.createCellStyle();
      Font titleFont = workbook.createFont();
      titleFont.setBold(true);
      titleFont.setFontHeightInPoints((short) 14);
      titleStyle.setFont(titleFont);
      titleStyle.setAlignment(HorizontalAlignment.CENTER);

      // Header style
      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerFont.setFontHeightInPoints((short) 12);
      headerStyle.setFont(headerFont);
      headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      headerStyle.setBorderBottom(BorderStyle.THIN);
      headerStyle.setBorderTop(BorderStyle.THIN);
      headerStyle.setBorderLeft(BorderStyle.THIN);
      headerStyle.setBorderRight(BorderStyle.THIN);
      headerStyle.setAlignment(HorizontalAlignment.CENTER);

      // Number style
      CellStyle numberStyle = workbook.createCellStyle();
      numberStyle.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat("#,##0"));

      // Title
      Row titleRow = sheet.createRow(0);
      Cell titleCell = titleRow.createCell(0);
      titleCell.setCellValue("Thống kê lịch hẹn CSKH " + monthLabel);
      titleCell.setCellStyle(titleStyle);
      sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 6));

      // Header
      Row headerRow = sheet.createRow(2);
      String[] headers = {
          "STT", "Tên Nhân Viên", "Tổng Khách", "Tổng Lịch", "Hoàn Thành Muộn", "Hoàn Thành", "Quá Hạn"
      };
      for (int i = 0; i < headers.length; i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      // Data
      int rowNum = 3;
      int stt = 1;
      for (EmployeeStatsDto stat : statsList) {
        Row dataRow = sheet.createRow(rowNum++);
        int col = 0;
        dataRow.createCell(col++).setCellValue(stt++);
        dataRow.createCell(col++).setCellValue(stat.getEmployeeName() != null ? stat.getEmployeeName() : "");

        Cell tongKhachCell = dataRow.createCell(col++);
        tongKhachCell.setCellValue(stat.getTongKhach());
        tongKhachCell.setCellStyle(numberStyle);

        Cell tongLichCell = dataRow.createCell(col++);
        tongLichCell.setCellValue(stat.getTongLich());
        tongLichCell.setCellStyle(numberStyle);

        Cell hoanThanhMuonCell = dataRow.createCell(col++);
        hoanThanhMuonCell.setCellValue(stat.getHoanThanhMuon());
        hoanThanhMuonCell.setCellStyle(numberStyle);

        Cell hoanThanhCell = dataRow.createCell(col++);
        hoanThanhCell.setCellValue(stat.getHoanThanh());
        hoanThanhCell.setCellStyle(numberStyle);

        Cell quaHanCell = dataRow.createCell(col++);
        quaHanCell.setCellValue(stat.getQuaHan());
        quaHanCell.setCellStyle(numberStyle);
      }

      // Auto-size columns
      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
        sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 800);
      }

      // Filename
      SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
      String fileName = "report_CSKH_" + monthLabel.replace(" ", "_") + "_" + dateFormat.format(new java.util.Date()) + ".xlsx";

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      workbook.close();

      HttpHeaders responseHeaders = new HttpHeaders();
      responseHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
      responseHeaders.setContentDispositionFormData("attachment", fileName);

      return ResponseEntity.ok()
          .headers(responseHeaders)
          .body(outputStream.toByteArray());

    } catch (Exception e) {
      log.error("Error exporting stats Excel: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError().build();
    }
  }

  private List<EmployeeStatsDto> calculateEmployeeStats(HttpSession session,
      List<UserConfigDto> userConfigs, String customerMonthRange) throws Exception {
    List<EmployeeStatsDto> statsList = Collections.synchronizedList(new ArrayList<>());

    // customerMonthRange: "CurrentMonth" hoặc "LastMonth" - dùng cho cả API lấy khách hàng và lịch hẹn
    // Cả hai API đều filter theo cùng tháng để đảm bảo tính nhất quán
    String khachHangTimeRange = customerMonthRange != null ? customerMonthRange : "CurrentMonth";
    String lichHenTimeRange = customerMonthRange != null ? customerMonthRange : "CurrentMonth";

    // ✅ Chia danh sách userConfigs thành 5 phần để xử lý song song
    int totalConfigs = userConfigs.size();
    int chunkSize = Math.max(1, (totalConfigs + 4) / 5); // Chia thành 5 phần, làm tròn lên
    
    List<List<UserConfigDto>> chunks = new ArrayList<>();
    for (int i = 0; i < totalConfigs; i += chunkSize) {
      int end = Math.min(i + chunkSize, totalConfigs);
      chunks.add(userConfigs.subList(i, end));
    }
    
    // Đảm bảo có đúng 5 chunks (nếu ít hơn thì thêm empty lists)
    while (chunks.size() < 5) {
      chunks.add(Collections.emptyList());
    }
    
    // Tạo các CompletableFuture để chạy song song
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    
    for (List<UserConfigDto> chunk : chunks) {
      if (chunk.isEmpty()) continue;
      
      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
        for (UserConfigDto userConfig : chunk) {
      String employeeName = userConfig.getPosName();
      String baseId = userConfig.getBaseId();
      String khachHangTableId = userConfig.getKhachHangTableId();
      String lichHenTableId = userConfig.getLichHenTableId();

      // Bỏ qua nhân viên không có base id - không hiển thị trong bảng thống kê
      if (baseId == null || baseId.isBlank()) {
        continue;
      }

      // Bỏ qua nhân viên không có đủ table id
      if (khachHangTableId == null || khachHangTableId.isBlank() 
          || lichHenTableId == null || lichHenTableId.isBlank()) {
        continue;
      }

      EmployeeStatsDto stats = new EmployeeStatsDto(employeeName);
      
      // Kiểm tra xem có phải nhân viên đặc biệt không
      boolean isSpecialEmployee = employeeName != null 
          && (employeeName.contains("Nguyễn Thị Lan Anh") && employeeName.contains("0333058439"));

      try {
        // 1. 获取客户列表（Record ID 会自动返回）
        // Sử dụng view_id: vew5Ou4Kee cho bảng Khách Hàng
        // Sử dụng customerMonthRange (CurrentMonth hoặc LastMonth) cho API lấy khách hàng
        List<String> fieldNamesKhachHang = List.of("Ngày tạo", "Điện thoại");
        String khachHangViewId = "vew5Ou4Kee";
        List<BitableRecord> khachHangRecords = bitableService.searchRecords(session, baseId,
            khachHangTableId, fieldNamesKhachHang, khachHangViewId, khachHangTimeRange);

        // 提取客户 Record ID 集合
        java.util.Set<String> khachHangRecordIds = new java.util.HashSet<>();
        for (BitableRecord record : khachHangRecords) {
          if (record.getRecordId() != null && !record.getRecordId().isBlank()) {
            khachHangRecordIds.add(record.getRecordId());
          }
        }
        stats.setTongKhach(khachHangRecordIds.size());

        // 2. 获取预约列表（包含 Khách Hàng 的 link_record_ids 和 Trạng Thái）
        // Sử dụng view_id: vewRa6d1vZ cho bảng Lịch Hẹn (mặc định)
        // Riêng nhân viên "Nguyễn Thị Lan Anh 0333058439" dùng vewENGQUc0
        // API lấy lịch hẹn cũng filter theo tháng tương ứng (CurrentMonth hoặc LastMonth)
        // để chỉ tính các lịch hẹn được tạo trong tháng đó
        List<String> fieldNamesLichHen = List.of("Ngày tạo", "Khách Hàng", "Trạng Thái");
        String lichHenViewId = isSpecialEmployee ? "vewENGQUc0" : "vewRa6d1vZ";
        if (isSpecialEmployee) {
          log.info("Using special view_id vewENGQUc0 for employee: {}", employeeName);
        }
        log.debug("Fetching lich hen records with timeRange: {} for employee: {}", lichHenTimeRange, employeeName);
        List<BitableRecord> lichHenRecords = bitableService.searchRecords(session, baseId,
            lichHenTableId, fieldNamesLichHen, lichHenViewId, lichHenTimeRange);

        long tongLich = 0;
        long hoanThanhMuon = 0;
        long hoanThanh = 0;
        long quaHan = 0;

        for (BitableRecord record : lichHenRecords) {
          Map<String, Object> fields = record.getFields();
          if (fields == null) continue;

          // 获取 Khách Hàng 的 link_record_ids
          Object khachHangField = fields.get("Khách Hàng");
          if (khachHangField == null) continue;

          java.util.List<String> linkRecordIds = extractLinkRecordIds(khachHangField);

          // 检查是否有任何 link_record_id 在客户列表中
          boolean hasMatchingCustomer = false;
          for (String linkRecordId : linkRecordIds) {
            if (khachHangRecordIds.contains(linkRecordId)) {
              hasMatchingCustomer = true;
              break;
            }
          }

          if (hasMatchingCustomer) {
            tongLich++;

            // 获取 Trạng Thái
            Object trangThaiField = fields.get("Trạng Thái");
            String trangThai = extractText(trangThaiField).toLowerCase();

            if (trangThai.contains("hoàn thành muộn") || trangThai.contains("hoàn thành trễ")) {
              hoanThanhMuon++;
            } else if (trangThai.contains("hoàn thành")) {
              hoanThanh++;
            } else if (trangThai.contains("quá hạn") || trangThai.contains("quá hạn")) {
              quaHan++;
            }
          }
        }

          stats.setTongLich(tongLich);
          stats.setHoanThanhMuon(hoanThanhMuon);
          stats.setHoanThanh(hoanThanh);
          stats.setQuaHan(quaHan);

        } catch (Exception e) {
          log.warn("Failed to calculate stats for employee {}: {}", employeeName, e.getMessage());
        }

        statsList.add(stats);
        }
      }, executorService);
      
      futures.add(future);
    }
    
    // Chờ tất cả các thread hoàn thành
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(120, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      log.warn("Timeout waiting for stats calculation threads");
    } catch (Exception e) {
      log.error("Error waiting for stats calculation threads: {}", e.getMessage(), e);
    }

    return statsList;
  }

  private java.util.List<String> extractLinkRecordIds(Object khachHangField) {
    java.util.List<String> result = new java.util.ArrayList<>();

    if (khachHangField instanceof Map<?, ?> map) {
      Object linkRecordIds = map.get("link_record_ids");
      if (linkRecordIds instanceof java.util.List<?> list) {
        for (Object item : list) {
          if (item instanceof String str) {
            result.add(str);
          }
        }
      }
    }

    return result;
  }

  private String extractText(Object v) {
    if (v == null) return "";
    if (v instanceof String s) return s;
    if (v instanceof Number n) return String.valueOf(n);

    if (v instanceof Map<?, ?> map) {
      Object name = map.get("name");
      if (name != null) return String.valueOf(name);
      Object text = map.get("text");
      if (text != null) return String.valueOf(text);
      Object value = map.get("value");
      if (value != null) return String.valueOf(value);
    }

    if (v instanceof java.util.List<?> list) {
      StringBuilder sb = new StringBuilder();
      for (Object it : list) {
        String part = extractText(it);
        if (!part.isBlank()) {
          if (!sb.isEmpty()) sb.append(", ");
          sb.append(part);
        }
      }
      return sb.toString();
    }

    return String.valueOf(v);
  }
}
