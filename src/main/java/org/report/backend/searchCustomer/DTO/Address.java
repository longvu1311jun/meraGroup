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
public class Address {
  private String fullName;
  private String phone;
  private String fullAddress;
  private String communeId;
  private String districtId;
  private String provinceId;
}
