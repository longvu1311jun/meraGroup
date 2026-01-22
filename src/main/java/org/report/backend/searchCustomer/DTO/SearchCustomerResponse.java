package org.report.backend.searchCustomer.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SearchCustomerResponse {
    private CustomerInfo customer;
    private List<OrderInfo> orders;
}
