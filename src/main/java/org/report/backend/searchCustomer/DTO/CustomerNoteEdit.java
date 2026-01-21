package org.report.backend.searchCustomer.DTO;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CustomerNoteEdit {
  private LocalDateTime createdAt;
  private CustomerNoteCreator createdBy;
  private List<String> images;
  private String message;
}
