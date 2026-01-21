package org.report.backend.searchCustomer.DTO;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CustomerNote {
  private String id;
  private String message;
  private String orderId;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private LocalDateTime removedAt;
  private CustomerNoteCreator createdBy;
  private List<CustomerNoteEdit> editHistory;
  private List<String> images;
  private List<String> links;
}
