package org.report.backend.searchCustomer.DTO;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
public class Message {
  private String message;
  private String createdBy;
  private LocalDateTime createdAt;
  private Long orderId;
}
