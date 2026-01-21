package org.report.backend.searchCustomer.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.report.backend.searchCustomer.DTO.Address;
import org.report.backend.searchCustomer.DTO.Customer;
import org.report.backend.searchCustomer.DTO.CustomerLookupResponse;
import org.report.backend.searchCustomer.DTO.CustomerNote;
import org.report.backend.searchCustomer.DTO.CustomerNoteCreator;
import org.report.backend.searchCustomer.DTO.CustomerNoteEdit;
import org.report.backend.searchCustomer.DTO.CustomerSummary;
import org.report.backend.searchCustomer.DTO.Message;
import org.report.backend.searchCustomer.DTO.Order;
import org.report.backend.searchCustomer.DTO.OrderItem;
import org.report.backend.searchCustomer.DTO.Shipping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class SearchService {

  private static final Logger log = LoggerFactory.getLogger(SearchService.class);
  private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

  @Value("${pos.base-url}")
  private String baseUrl;

  @Value("${pos.api-key}")
  private String apiKey;

  private final RestTemplateBuilder restTemplateBuilder;
  private final ObjectMapper objectMapper;

  public CustomerLookupResponse lookupCustomer(String phone) throws Exception {
    String sanitizedPhone = sanitizePhone(phone);
    if (sanitizedPhone.isBlank()) {
      throw new IllegalArgumentException("Số điện thoại không hợp lệ");
    }

    String url =
        UriComponentsBuilder.fromHttpUrl(baseUrl + "/customers")
            .queryParam("search", sanitizedPhone)
            .queryParam("api_key", apiKey)
            .toUriString();

    RestTemplate restTemplate =
        restTemplateBuilder
            .setConnectTimeout(java.time.Duration.ofSeconds(10))
            .setReadTimeout(java.time.Duration.ofSeconds(20))
            .build();

    try {
      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
      JsonNode root = objectMapper.readTree(response.getBody());
      return mapResponse(root);
    } catch (RestClientResponseException ex) {
      log.error("Call SearchInfo failed: status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
      throw new RuntimeException("Không thể gọi POS API: " + ex.getStatusText());
    } catch (Exception ex) {
      log.error("Unexpected error when calling POS API", ex);
      throw ex;
    }
  }

  private CustomerLookupResponse mapResponse(JsonNode root) {
    if (root == null || root.isMissingNode()) {
      return null;
    }

    CustomerLookupResponse response = new CustomerLookupResponse();

    JsonNode dataNode = root.path("data");
    if (!dataNode.isArray() || dataNode.isEmpty()) {
      return response;
    }

    // Lấy thông tin khách hàng từ API customers
    JsonNode customerNode = dataNode.get(0);
    response.setCustomer(parseCustomer(customerNode));

    // Tổng quan khách hàng
    response.setSummary(parseCustomerSummary(customerNode));

    // Danh sách đơn hàng - API customers không trả về orders, set empty list
    response.setOrders(new ArrayList<>());

    // Ghi chú
    response.setNotes(parseCustomerNotes(customerNode.path("notes")));

    // Thông tin phân trang
    response.setPageNumber(root.path("page_number").asInt(1));
    response.setPageSize(root.path("page_size").asInt(30));
    response.setSuccess(root.path("success").asBoolean(true));
    response.setTotalEntries(root.path("total_entries").asInt(0));
    response.setTotalPages(root.path("total_pages").asInt(1));

    return response;
  }

  private Customer parseCustomer(JsonNode node) {
    if (node == null || node.isMissingNode()) {
      return null;
    }

    Customer customer = new Customer();
    customer.setCustomerId(asText(node, "id"));
    customer.setName(asText(node, "name"));
    customer.setFacebookLink(asText(node, "fb_id"));
    customer.setPhone(extractPhone(node.path("phone_numbers")));
    customer.setCreatedAt(parseDateTime(asText(node, "inserted_at")));

    // Thông tin bổ sung từ API customers
    customer.setGender(asText(node, "gender"));
    customer.setEmail(asText(node, "email"));
    customer.setDateOfBirth(parseDateTime(asText(node, "date_of_birth")));
    customer.setRewardPoint(node.path("reward_point").asInt(0));
    customer.setCurrentDebts(asBigDecimal(node.path("current_debts")));
    customer.setReferralCode(asText(node, "referral_code"));
    customer.setCountReferrals(node.path("count_referrals").asInt(0));

    List<Address> addresses = new ArrayList<>();
    JsonNode addressNode = node.path("shop_customer_addresses");
    if (addressNode.isArray()) {
      for (JsonNode a : addressNode) {
        Address address = new Address();
        address.setFullName(asText(a, "full_name"));
        address.setPhone(asText(a, "phone_number"));
        address.setFullAddress(asText(a, "full_address"));
        address.setCommuneId(asText(a, "commune_id"));
        address.setDistrictId(asText(a, "district_id"));
        address.setProvinceId(asText(a, "province_id"));
        addresses.add(address);
      }
    }

    if (!addresses.isEmpty()) {
      customer.setAddresses(addresses);
    }

    return customer;
  }

  private CustomerSummary parseCustomerSummary(JsonNode customerNode) {
    CustomerSummary summary = new CustomerSummary();

    summary.setTotalOrders(customerNode.path("order_count").asInt(0));
    summary.setDeliveredOrders(customerNode.path("succeed_order_count").asInt(0));
    summary.setReturnedOrders(customerNode.path("returned_order_count").asInt(0));
    summary.setTotalSpent(asBigDecimal(customerNode.path("purchased_amount")));

    // API customers không có thông tin COD, set về 0
    summary.setTotalCOD(BigDecimal.ZERO);
    summary.setReconciledCOD(BigDecimal.ZERO);

    return summary;
  }

  private List<Order> parseOrders(JsonNode dataNode) {
    List<Order> orders = new ArrayList<>();
    if (!dataNode.isArray()) {
      return orders;
    }

    for (JsonNode orderNode : dataNode) {
      Order order = new Order();
      order.setOrderId(orderNode.path("id").asLong(0));
      order.setCreatedAt(parseDateTime(asText(orderNode, "inserted_at")));
      order.setUpdatedAt(parseDateTime(asText(orderNode, "updated_at")));
      order.setStatus(orderNode.path("status").asInt(0));
      order.setStatusText(asText(orderNode, "status_name"));

      order.setTotalAmount(asBigDecimal(orderNode.path("total_price")));
      order.setDiscount(asBigDecimal(orderNode.path("total_discount")));
      order.setTax(asBigDecimal(orderNode.path("tax")));
      order.setShippingFee(asBigDecimal(orderNode.path("shipping_fee")));
      order.setMoneyToCollect(asBigDecimal(orderNode.path("money_to_collect")));

      order.setItems(parseItems(orderNode.path("items")));
      order.setShipping(parseShipping(orderNode.path("shipping_address"), asText(orderNode, "partner_status")));

      orders.add(order);
    }
    return orders;
  }

  private List<OrderItem> parseItems(JsonNode itemsNode) {
    List<OrderItem> items = new ArrayList<>();
    if (!itemsNode.isArray()) {
      return items;
    }

    for (JsonNode itemNode : itemsNode) {
      OrderItem item = new OrderItem();
      item.setProductId(asText(itemNode, "product_id"));
      JsonNode variationInfo = itemNode.path("variation_info");
      item.setProductName(asText(variationInfo, "name"));

      item.setQuantity(itemNode.path("quantity").asInt(0));
      // Giá ưu tiên lấy ở variation_info.retail_price, fallback price
      BigDecimal price =
          asBigDecimal(variationInfo.path("retail_price"), asBigDecimal(itemNode.path("price")));
      item.setPrice(price);
      items.add(item);
    }
    return items;
  }

  private Shipping parseShipping(JsonNode shippingNode, String partnerStatus) {
    if (shippingNode == null || shippingNode.isMissingNode()) {
      return null;
    }
    Shipping shipping = new Shipping();
    shipping.setPartnerStatus(partnerStatus);
    shipping.setDeliveryName(asText(shippingNode, "full_name"));
    shipping.setDeliveryPhone(asText(shippingNode, "phone_number"));
    shipping.setTrackingCode(asText(shippingNode, "tracking_code"));
    shipping.setPartnerName(asText(shippingNode, "partner_name"));
    return shipping;
  }

  private List<Message> parseNotes(JsonNode notesNode) {
    List<Message> messages = new ArrayList<>();
    if (!notesNode.isArray()) {
      return messages;
    }

    for (JsonNode noteNode : notesNode) {
      Message msg = new Message();
      msg.setMessage(asText(noteNode, "message"));
      msg.setOrderId(parseLongSafe(asText(noteNode, "order_id")));
      msg.setCreatedAt(parseDateTime(asText(noteNode, "created_at")));

      JsonNode createdBy = noteNode.path("created_by");
      if (createdBy.isObject()) {
        msg.setCreatedBy(asText(createdBy, "fb_name"));
      }
      messages.add(msg);
    }
    return messages;
  }

  private String sanitizePhone(String phone) {
    if (phone == null) {
      return "";
    }
    return phone.replaceAll("\\D", "").trim();
  }

  private String asText(JsonNode node, String field) {
    if (node == null || node.isMissingNode()) {
      return null;
    }
    JsonNode target = node.get(field);
    return target == null || target.isNull() ? null : target.asText();
  }

  private BigDecimal asBigDecimal(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return BigDecimal.ZERO;
    }
    try {
      if (node.isNumber()) {
        return node.decimalValue();
      }
      String text = node.asText();
      if (text == null || text.isBlank()) {
        return BigDecimal.ZERO;
      }
      return new BigDecimal(text);
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private BigDecimal asBigDecimal(JsonNode primary, BigDecimal fallback) {
    BigDecimal result = asBigDecimal(primary);
    return result.compareTo(BigDecimal.ZERO) != 0 ? result : fallback;
  }

  private LocalDateTime parseDateTime(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    // Thử parse ISO
    try {
      return LocalDateTime.parse(value, ISO_FORMATTER);
    } catch (DateTimeParseException ignored) {
      // fallback
    }

    try {
      long epoch = Long.parseLong(value);
      return LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneId.systemDefault());
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String extractPhone(JsonNode phonesNode) {
    if (!phonesNode.isArray() || phonesNode.isEmpty()) {
      return null;
    }
    return phonesNode.get(0).asText();
  }

  private Long parseLongSafe(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private List<CustomerNote> parseCustomerNotes(JsonNode notesNode) {
    List<CustomerNote> notes = new ArrayList<>();
    if (!notesNode.isArray()) {
      return notes;
    }

    for (JsonNode noteNode : notesNode) {
      CustomerNote note = new CustomerNote();
      note.setId(asText(noteNode, "id"));
      note.setMessage(asText(noteNode, "message"));
      note.setOrderId(asText(noteNode, "order_id"));
      note.setCreatedAt(parseDateTime(asText(noteNode, "created_at")));
      note.setUpdatedAt(parseDateTime(asText(noteNode, "updated_at")));
      note.setRemovedAt(parseDateTime(asText(noteNode, "removed_at")));

      // Parse created_by
      JsonNode createdByNode = noteNode.path("created_by");
      if (createdByNode.isObject()) {
        CustomerNoteCreator creator = new CustomerNoteCreator();
        creator.setUid(asText(createdByNode, "uid"));
        creator.setFbId(asText(createdByNode, "fb_id"));
        creator.setFbName(asText(createdByNode, "fb_name"));
        creator.setSessionId(asText(createdByNode, "session_id"));
        creator.setTokenForBusiness(asText(createdByNode, "token_for_business"));
        creator.setApplication(createdByNode.path("application").asInt(0));
        note.setCreatedBy(creator);
      }

      // Parse edit_history
      List<CustomerNoteEdit> editHistory = new ArrayList<>();
      JsonNode editHistoryNode = noteNode.path("edit_history");
      if (editHistoryNode.isArray()) {
        for (JsonNode editNode : editHistoryNode) {
          CustomerNoteEdit edit = new CustomerNoteEdit();
          edit.setCreatedAt(parseDateTime(asText(editNode, "created_at")));
          edit.setMessage(asText(editNode, "message"));

          // Parse created_by for edit
          JsonNode editCreatedByNode = editNode.path("created_by");
          if (editCreatedByNode.isObject()) {
            CustomerNoteCreator editCreator = new CustomerNoteCreator();
            editCreator.setUid(asText(editCreatedByNode, "uid"));
            editCreator.setFbId(asText(editCreatedByNode, "fb_id"));
            editCreator.setFbName(asText(editCreatedByNode, "fb_name"));
            editCreator.setSessionId(asText(editCreatedByNode, "session_id"));
            editCreator.setTokenForBusiness(asText(editCreatedByNode, "token_for_business"));
            editCreator.setApplication(editCreatedByNode.path("application").asInt(0));
            edit.setCreatedBy(editCreator);
          }

          // Parse images for edit
          List<String> editImages = new ArrayList<>();
          JsonNode editImagesNode = editNode.path("images");
          if (editImagesNode.isArray()) {
            for (JsonNode imgNode : editImagesNode) {
              editImages.add(imgNode.asText());
            }
          }
          edit.setImages(editImages);

          editHistory.add(edit);
        }
      }
      note.setEditHistory(editHistory);

      // Parse images
      List<String> images = new ArrayList<>();
      JsonNode imagesNode = noteNode.path("images");
      if (imagesNode.isArray()) {
        for (JsonNode imgNode : imagesNode) {
          images.add(imgNode.asText());
        }
      }
      note.setImages(images);

      // Parse links
      List<String> links = new ArrayList<>();
      JsonNode linksNode = noteNode.path("links");
      if (linksNode.isArray()) {
        for (JsonNode linkNode : linksNode) {
          links.add(linkNode.asText());
        }
      }
      note.setLinks(links);

      notes.add(note);
    }
    return notes;
  }
}
