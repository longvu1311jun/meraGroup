package org.report.backend.searchCustomer.DTO;

import java.math.BigDecimal;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CustomerSummary {
  private int totalOrders;
  private int deliveredOrders;
  private int returnedOrders;

  private BigDecimal totalSpent;
  private BigDecimal totalCOD;
  private BigDecimal reconciledCOD;

}
