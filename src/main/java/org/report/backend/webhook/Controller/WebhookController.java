package org.report.backend.webhook.Controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.report.backend.webhook.DTO.WebhookPayload;
import org.report.backend.webhook.DTO.WebhookResponse;
import org.report.backend.webhook.Service.WebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    /**
     * Endpoint chính để nhận webhook từ POS
     * POS thường gửi POST request với JSON payload
     */
    @PostMapping("/pos")
    public ResponseEntity<WebhookResponse> handlePosWebhook(@RequestBody WebhookPayload payload) {
        try {
            log.info("Received POS webhook: {}", payload.getEventType());
            log.info("Webhook request body: {}", payload);

            // Xử lý webhook
            webhookService.processWebhook(payload);

            // Trả về response thành công
            WebhookResponse response = WebhookResponse.success(payload.getEventType());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing POS webhook", e);

            // Trả về response lỗi
            WebhookResponse response = WebhookResponse.error("Failed to process webhook: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Endpoint để test webhook với GET request
     * Có thể dùng để kiểm tra endpoint có hoạt động không
     */
    @GetMapping("/test")
    public ResponseEntity<WebhookResponse> testWebhook(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Long shopId) {

        String testEvent = eventType != null ? eventType : "test.event";
        log.info("Test webhook called with eventType: {}, shopId: {}", testEvent, shopId);

        WebhookResponse response = WebhookResponse.success(testEvent);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint để nhận webhook dạng form-data (nếu POS gửi theo format này)
     */
    @PostMapping(value = "/pos-form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<WebhookResponse> handlePosWebhookForm(@RequestParam("event_type") String eventType,
                                                               @RequestParam("shop_id") Long shopId,
                                                               @RequestParam(value = "data", required = false) String data) {
        try {
            log.info("Received POS webhook (form): eventType={}, shopId={}", eventType, shopId);
            log.info("Webhook form data: {}", data);

            // Tạo payload từ form data
            WebhookPayload payload = new WebhookPayload();
            payload.setEventType(eventType);
            payload.setShopId(shopId);
            payload.setTimestamp(System.currentTimeMillis());

            // Xử lý webhook
            webhookService.processWebhook(payload);

            WebhookResponse response = WebhookResponse.success(eventType);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing POS webhook form", e);
            WebhookResponse response = WebhookResponse.error("Failed to process webhook: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Endpoint để xem logs webhook gần đây (cho debugging)
     */
    @GetMapping("/logs")
    public ResponseEntity<String> getWebhookLogs() {
        // TODO: Implement webhook logging system
        return ResponseEntity.ok("Webhook logs feature not implemented yet");
    }
}
