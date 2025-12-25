package org.report.backend.controller;

import jakarta.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;
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
      
      // AtomicReference để lưu kết quả tìm thấy (thread-safe)
      AtomicReference<BitableRecord> foundCustomerRef = new AtomicReference<>();
      AtomicReference<String> foundBaseIdRef = new AtomicReference<>();
      AtomicReference<String> foundLichHenTableIdRef = new AtomicReference<>();
      AtomicReference<String> foundTraoDoiTableIdRef = new AtomicReference<>();
      AtomicReference<String> foundCskhNameRef = new AtomicReference<>();

      // Tạo các CompletableFuture để chạy song song
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      
      for (List<UserConfigDto> chunk : chunks) {
        if (chunk.isEmpty()) continue;
        
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
          // Nếu đã tìm thấy ở thread khác, dừng lại
          if (foundCustomerRef.get() != null) {
//            System.out.println(foundCustomerRef.toString());
            return;
          }
          
          for (UserConfigDto userConfig : chunk) {
            // Nếu đã tìm thấy ở thread khác, dừng lại
            if (foundCustomerRef.get() != null) {
              break;
            }
            
            String baseId = userConfig.getBaseId();
            String khachHangTableId = userConfig.getKhachHangTableId();

            if (baseId == null || baseId.isBlank() || khachHangTableId == null || khachHangTableId.isBlank()) {
              continue;
            }

            try {
              List<BitableRecord> customers = bitableService.searchCustomerByPhone(
                  session, baseId, khachHangTableId, phoneNumber, KHACH_HANG_VIEW_ID);

              if (customers != null && !customers.isEmpty()) {
                // Chỉ set nếu chưa có thread nào set (atomic check-and-set)
                if (foundCustomerRef.compareAndSet(null, customers.get(0))) {
                  foundBaseIdRef.set(baseId);
                  foundLichHenTableIdRef.set(userConfig.getLichHenTableId());
                  foundTraoDoiTableIdRef.set(userConfig.getTraoDoiTableId());
                  // Lấy tên CSKH từ mapping
                  String cskhName = userConfig.getPosName();
                  if (cskhName == null || cskhName.isBlank()) {
                    cskhName = userConfig.getLarkName();
                  }
                  foundCskhNameRef.set(cskhName);
//                  log.info("✅ Found customer in baseId: {}, tableId: {}, CSKH: {}", baseId, khachHangTableId, cskhName);
                  break; // Tìm thấy rồi, dừng thread này
                }
              }
            } catch (Exception e) {
              log.warn("Error searching customer in baseId {}: {}", baseId, e.getMessage());
            }
          }
        }, executorService);
        
        futures.add(future);
      }
      
      // Chờ tất cả các thread hoàn thành hoặc khi tìm thấy khách hàng
      try {
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        log.warn("Timeout waiting for customer search threads");
      } catch (Exception e) {
        log.error("Error waiting for customer search threads: {}", e.getMessage(), e);
      }
      
      // Lấy kết quả
      BitableRecord foundCustomer = foundCustomerRef.get();
      String foundBaseId = foundBaseIdRef.get();
      String foundLichHenTableId = foundLichHenTableIdRef.get();
      String foundTraoDoiTableId = foundTraoDoiTableIdRef.get();
      String foundCskhName = foundCskhNameRef.get();

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
//            log.info("✅ Loaded {} trao doi records", traoDoiList.size());
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
//            log.info("✅ Loaded {} lich hen records", lichHenList.size());
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
      customerData.put("benhNen", customerFields.get("Bệnh nền"));
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

    // Format "Bệnh nền" - có thể là list
    Object benhNen = fields.get("Bệnh nền");
    if (benhNen instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> list = (List<Object>) benhNen;
      fields.put("Bệnh nền", String.join(", ", 
          list.stream().map(Object::toString).toArray(String[]::new)));
    }
  }

  /**
   * Chuẩn hóa field text: nếu là list/map có {text, type} thì lấy ra chuỗi text,
   * tránh gửi structure phức tạp sang bảng đích.
   */
  private String extractPlainText(Object value) {
    if (value == null) return null;

    if (value instanceof String s) {
      return s;
    }

    if (value instanceof List<?> list) {
      StringBuilder sb = new StringBuilder();
      for (Object item : list) {
        String part = extractPlainText(item);
        if (part != null && !part.isBlank()) {
          if (!sb.isEmpty()) sb.append(", ");
          sb.append(part);
        }
      }
      return sb.toString();
    }

    if (value instanceof Map<?, ?> map) {
      Object text = map.get("text");
      if (text != null) return text.toString();
      Object name = map.get("name");
      if (name != null) return name.toString();
    }

    return value.toString();
  }

  /**
   * Chuẩn hóa field Link: bỏ key "type", chỉ giữ "link" và "text".
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> normalizeLinkField(Object linkValue) {
    if (linkValue == null) {
      return null;
    }

    if (linkValue instanceof Map<?, ?> rawMap) {
      Map<String, Object> map = new HashMap<>();
      Object link = rawMap.get("link");
      Object text = rawMap.get("text");
      if (link != null) map.put("link", link.toString());
      if (text != null) map.put("text", text.toString());
      return map;
    }

    // Nếu chỉ là string thì dùng cho cả link và text
    String s = linkValue.toString();
    Map<String, Object> map = new HashMap<>();
    map.put("link", s);
    map.put("text", s);
    return map;
  }

  // ================== Đồng bộ "Từ chối chăm" ==================

  /**
   * API nội bộ: quét tất cả CSKH, tìm khách hàng có "Tên Liệu Trình" chứa "Từ chối chăm"
   * và tạo bản ghi tương ứng trong bảng "Từ chối chăm" đích.
   *
   * - Dùng: gọi từ browser hoặc tool: /api/sync_tu_choi_cham
   * - Không có UI riêng, chỉ trả JSON.
   */
  @GetMapping("/api/sync_tu_choi_cham")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> syncTuChoiCham(HttpSession session) {
    Map<String, Object> result = new HashMap<>();

    if (!tokenService.hasToken(session)) {
      result.put("error", "Vui lòng đăng nhập trước");
      return ResponseEntity.ok(result);
    }

    try {
      tokenService.autoRefreshTokenIfNeeded(session);

      @SuppressWarnings("unchecked")
      List<UserConfigDto> userConfigs =
          (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);

      if (userConfigs == null || userConfigs.isEmpty()) {
        result.put("error",
            "Chưa có dữ liệu cấu hình. Vui lòng vào trang /config để load dữ liệu trước.");
        return ResponseEntity.ok(result);
      }

      int totalBases = 0;
      int totalFound = 0;
      int totalInserted = 0;
      int totalFailed = 0;

      List<String> insertedPhones = new ArrayList<>();

      for (UserConfigDto userConfig : userConfigs) {
        String baseId = userConfig.getBaseId();
        String khachHangTableId = userConfig.getKhachHangTableId();

        if (baseId == null || baseId.isBlank() || khachHangTableId == null
            || khachHangTableId.isBlank()) {
          continue;
        }

        totalBases++;
        List<BitableRecord> records = bitableService.searchRejectedCareCustomers(
            session, baseId, khachHangTableId, KHACH_HANG_VIEW_ID);

        if (records == null || records.isEmpty()) {
          continue;
        }

        totalFound += records.size();

        for (BitableRecord r : records) {
          Map<String, Object> srcFields = r.getFields();
          if (srcFields == null) continue;

          // Lấy số điện thoại để check trùng ở bảng "Từ chối chăm"
          Object rawPhone = srcFields.get("Điện thoại");
          String phoneStr = (rawPhone != null) ? rawPhone.toString().trim() : "";
          if (!phoneStr.isEmpty()) {
            log.info("Check 'Từ chối chăm' phone={} baseId={} tableId={}", phoneStr, baseId, khachHangTableId);
            boolean exists = bitableService.existsRejectedCareByPhone(session, phoneStr);
            if (exists) {
              continue;
            }
          }

          Map<String, Object> destFields = new HashMap<>();
          destFields.put("Mã KH", srcFields.get("Mã KH"));
          // Chuẩn hóa các field text: bỏ wrapper {text,type}, chỉ lấy string
          destFields.put("Tên khách hàng", extractPlainText(srcFields.get("Tên khách hàng")));
          destFields.put("Địa chỉ", extractPlainText(srcFields.get("Địa chỉ")));
          destFields.put("Tỉnh/Thành phố", srcFields.get("Tỉnh/Thành phố"));
          destFields.put("Điện thoại", srcFields.get("Điện thoại"));
          destFields.put("Tên Liệu Trình", srcFields.get("Tên Liệu Trình"));
          // Chuẩn hóa Link: bỏ field type, giữ link + text
          destFields.put("Link", normalizeLinkField(srcFields.get("Link")));
          destFields.put("Tuổi", srcFields.get("Tuổi"));
          destFields.put("Bệnh nền", srcFields.get("Bệnh nền"));

          Object ngayTao = srcFields.get("Ngày tạo");
          if (ngayTao == null) {
            ngayTao = System.currentTimeMillis();
          }
          destFields.put("Ngày tạo", ngayTao);

          // Ưu tiên giữ nguyên field "Người CSKH" từ bản ghi gốc (chứa id dạng ou_...)
          Object nguoiCskhField = srcFields.get("Người CSKH");
          if (nguoiCskhField != null) {
            destFields.put("Người CSKH", nguoiCskhField);
          }

          // Tạo bản ghi mới trong bảng "Từ chối chăm"
          try {
            bitableService.createRejectedCareRecord(session, destFields);
            totalInserted++;
            insertedPhones.add(phoneStr);
            log.info("Inserted 'Từ chối chăm' phone={}", phoneStr);
          } catch (Exception ex) {
            totalFailed++;
            log.error("❌ Failed to insert 'Từ chối chăm' record for phone {}: {}", phoneStr, ex.getMessage());
          }
        }
      }

      result.put("message", "Đã đồng bộ xong 'Từ chối chăm'");
      result.put("totalBases", totalBases);
      result.put("totalFound", totalFound);
      result.put("totalInserted", totalInserted);
      result.put("totalFailed", totalFailed);
      result.put("phones", insertedPhones);

    } catch (Exception e) {
      log.error("Error when syncing 'Từ chối chăm': {}", e.getMessage(), e);
      result.put("error", "Lỗi khi đồng bộ 'Từ chối chăm': " + e.getMessage());
    }

    return ResponseEntity.ok(result);
  }

  /**
   * Shortcut để chạy sync "Từ chối chăm" trực tiếp trên browser:
   * truy cập /updateTTC sẽ gọi lại logic /api/sync_tu_choi_cham và trả về JSON.
   */
  @GetMapping("/updateTTC")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> updateTuChoiCham(HttpSession session) {
    return syncTuChoiCham(session);
  }

  /**
   * API nội bộ: đồng bộ khách hàng có "Tên Liệu Trình" chứa "Đang chăm"
   * sang bảng đích tương ứng.
   *
   * Shortcut: /updateDangCham
   */
  @GetMapping("/api/sync_dang_cham")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> syncDangCham(HttpSession session) {
    Map<String, Object> result = new HashMap<>();

    if (!tokenService.hasToken(session)) {
      result.put("error", "Vui lòng đăng nhập trước");
      return ResponseEntity.ok(result);
    }

    try {
      tokenService.autoRefreshTokenIfNeeded(session);

      @SuppressWarnings("unchecked")
      List<UserConfigDto> userConfigs =
          (List<UserConfigDto>) session.getAttribute(SESSION_USER_CONFIGS);

      if (userConfigs == null || userConfigs.isEmpty()) {
        result.put("error",
            "Chưa có dữ liệu cấu hình. Vui lòng vào trang /config để load dữ liệu trước.");
        return ResponseEntity.ok(result);
      }

      int totalBases = 0;
      int totalFound = 0;
      int totalInserted = 0;
      int totalFailed = 0;

      List<String> insertedPhones = new ArrayList<>();

      for (UserConfigDto userConfig : userConfigs) {
        String baseId = userConfig.getBaseId();
        String khachHangTableId = userConfig.getKhachHangTableId();

        if (baseId == null || baseId.isBlank() || khachHangTableId == null
            || khachHangTableId.isBlank()) {
          continue;
        }

        totalBases++;
        List<BitableRecord> records = bitableService.searchDangChamCustomers(
            session, baseId, khachHangTableId, KHACH_HANG_VIEW_ID);

        if (records == null || records.isEmpty()) {
          continue;
        }

        totalFound += records.size();

        for (BitableRecord r : records) {
          Map<String, Object> srcFields = r.getFields();
          if (srcFields == null) continue;

          Object rawPhone = srcFields.get("Điện thoại");
          String phoneStr = (rawPhone != null) ? rawPhone.toString().trim() : "";
          if (!phoneStr.isEmpty()) {
            log.info("Check 'Đang chăm' phone={} baseId={} tableId={}", phoneStr, baseId, khachHangTableId);
            boolean exists = bitableService.existsDangChamByPhone(session, phoneStr);
            if (exists) {
              continue;
            }
          }

          Map<String, Object> destFields = new HashMap<>();
          destFields.put("Mã KH", srcFields.get("Mã KH"));
          destFields.put("Tên khách hàng", extractPlainText(srcFields.get("Tên khách hàng")));
          destFields.put("Địa chỉ", extractPlainText(srcFields.get("Địa chỉ")));
          destFields.put("Tỉnh/Thành phố", srcFields.get("Tỉnh/Thành phố"));
          destFields.put("Điện thoại", srcFields.get("Điện thoại"));
          destFields.put("Tên Liệu Trình", srcFields.get("Tên Liệu Trình"));
          destFields.put("Link", normalizeLinkField(srcFields.get("Link")));
          destFields.put("Tuổi", srcFields.get("Tuổi"));
          destFields.put("Bệnh nền", srcFields.get("Bệnh nền"));

          Object ngayTao = srcFields.get("Ngày tạo");
          if (ngayTao == null) {
            ngayTao = System.currentTimeMillis();
          }
          destFields.put("Ngày tạo", ngayTao);

          Object nguoiCskhField = srcFields.get("Người CSKH");
          if (nguoiCskhField != null) {
            destFields.put("Người CSKH", nguoiCskhField);
          }

          try {
            bitableService.createDangChamRecord(session, destFields);
            totalInserted++;
            insertedPhones.add(phoneStr);
            log.info("Inserted 'Đang chăm' phone={}", phoneStr);
          } catch (Exception ex) {
            totalFailed++;
            log.error("❌ Failed to insert 'Đang chăm' record for phone {}: {}", phoneStr, ex.getMessage());
          }
        }
      }

      result.put("message", "Đã đồng bộ xong 'Đang chăm'");
      result.put("totalBases", totalBases);
      result.put("totalFound", totalFound);
      result.put("totalInserted", totalInserted);
      result.put("totalFailed", totalFailed);
      result.put("phones", insertedPhones);

    } catch (Exception e) {
      log.error("Error when syncing 'Đang chăm': {}", e.getMessage(), e);
      result.put("error", "Lỗi khi đồng bộ 'Đang chăm': " + e.getMessage());
    }

    return ResponseEntity.ok(result);
  }

  @GetMapping("/updateDangCham")
  @ResponseBody
  public ResponseEntity<Map<String, Object>> updateDangCham(HttpSession session) {
    return syncDangCham(session);
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

