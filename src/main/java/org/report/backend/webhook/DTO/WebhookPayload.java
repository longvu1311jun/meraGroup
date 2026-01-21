package org.report.backend.webhook.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.util.Map;

@Data
@ToString
public class WebhookPayload {
    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("shop_id")
    private Long shopId;

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("data")
    private Map<String, Object> data;

    // Các trường bổ sung có thể có trong webhook POS
    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("customer_id")
    private String customerId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("source")
    private String source;
}
