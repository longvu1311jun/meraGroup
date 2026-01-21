package org.report.backend.searchCustomer.DTO;

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
public class Shipping {
  private String partnerName;
  private String trackingCode;
  private String partnerStatus;
  private String deliveryName;
  private String deliveryPhone;

}
