package org.report.backend.searchCustomer.Controller;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.report.backend.searchCustomer.DTO.CustomerLookupResponse;
import org.report.backend.searchCustomer.Service.SearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchInfoController {

  private static final Logger log = LoggerFactory.getLogger(SearchInfoController.class);
  private final SearchService searchService;

  /**
   * API SearchInfo: truyền vào số điện thoại, trả về thông tin khách hàng từ POS.
   */
  @GetMapping("/search-info")
  public ResponseEntity<?> searchInfo(@RequestParam("phone") String phone) {
    try {
      CustomerLookupResponse result = searchService.lookupCustomer(phone);
      if (result == null || result.getCustomer() == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("message", "Không tìm thấy khách hàng với số điện thoại đã nhập"));
      }
      return ResponseEntity.ok(result);
    } catch (IllegalArgumentException ex) {
      return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    } catch (Exception ex) {
      log.error("Lỗi SearchInfo", ex);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Không thể tra cứu khách hàng: " + ex.getMessage()));
    }
  }
}
