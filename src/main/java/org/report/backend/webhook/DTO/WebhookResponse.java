package org.report.backend.webhook.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class WebhookResponse {
    private boolean success;
    private String message;
    private String eventType;
    private Long timestamp;

    public static WebhookResponse success(String eventType) {
        return new WebhookResponse(true, "Webhook processed successfully", eventType, System.currentTimeMillis());
    }

    public static WebhookResponse error(String message) {
        return new WebhookResponse(false, message, null, System.currentTimeMillis());
    }
}
