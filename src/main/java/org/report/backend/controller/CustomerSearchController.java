package org.report.backend.controller;

import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import jakarta.annotation.PreDestroy;
import org.report.backend.model.BitableRecord;
import org.report.backend.model.UserConfigDto;
import org.report.backend.service.BitableService;
import org.report.backend.service.LarkTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.HashMap;

@Controller
public class CustomerSearchController {

  private static final Logger log = LoggerFactory.getLogger(CustomerSearchController.class);
  private static final String SESSION_USER_CONFIGS = "SESSION_USER_CONFIGS";
  
  // View IDs
  private static final String KHACH_HANG_VIEW_ID = "vew5Ou4Kee";
  private static final String TRAO_DOI_VIEW_ID = "vewNXdsB3K";
  private static final String LICH_HEN_VIEW_ID = "vewRa6d1vZ";

  private final LarkTokenService tokenService;
  private final BitableService bitableService;
  private final ExecutorService executorService;

  public CustomerSearchController(LarkTokenService tokenService, BitableService bitableService) {
    this.tokenService = tokenService;
    this.bitableService = bitableService;
    // Tạo thread pool với 5 threads để xử lý các API calls song song
    this.executorService = Executors.newFixedThreadPool(5);
  }

  @GetMapping("/tra_cuu_khach_hang")
  public String searchCustomerPage(Model model, HttpSession session) {
    if (!tokenService.hasToken(session)) {
      return "redirect:/";
    }
    return "searchCustomer";
  }

  @GetMapping("/api/tra_cuu_khach_hang")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> searchCustomerApi(
      @RequestParam("phoneNumber") String phoneNumber,
      HttpSession session) {
    
    Map<String, Object> response = new HashMap<>();
    
    // Ghi lại thời gian bắt đầu tra cứu
    long startTime = System.currentTimeMillis();
    SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    String searchTime = dateFormat.format(new Date(startTime));
    
    if (!tokenService.hasToken(session)) {
      response.put("error", "Vui lòng đăng nhập trước");
      return ResponseEntity.ok(response);
    }

    try {
      tokenService.autoRefreshTokenIfNeeded(session);

      // Lấy danh sách user configs từ session
      @SuppressWarnings("unchecked")
      List<UserConfigDto> userConfigs = (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);

      if (userConfigs == null || userConfigs.isEmpty()) {
        response.put("error", "Chưa có dữ liệu cấu hình. Vui lòng vào trang /config để load dữ liệu trước.");
        return ResponseEntity.ok(response);
      }

      // Tìm kiếm trong tất cả các CSKH
      BitableRecord foundCustomer = null;
      String foundBaseId = null;
      String foundLichHenTableId = null;
      String foundTraoDoiTableId = null;
      String foundCskhName = null;

      for (UserConfigDto userConfig : userConfigs) {
        String baseId = userConfig.getBaseId();
        String khachHangTableId = userConfig.getKhachHangTableId();

        if (baseId == null || baseId.isBlank() || khachHangTableId == null || khachHangTableId.isBlank()) {
          continue;
        }

        try {
          List<BitableRecord> customers = bitableService.searchCustomerByPhone(
              session, baseId, khachHangTableId, phoneNumber, KHACH_HANG_VIEW_ID);

          if (customers != null && !customers.isEmpty()) {
            foundCustomer = customers.get(0);
            foundBaseId = baseId;
            foundLichHenTableId = userConfig.getLichHenTableId();
            foundTraoDoiTableId = userConfig.getTraoDoiTableId();
            // Lấy tên CSKH từ mapping
            foundCskhName = userConfig.getPosName();
            if (foundCskhName == null || foundCskhName.isBlank()) {
              foundCskhName = userConfig.getLarkName();
            }
            log.info("✅ Found customer in baseId: {}, tableId: {}, CSKH: {}", baseId, khachHangTableId, foundCskhName);
            break;
          }
        } catch (Exception e) {
          log.warn("Error searching customer in baseId {}: {}", baseId, e.getMessage());
        }
      }

      if (foundCustomer == null) {
        response.put("error", "Không tìm thấy khách hàng với số điện thoại: " + phoneNumber);
        return ResponseEntity.ok(response);
      }

      // Lấy thông tin khách hàng và format các field phức tạp
      Map<String, Object> customerFields = foundCustomer.getFields();
      String customerRecordId = foundCustomer.getRecordId();
      
      formatCustomerFields(customerFields);

      // ✅ Tạo các biến final để dùng trong lambda
      final String finalBaseId = foundBaseId;
      final String finalTraoDoiTableId = foundTraoDoiTableId;
      final String finalLichHenTableId = foundLichHenTableId;
      final String finalCustomerRecordId = customerRecordId;
      final String customerMaKH = (String) customerFields.get("Mã KH");
      final HttpSession finalSession = session;

      // ✅ Dùng đa tiến trình để chạy song song 2 API: trao đổi và lịch hẹn
      CompletableFuture<List<Map<String, Object>>> traoDoiFuture = CompletableFuture.supplyAsync(() -> {
        List<Map<String, Object>> traoDoiList = new ArrayList<>();
        if (finalTraoDoiTableId != null && !finalTraoDoiTableId.isBlank()) {
          try {
            List<BitableRecord> records = bitableService.searchRecordsByCustomerId(
                finalSession, finalBaseId, finalTraoDoiTableId, finalCustomerRecordId,
                List.of("Khách Hàng", "Nội dung", "Ngày"), TRAO_DOI_VIEW_ID);
            for (BitableRecord r : records) {
              Map<String, Object> item = new HashMap<>();
              item.put("customerId", customerMaKH != null ? customerMaKH : finalCustomerRecordId);
              item.put("date", formatDate(extractFieldValue(r.getFields(), "Ngày")));
              item.put("content", extractFieldValue(r.getFields(), "Nội dung"));
              traoDoiList.add(item);
            }
            log.info("✅ Loaded {} trao doi records", traoDoiList.size());
          } catch (Exception e) {
            log.warn("Error loading trao doi: {}", e.getMessage());
          }
        }
        return traoDoiList;
      }, executorService);

      CompletableFuture<List<Map<String, Object>>> lichHenFuture = CompletableFuture.supplyAsync(() -> {
        List<Map<String, Object>> lichHenList = new ArrayList<>();
        if (finalLichHenTableId != null && !finalLichHenTableId.isBlank()) {
          try {
            List<BitableRecord> records = bitableService.searchRecordsByCustomerId(
                finalSession, finalBaseId, finalLichHenTableId, finalCustomerRecordId,
                List.of("Khách Hàng", "Công Việc", "Ngày Bắt Đầu", "Trạng Thái", "Ngày Kết Thúc"), LICH_HEN_VIEW_ID);
            for (BitableRecord r : records) {
              Map<String, Object> item = new HashMap<>();
              item.put("customerId", customerMaKH != null ? customerMaKH : finalCustomerRecordId);
              item.put("task", extractFieldValue(r.getFields(), "Công Việc"));
              item.put("status", extractStatusText(r.getFields(), "Trạng Thái"));
              item.put("start", formatDate(extractFieldValue(r.getFields(), "Ngày Bắt Đầu")));
              item.put("end", formatDate(extractFieldValue(r.getFields(), "Ngày Kết Thúc")));
              lichHenList.add(item);
            }
            log.info("✅ Loaded {} lich hen records", lichHenList.size());
          } catch (Exception e) {
            log.warn("Error loading lich hen: {}", e.getMessage());
          }
        }
        return lichHenList;
      }, executorService);

      // Chờ cả 2 API hoàn thành (tối đa 30 giây)
      try {
        CompletableFuture.allOf(traoDoiFuture, lichHenFuture).get(30, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        log.error("Timeout waiting for API calls: {}", e.getMessage());
        response.put("error", "Timeout khi tải dữ liệu. Vui lòng thử lại.");
        return ResponseEntity.ok(response);
      } catch (Exception e) {
        log.error("Error waiting for API calls: {}", e.getMessage(), e);
        response.put("error", "Lỗi khi tải dữ liệu: " + e.getMessage());
        return ResponseEntity.ok(response);
      }
      
      List<Map<String, Object>> traoDoiList = traoDoiFuture.get();
      List<Map<String, Object>> lichHenList = lichHenFuture.get();

      // Build response
      List<Map<String, Object>> customersList = new ArrayList<>();
      Map<String, Object> customerData = new HashMap<>();
      customerData.put("id", customerFields.get("Mã KH"));
      customerData.put("name", customerFields.get("Tên khách hàng"));
      customerData.put("phone", customerFields.get("Điện thoại"));
      customerData.put("address", customerFields.get("Địa chỉ"));
      customerData.put("note", customerFields.get("Tên Liệu Trình"));
      customerData.put("cskh", foundCskhName != null ? foundCskhName : "-");
      customersList.add(customerData);

      response.put("customers", customersList);
      response.put("notes", traoDoiList);
      response.put("appointments", lichHenList);

      // Tính thời gian phản hồi
      long endTime = System.currentTimeMillis();
      long responseTime = endTime - startTime;
      response.put("searchTime", searchTime);
      response.put("responseTime", responseTime);
      response.put("responseTimeFormatted", String.format("%.2f giây", responseTime / 1000.0));

    } catch (Exception e) {
      log.error("Error searching customer: {}", e.getMessage(), e);
      response.put("error", "Lỗi khi tìm kiếm khách hàng: " + e.getMessage());
      
      // Vẫn thêm thời gian tra cứu ngay cả khi có lỗi
      long endTime = System.currentTimeMillis();
      long responseTime = endTime - startTime;
      response.put("searchTime", searchTime);
      response.put("responseTime", responseTime);
      response.put("responseTimeFormatted", String.format("%.2f giây", responseTime / 1000.0));
    }

    return ResponseEntity.ok(response);
  }

  private String extractFieldValue(Map<String, Object> fields, String fieldName) {
    if (fields == null) return "";
    Object value = fields.get(fieldName);
    if (value == null) return "";
    if (value instanceof String) return (String) value;
    if (value instanceof Number) return String.valueOf(value);
    if (value instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) value;
      if (list.isEmpty()) return "";
      Object first = list.get(0);
      if (first instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) first;
        Object text = map.get("text");
        return text != null ? text.toString() : first.toString();
      }
      return first.toString();
    }
    return value.toString();
  }

  /**
   * Extract text từ trạng thái object (có thể là Map với structure phức tạp)
   */
  private String extractStatusText(Map<String, Object> fields, String fieldName) {
    if (fields == null) return "";
    Object value = fields.get(fieldName);
    if (value == null) return "";
    
    // Nếu là Map (object structure)
    if (value instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) value;
      
      // Tìm trong value array
      Object valueObj = map.get("value");
      if (valueObj instanceof List) {
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) valueObj;
        if (!list.isEmpty()) {
          Object first = list.get(0);
          if (first instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> firstMap = (Map<String, Object>) first;
            Object text = firstMap.get("text");
            if (text != null) return text.toString();
          }
        }
      }
      
      // Fallback: tìm text trực tiếp
      Object text = map.get("text");
      if (text != null) return text.toString();
    }
    
    // Nếu là List
    if (value instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) value;
      if (!list.isEmpty()) {
        Object first = list.get(0);
        if (first instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> map = (Map<String, Object>) first;
          Object text = map.get("text");
          if (text != null) return text.toString();
        }
        return first.toString();
      }
    }
    
    return value.toString();
  }

  /**
   * Format ngày từ timestamp (milliseconds) hoặc string sang dd/MM/yyyy
   */
  private String formatDate(String dateStr) {
    if (dateStr == null || dateStr.isBlank() || "-".equals(dateStr)) {
      return "-";
    }
    
    try {
      // Thử parse như timestamp (milliseconds)
      long timestamp = Long.parseLong(dateStr);
      Date date = new Date(timestamp);
      SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
      return sdf.format(date);
    } catch (NumberFormatException e) {
      // Nếu không phải số, thử parse như date string
      try {
        // Thử các format phổ biến
        SimpleDateFormat[] formats = {
          new SimpleDateFormat("yyyy-MM-dd"),
          new SimpleDateFormat("yyyy/MM/dd"),
          new SimpleDateFormat("dd/MM/yyyy"),
          new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
          new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        };
        
        for (SimpleDateFormat format : formats) {
          try {
            Date date = format.parse(dateStr);
            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy");
            return outputFormat.format(date);
          } catch (Exception ignored) {
            // Thử format tiếp theo
          }
        }
      } catch (Exception ignored) {
        // Nếu không parse được, trả về nguyên bản
      }
      
      // Nếu không parse được, trả về nguyên bản
      return dateStr;
    }
  }

  /**
   * Format các field phức tạp (list, object) thành string để hiển thị
   */
  private void formatCustomerFields(Map<String, Object> fields) {
    if (fields == null) return;

    // Format "Tên khách hàng" - có thể là list of objects
    Object tenKhachHang = fields.get("Tên khách hàng");
    if (tenKhachHang instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) tenKhachHang;
      StringBuilder sb = new StringBuilder();
      for (Object item : list) {
        if (item instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> map = (Map<String, Object>) item;
          Object text = map.get("text");
          if (text != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(text);
          }
        } else if (item != null) {
          if (sb.length() > 0) sb.append(", ");
          sb.append(item);
        }
      }
      fields.put("Tên khách hàng", sb.length() > 0 ? sb.toString() : "-");
    }

    // Format "Địa chỉ" - có thể là list of objects
    Object diaChi = fields.get("Địa chỉ");
    if (diaChi instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) diaChi;
      StringBuilder sb = new StringBuilder();
      for (Object item : list) {
        if (item instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> map = (Map<String, Object>) item;
          Object text = map.get("text");
          if (text != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(text);
          }
        } else if (item != null) {
          if (sb.length() > 0) sb.append(", ");
          sb.append(item);
        }
      }
      fields.put("Địa chỉ", sb.length() > 0 ? sb.toString() : "-");
    }

    // Format "Tên Liệu Trình" - có thể là list
    Object tenLieuTrinh = fields.get("Tên Liệu Trình");
    if (tenLieuTrinh instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) tenLieuTrinh;
      fields.put("Tên Liệu Trình", String.join(", ", 
          list.stream().map(Object::toString).toArray(String[]::new)));
    }
  }

  @PreDestroy
  public void cleanup() {
    if (executorService != null && !executorService.isShutdown()) {
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
      log.info("✅ ExecutorService shutdown completed");
    }
  }
}

