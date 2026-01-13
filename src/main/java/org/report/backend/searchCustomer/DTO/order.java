package org.report.backend.searchCustomer.DTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class order {
  private Long orderId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  private int status;
  private String statusText;

  private BigDecimal totalAmount;
  private BigDecimal discount;
  private BigDecimal tax;
  private BigDecimal shippingFee;
  private BigDecimal moneyToCollect;

  private List<orderItem> items;
  private shipping shipping;
}
