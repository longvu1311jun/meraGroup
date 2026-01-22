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
public class CustomerInfo {
    private String customerId;
    private String name;
    private String phone;
    private String fullAddress;
    private int succeedOrderCount;
    private List<CustomerNoteInfo> notes;
}
