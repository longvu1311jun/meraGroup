package org.report.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TestApiCall {
    @Test
    public void testApiCall() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper objectMapper = new ObjectMapper();

        // API customer search
        String customerUrl = "https://pos.pages.fm/api/v1/shops/1546758/customers?page_size=30&page_number=1&search=0982844337&api_key=2a6ed8b51a8d4ae49a851d5876b00018";

        System.out.println("=== CUSTOMER API RESPONSE ===");
        String customerResponse = restTemplate.getForObject(customerUrl, String.class);
        JsonNode customerRoot = objectMapper.readTree(customerResponse);

        if (customerRoot.has("data") && customerRoot.get("data").isArray() && customerRoot.get("data").size() > 0) {
            JsonNode customer = customerRoot.get("data").get(0);

            System.out.println("=== CUSTOMER INFO ===");
            System.out.println("ID: " + customer.get("id").asText());
            System.out.println("Name: " + customer.get("name").asText());
            System.out.println("Phone: " + (customer.has("phone_numbers") && customer.get("phone_numbers").isArray() ?
                customer.get("phone_numbers").get(0).asText() : "N/A"));

            // Notes
            if (customer.has("notes") && customer.get("notes").isArray()) {
                System.out.println("\n=== NOTES (với ngày tạo) ===");
                JsonNode notes = customer.get("notes");
                for (int i = 0; i < Math.min(notes.size(), 5); i++) { // Chỉ hiện 5 notes đầu
                    JsonNode note = notes.get(i);
                    String orderId = note.get("order_id").asText();
                    String message = note.get("message").asText();
                    long createdAt = note.get("created_at").asLong();

                    // Convert timestamp to readable date
                    LocalDateTime dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(createdAt), ZoneId.systemDefault());
                    String formattedDate = dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));

                    System.out.println("Order ID: " + orderId);
                    System.out.println("Message: " + message);
                    System.out.println("Created At: " + formattedDate + " (timestamp: " + createdAt + ")");
                    System.out.println("---");
                }
                System.out.println("Total notes: " + notes.size());
            }
        }
    }
}
