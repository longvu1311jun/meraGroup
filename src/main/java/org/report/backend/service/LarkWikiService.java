package org.report.backend.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpSession;
import org.report.backend.model.LarkNode;
import org.report.backend.model.PosUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LarkWikiService {
  
  private static final Logger log = LoggerFactory.getLogger(LarkWikiService.class);
  private static final String LARK_SPACE_ID = "7553087350184673311";
  
  private final RestTemplate restTemplate;
  private final LarkTokenService tokenService;
  
  public LarkWikiService(LarkTokenService tokenService) {
    this.restTemplate = new RestTemplate();
    this.tokenService = tokenService;
  }
  
  /**
   * Get all nodes (bases) from space
   */
  public List<LarkNode> getAllNodes(HttpSession session) throws Exception {
    String accessToken = tokenService.getAccessToken(session);
    String url = String.format(
        "https://open.larksuite.com/open-apis/wiki/v2/spaces/%s/nodes",
        LARK_SPACE_ID
    );
    
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setBearerAuth(accessToken);
    
    HttpEntity<String> entity = new HttpEntity<>(headers);
    
    try {
      ResponseEntity<LarkNodesResponse> response = restTemplate.exchange(
          url,
          HttpMethod.GET,
          entity,
          LarkNodesResponse.class
      );
      
      // Log response để debug
      log.info("=== API Response - Get All Nodes (Bases) ===");
      log.info("URL: {}", url);
      log.info("HTTP Status: {}", response.getStatusCode());
      
      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        LarkNodesResponse body = response.getBody();
        System.out.println("Full Response Body: " + body);
        log.info("Response Code: {}", body.getCode());
        log.info("Response Message: {}", body.getMsg());
        
        if (body.getCode() == 0 && body.getData() != null) {
          List<LarkNode> nodes = body.getData().getItems();
          log.info("Total number of nodes (bases): {}", nodes.size());
          
          // Log từng node với Base ID (obj_token)
          for (int i = 0; i < nodes.size(); i++) {
            LarkNode node = nodes.get(i);
            log.info("Base [{}]: Title='{}', BaseID(obj_token)='{}', NodeToken='{}', ParentNodeToken='{}'", 
                i + 1, node.getTitle(), node.getObjToken(), node.getNodeToken(), node.getParentNodeToken());
          }
          
          log.info("=== End API Response ===");
          return nodes;
        } else {
          log.warn("API returned error code: {}, message: {}", body.getCode(), body.getMsg());
        }
      }
      log.warn("Failed to get all nodes: HTTP {}", response.getStatusCode());
      return Collections.emptyList();
    } catch (RestClientException e) {
      log.error("Error calling Lark Wiki API: {}", e.getMessage(), e);
      return Collections.emptyList();
    }
  }
  
  /**
   * Get parent node token from space
   */
  private String getParentNodeToken(HttpSession session) throws Exception {
    String accessToken = tokenService.getAccessToken(session);
    String url = String.format(
        "https://open.larksuite.com/open-apis/wiki/v2/spaces/%s/nodes",
        LARK_SPACE_ID
    );
    
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setBearerAuth(accessToken);
    
    HttpEntity<String> entity = new HttpEntity<>(headers);
    
    try {
      ResponseEntity<LarkNodesResponse> response = restTemplate.exchange(
          url,
          HttpMethod.GET,
          entity,
          LarkNodesResponse.class
      );
      
      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        LarkNodesResponse body = response.getBody();
        System.out.println(body);
        log.info("=== API Response - Get Parent Node Token ===");
        log.info("URL: {}", url);
        log.info("Response Code: {}", body.getCode());
        log.info("Response Message: {}", body.getMsg());
        
        if (body.getCode() == 0 && body.getData() != null && !body.getData().getItems().isEmpty()) {
          // Get first node's parent_node_token
          LarkNode firstNode = body.getData().getItems().get(0);
          String parentToken = firstNode.getParentNodeToken();
          log.info("Parent Node Token: {}", parentToken);
          log.info("=== End API Response ===");
          return parentToken;
        }
      }
      throw new RuntimeException("Failed to get parent node token");
    } catch (RestClientException e) {
      log.error("Error calling Lark Wiki API: {}", e.getMessage(), e);
      throw new RuntimeException("Error getting parent node token: " + e.getMessage(), e);
    }
  }
  
  /**
   * Get child nodes of a specific node using its node_token as parent_node_token
   */
  public List<LarkNode> getChildNodesByNodeToken(String nodeToken, HttpSession session) throws Exception {
    if (nodeToken == null || nodeToken.isEmpty()) {
      log.warn("Node token is null or empty");
      return Collections.emptyList();
    }
    
    String accessToken = tokenService.getAccessToken(session);
    String url = String.format(
        "https://open.larksuite.com/open-apis/wiki/v2/spaces/%s/nodes?parent_node_token=%s",
        LARK_SPACE_ID,
        nodeToken
    );
    
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setBearerAuth(accessToken);
    
    HttpEntity<String> entity = new HttpEntity<>(headers);
    
    try {
      ResponseEntity<LarkNodesResponse> response = restTemplate.exchange(
          url,
          HttpMethod.GET,
          entity,
          LarkNodesResponse.class
      );
      
      // Log response để debug
      log.info("=== API Response - Get Child Nodes by Node Token ===");
      log.info("URL: {}", url);
      log.info("Parent Node Token: {}", nodeToken);
      log.info("HTTP Status: {}", response.getStatusCode());
      
      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        LarkNodesResponse body = response.getBody();
        log.info("Response Code: {}", body.getCode());
        log.info("Response Message: {}", body.getMsg());
        
        if (body.getCode() == 0 && body.getData() != null) {
          List<LarkNode> nodes = body.getData().getItems();
          log.info("Number of child nodes: {}", nodes.size());
          
          // Log từng child node
          for (int i = 0; i < nodes.size(); i++) {
            LarkNode node = nodes.get(i);
            log.info("Child Node [{}]: Title='{}', BaseID(obj_token)='{}', NodeToken='{}'", 
                i + 1, node.getTitle(), node.getObjToken(), node.getNodeToken());
          }
          
          log.info("=== End API Response ===");
          return nodes;
        } else {
          log.warn("API returned error code: {}, message: {}", body.getCode(), body.getMsg());
        }
      }
      log.warn("Failed to get child nodes: HTTP {}", response.getStatusCode());
      return Collections.emptyList();
    } catch (RestClientException e) {
      log.error("Error calling Lark Wiki API: {}", e.getMessage(), e);
      return Collections.emptyList();
    }
  }
  
  /**
   * Get all nodes and their child nodes
   */
  public List<LarkNode> getAllNodesWithChildren(HttpSession session) throws Exception {
    List<LarkNode> allNodes = getAllNodes(session);
    
    // For each node, get its child nodes
    for (LarkNode node : allNodes) {
      if (node.getNodeToken() != null && !node.getNodeToken().isEmpty()) {
        try {
          List<LarkNode> childNodes = getChildNodesByNodeToken(node.getNodeToken(), session);
          node.setChildNodes(childNodes);
        } catch (Exception e) {
          log.warn("Failed to get child nodes for node {}: {}", node.getNodeToken(), e.getMessage());
          node.setChildNodes(Collections.emptyList());
        }
      }
    }
    
    return allNodes;
  }
  
  /**
   * Get child nodes using parent node token (old method, kept for backward compatibility)
   */
  public List<LarkNode> getChildNodes(HttpSession session) throws Exception {
    String parentNodeToken = getParentNodeToken(session);
    if (parentNodeToken == null || parentNodeToken.isEmpty()) {
      log.warn("Parent node token is null or empty");
      return Collections.emptyList();
    }
    
    String accessToken = tokenService.getAccessToken(session);
    String url = String.format(
        "https://open.larksuite.com/open-apis/wiki/v2/spaces/%s/nodes?parent_node_token=%s",
        LARK_SPACE_ID,
        parentNodeToken
    );
    
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setBearerAuth(accessToken);
    
    HttpEntity<String> entity = new HttpEntity<>(headers);
    
    try {
      ResponseEntity<LarkNodesResponse> response = restTemplate.exchange(
          url,
          HttpMethod.GET,
          entity,
          LarkNodesResponse.class
      );
      
      // Log response để debug
      log.info("=== API Response - Get Child Nodes (Base IDs) ===");
      log.info("URL: {}", url);
      log.info("HTTP Status: {}", response.getStatusCode());
      
      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        LarkNodesResponse body = response.getBody();
        log.info("Response Code: {}", body.getCode());
        log.info("Response Message: {}", body.getMsg());
        
        if (body.getCode() == 0 && body.getData() != null) {
          List<LarkNode> nodes = body.getData().getItems();
          log.info("Number of nodes: {}", nodes.size());
          
          // Log từng node với Base ID (obj_token)
          for (int i = 0; i < nodes.size(); i++) {
            LarkNode node = nodes.get(i);
            log.info("Node [{}]: Title='{}', BaseID(obj_token)='{}', NodeToken='{}'", 
                i + 1, node.getTitle(), node.getObjToken(), node.getNodeToken());
          }
          
          log.info("=== End API Response ===");
          return nodes;
        } else {
          log.warn("API returned error code: {}, message: {}", body.getCode(), body.getMsg());
        }
      }
      log.warn("Failed to get child nodes: HTTP {}", response.getStatusCode());
      return Collections.emptyList();
    } catch (RestClientException e) {
      log.error("Error calling Lark Wiki API: {}", e.getMessage(), e);
      return Collections.emptyList();
    }
  }
  
  /**
   * Extract phone number from a string (supports Vietnamese phone formats)
   */
  private String extractPhoneNumber(String text) {
    if (text == null || text.isEmpty()) {
      return null;
    }
    
    // Pattern to match Vietnamese phone numbers (10-11 digits, may have spaces, dashes, or dots)
    // Examples: 0123456789, 0912345678, 0987.654.321, 0901-234-567
    Pattern pattern = Pattern.compile("(?:0|\\+84)(?:3|5|7|8|9)[0-9]{8,9}");
    Matcher matcher = pattern.matcher(text.replaceAll("[\\s\\.\\-]", ""));
    
    if (matcher.find()) {
      String phone = matcher.group();
      // Normalize: remove +84, replace with 0
      if (phone.startsWith("+84")) {
        phone = "0" + phone.substring(3);
      }
      // Remove all non-digit characters for comparison
      return phone.replaceAll("[^0-9]", "");
    }
    
    return null;
  }
  
  /**
   * Get phone number from POS user (from phone_number field or extract from name)
   */
  private String getPosUserPhone(PosUser posUser) {
    // First try phone_number field
    if (posUser.getUser() != null && posUser.getUser().getPhoneNumber() != null) {
      String phone = extractPhoneNumber(posUser.getUser().getPhoneNumber());
      if (phone != null) {
        return phone;
      }
    }
    
    // If not found, try to extract from name
    String name = posUser.getName();
    if (name != null) {
      return extractPhoneNumber(name);
    }
    
    return null;
  }
  
  /**
   * Get all nodes including child nodes for matching
   */
  private List<LarkNode> getAllNodesForMatching(HttpSession session) throws Exception {
    List<LarkNode> allNodes = getAllNodes(session);
    List<LarkNode> allNodesWithChildren = new java.util.ArrayList<>(allNodes);
    
    // Add all child nodes to the list for matching
    for (LarkNode node : allNodes) {
      if (node.getNodeToken() != null && !node.getNodeToken().isEmpty()) {
        try {
          List<LarkNode> childNodes = getChildNodesByNodeToken(node.getNodeToken(), session);
          allNodesWithChildren.addAll(childNodes);
        } catch (Exception e) {
          log.warn("Failed to get child nodes for node {}: {}", node.getNodeToken(), e.getMessage());
        }
      }
    }
    
    return allNodesWithChildren;
  }
  
  /**
   * Match POS users with Lark nodes by phone number (SDT)
   * Returns a map: PosUser -> LarkNode (matched by phone number)
   */
  public Map<PosUser, LarkNode> matchUsersWithNodes(List<PosUser> posUsers, HttpSession session) {
    Map<PosUser, LarkNode> matchedMap = new HashMap<>();
    
    try {
      // Get all nodes including child nodes
      List<LarkNode> allLarkNodes = getAllNodesForMatching(session);
      
      log.info("=== Matching POS Users with Lark Nodes by Phone Number ===");
      log.info("Total POS users: {}", posUsers.size());
      log.info("Total Lark nodes (including children): {}", allLarkNodes.size());
      
      for (PosUser posUser : posUsers) {
        String posUserPhone = getPosUserPhone(posUser);
        String posUserName = posUser.getName();
        
        log.info("POS User: {} - Phone: {}", posUserName, posUserPhone);
        
        if (posUserPhone != null && !posUserPhone.isEmpty()) {
          // Find matching node by phone number in title
          LarkNode matchedNode = allLarkNodes.stream()
              .filter(node -> {
                if (node.getTitle() == null) {
                  return false;
                }
                String nodePhone = extractPhoneNumber(node.getTitle());
                boolean match = posUserPhone.equals(nodePhone);
                if (match) {
                  log.info("  ✓ Matched with Lark Node: {} - Phone: {}", node.getTitle(), nodePhone);
                }
                return match;
              })
              .findFirst()
              .orElse(null);
          
          if (matchedNode != null) {
            matchedMap.put(posUser, matchedNode);
          } else {
            log.info("  ✗ No match found for phone: {}", posUserPhone);
          }
        } else {
          log.warn("  ⚠ No phone number found for POS user: {}", posUserName);
        }
      }
      
      log.info("Total matches: {}", matchedMap.size());
      log.info("=== End Matching ===");
    } catch (Exception e) {
      log.error("Error matching users with nodes: {}", e.getMessage(), e);
    }
    
    return matchedMap;
  }
  
  /**
   * Response wrapper for Lark Wiki API
   */
  private static class LarkNodesResponse {
    @JsonProperty("code")
    private int code;
    
    @JsonProperty("msg")
    private String msg;
    
    @JsonProperty("data")
    private LarkNodesData data;
    
    public int getCode() {
      return code;
    }
    
    public String getMsg() {
      return msg;
    }
    
    public LarkNodesData getData() {
      return data;
    }
  }
  
  private static class LarkNodesData {
    @JsonProperty("items")
    private List<LarkNode> items;
    
    @JsonProperty("page_token")
    private String pageToken;
    
    @JsonProperty("has_more")
    private boolean hasMore;
    
    public List<LarkNode> getItems() {
      return items != null ? items : Collections.emptyList();
    }
    
    public String getPageToken() {
      return pageToken;
    }
    
    public boolean isHasMore() {
      return hasMore;
    }
  }
}

