package org.report.backend.searchCustomer.DTO;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class CustomerNoteCreator {
  private String uid;
  private String fbId;
  private String fbName;
  private String sessionId;
  private String tokenForBusiness;
  private int application;
}
