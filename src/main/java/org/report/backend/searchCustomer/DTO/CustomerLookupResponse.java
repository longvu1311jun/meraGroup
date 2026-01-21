package org.report.backend.searchCustomer.DTO;

import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CustomerLookupResponse {
  private Customer customer;
  private CustomerSummary summary;
  private List<Order> orders;
  private List<CustomerNote> notes;

  // Phân trang (từ API customers)
  private int pageNumber;
  private int pageSize;
  private boolean success;
  private int totalEntries;
  private int totalPages;
}
