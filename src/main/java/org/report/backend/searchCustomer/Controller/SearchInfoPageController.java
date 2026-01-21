package org.report.backend.searchCustomer.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SearchInfoPageController {

  @GetMapping("/search-info")
  public String searchInfoPage() {
    return "searchInfo";
  }
}
