package org.report.backend.searchCustomer.DTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Order {
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

  private List<OrderItem> items;
  private Shipping shipping;
}
