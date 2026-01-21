package org.report.backend.searchCustomer.DTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class Customer {
  private String customerId;
  private String name;
  private String phone;
  private String facebookLink;
  private LocalDateTime createdAt;

  // Thông tin bổ sung từ API customers
  private String gender;
  private String email;
  private LocalDateTime dateOfBirth;
  private int rewardPoint;
  private BigDecimal currentDebts;
  private String referralCode;
  private int countReferrals;

  private List<Address> addresses;
}
