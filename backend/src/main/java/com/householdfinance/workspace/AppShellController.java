package com.householdfinance.workspace;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class AppShellController {
  @GetMapping({"/bank/callback", "/workspaces/{module:[a-z-]+}/{page:[a-z-]+}"})
  public String workspacePage() { return "forward:/index.html"; }
}
