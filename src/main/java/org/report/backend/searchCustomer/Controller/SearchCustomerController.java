package org.report.backend.searchCustomer.Controller;

import org.report.backend.searchCustomer.DTO.Customer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api")
public class SearchCustomerController {
  @Value("${pos.base-url}")
  private String baseUrl;
  @Value("${pos.api-key}")
  private String apiKey;
  private RestTemplate  restTemplate = new RestTemplate();
  @GetMapping("/search")
  public String searchKH(@RequestParam String phone) {
    String url = baseUrl +"/orders?phone="+phone;
    System.out.println("SDT: "+phone);
    String url1 = UriComponentsBuilder.fromHttpUrl(url)
        .queryParam("phone", phone)
        .queryParam("api_key", apiKey)
        .toUriString();

try {
  System.out.println(url1);
  ResponseEntity<String> response = restTemplate.exchange(
      url1,
      HttpMethod.GET,
      new HttpEntity<String>("null"),
      String.class);
  return response.getBody();
}catch (Exception e) {
  return "Lỗi khi gọi API: "+e.getMessage();
}
}
}
