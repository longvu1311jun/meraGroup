package org.report.backend.searchCustomer.DTO;

import java.time.LocalDateTime;
import java.util.List;

public class customer {
  private String customerId;
  private String name;
  private String phone;
  private String facebookLink;
  private LocalDateTime createdAt;

  private List<address> addresses;
}
