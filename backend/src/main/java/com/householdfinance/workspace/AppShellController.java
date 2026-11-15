package com.householdfinance.workspace;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class AppShellController {
  @GetMapping("/bank/callback")
  public String callback() { return "forward:/index.html"; }
}
