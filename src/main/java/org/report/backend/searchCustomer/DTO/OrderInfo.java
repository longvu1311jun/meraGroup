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
public class OrderInfo {
    private String billPhoneNumber;
    private List<OrderItemInfo> items;
    private String systemId;
    private int status;
    private String timeAssignSeller;
    private String assigningSellerName;
    private String orderId;
    private String orderLink;
    private String orderSourcesName;
    private String assigningCareName;
}
