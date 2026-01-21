package org.report.backend.searchCustomer.DTO;

import java.math.BigDecimal;
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
public class OrderItem {
  private String productId;
  private String productName;
  private int quantity;
  private BigDecimal price;
}
