package org.report.backend.searchCustomer.DTO;

import java.util.List;

public class customerLookupResponse {
  private customer customer;
  private customerSummary summary;
  private List<order> orders;
  private List<message> messages;
}
