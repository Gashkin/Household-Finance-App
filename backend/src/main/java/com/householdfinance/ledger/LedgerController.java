package com.householdfinance.ledger;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {
  private final LedgerService service;
  private final ObjectMapper json;
  @org.springframework.beans.factory.annotation.Autowired private ActivityReviewService review;
  @org.springframework.beans.factory.annotation.Autowired private BudgetService budgets;
  @org.springframework.beans.factory.annotation.Autowired private GoalService goals;
  @org.springframework.beans.factory.annotation.Autowired private DebtService debts;
  @GetMapping("/debts") public Object debts(){return debts.get();}
  @PostMapping("/debts") public Object debtChange(HttpServletRequest r)throws IOException{return debts.change(body(r,DebtService.Change.class));}
  @PostMapping("/debts/compare") public Object debtCompare(HttpServletRequest r)throws IOException{return debts.compare(body(r,DebtService.Compare.class));}
  @GetMapping("/debts/report") public Object debtReport(){return debts.report(null);}
  @PostMapping("/debts/report") public Object debtComparisonReport(HttpServletRequest r)throws IOException{return debts.report(body(r,DebtService.Compare.class));}
  @GetMapping("/debts/history") public Object debtHistory(){return debts.history();}
  @GetMapping("/debts/operations/{id}") public Object debtOperation(@PathVariable("id")String id){return debts.operation(id);}
  @GetMapping("/goals") public Object goals(){return goals.get();}
  @PostMapping("/goals") public Object goalChange(HttpServletRequest r)throws IOException{return goals.change(body(r,GoalService.Change.class));}
  @GetMapping("/goals/history") public Object goalHistory(){return goals.history();}
  @GetMapping("/goals/operations/{id}") public Object goalOperation(@PathVariable("id")String id){return goals.operation(id);}
  @GetMapping("/budgets/templates") public Object budgetTemplates(){return budgets.templates();}
  @GetMapping("/budgets/templates/{id}/history") public Object budgetTemplateHistory(@PathVariable("id")String id){return budgets.templateHistory(id);}
  @PostMapping("/budgets/templates") public Object createBudgetTemplate(HttpServletRequest r)throws IOException{return budgets.template(null,body(r,BudgetService.TemplateInput.class));}
  @PutMapping("/budgets/templates/{id}") public Object budgetTemplate(@PathVariable("id")String id,HttpServletRequest r)throws IOException{return budgets.template(id,body(r,BudgetService.TemplateInput.class));}
  @PostMapping("/budgets/templates/{id}/periods") public Object budgetPeriod(@PathVariable("id")String id,HttpServletRequest r)throws IOException{return budgets.instantiate(id,body(r,BudgetService.Instantiate.class));}
  @GetMapping("/budgets/periods") public Object budgetPeriods(){return budgets.periods();}
  @GetMapping("/budgets/periods/{id}") public Object budgetReport(@PathVariable("id")String id){return budgets.report(id);}
  @PostMapping("/budgets/periods/{id}/rollover") public Object correctBudgetRollover(@PathVariable("id")String id,HttpServletRequest r)throws IOException{return budgets.correctRollover(id,body(r,BudgetService.CarryCorrection.class));}
  @PutMapping("/budgets/periods/{id}") public Object budgetChange(@PathVariable("id")String id,HttpServletRequest r)throws IOException{return budgets.change(id,body(r,BudgetService.Change.class));}
  @PostMapping("/budgets/periods/{id}/apply") public Object budgetApply(@PathVariable("id")String id,HttpServletRequest r)throws IOException{return budgets.apply(id,body(r,BudgetService.Apply.class));}
  @GetMapping("/budgets/periods/{id}/history") public Object budgetHistory(@PathVariable("id")String id){return budgets.history(id);}

  @org.springframework.beans.factory.annotation.Autowired private LocalClassifier localClassifier;
  @org.springframework.beans.factory.annotation.Autowired private CommitmentService commitments;

  @PostMapping("/review/activity/{id}/suggest")
  public java.util.concurrent.Callable<LocalClassifier.Result> suggest(@PathVariable("id") String id, HttpServletRequest request) throws IOException {
    var revision = body(request, ActivityReviewService.Revision.class).revision();
    var entry = review.suggestionEntry(id, revision);
    var categories = review.categories();
    var shortlist = commitments.shortlist(entry);
    // Authorise and snapshot under workspace admission. Release admission before waiting on inference.
    return () -> localClassifier.suggest(entry, categories, shortlist, revision);
  }

  @PutMapping("/review/activity/{id}/merchant")
  public Object merchant(@PathVariable("id") String id, HttpServletRequest request) throws IOException {
    review.merchant(id, body(request, ActivityReviewService.Merchant.class));
    return Map.of("saved", true);
  }

  @GetMapping("/review/categories") public Object categories(){return review.categories();}
  @PostMapping("/review/categories") public Object createCategory(HttpServletRequest r)throws IOException{return review.category(null,body(r,ActivityReviewService.CategoryInput.class));}
  @PutMapping("/review/categories/{id}") public Object category(@PathVariable("id")String id,HttpServletRequest r)throws IOException{return review.category(id,body(r,ActivityReviewService.CategoryInput.class));}
  @GetMapping("/review/rules") public Object rules(){return review.rules();}
  @PutMapping("/review/rules/{id}") public Object rule(@PathVariable("id")String id,HttpServletRequest r)throws IOException{return review.approveRule(id,body(r,ActivityReviewService.RuleApproval.class));}
  @GetMapping("/review/queue") public Object queue(@RequestParam(value="account",required=false)String account,@RequestParam("from")String from,@RequestParam("to")String to,@RequestParam(value="issue",required=false)String issue,@RequestParam(value="risk",required=false)String risk){return review.queue(account,LocalDate.parse(from),LocalDate.parse(to),issue,risk);}
  @PutMapping("/review/activity/{id}") public Object classify(@PathVariable("id")String id,HttpServletRequest r)throws IOException{review.classify(id,body(r,ActivityReviewService.Classification.class));return Map.of("saved",true);}
  @PostMapping("/review/activity/{id}/link") public Object link(@PathVariable("id")String id,HttpServletRequest r)throws IOException{review.link(id,body(r,ActivityReviewService.Link.class));return Map.of("saved",true);}
  @PostMapping("/review/activity/{id}/unlink") public Object unlink(@PathVariable("id")String id,HttpServletRequest r)throws IOException{review.unlink(id,body(r,ActivityReviewService.Link.class));return Map.of("saved",true);}
  @PostMapping("/accounts/{id}/reopen") public Object reopen(@PathVariable("id")String id,HttpServletRequest r)throws IOException{review.reopen(id,body(r,ActivityReviewService.Reopen.class));return Map.of("saved",true);}
  @GetMapping("/review/guide") public Object guide(@RequestParam("through")String date){return Map.of("guide",review.guide(LocalDate.parse(date)),"auditCount",review.visibleAuditCount());}
  @PostMapping("/review/complete") public Object complete(HttpServletRequest r)throws IOException{return review.complete(body(r,ActivityReviewService.Completion.class));}

  public LedgerController(LedgerService service, ObjectMapper mapper) {
    this.service = service;
    json =
        mapper
            .copy()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .enable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    json.coercionConfigFor(LogicalType.Integer)
        .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
    json.coercionConfigFor(LogicalType.Boolean)
        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
    json.coercionConfigFor(LogicalType.Textual)
        .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
  }

  private <T> T body(HttpServletRequest request, Class<T> type) throws IOException {
    int limit = 1024 * 1024;
    if (request.getContentLengthLong() > limit)
      throw new IllegalArgumentException("Ledger request too large");
    byte[] bytes = request.getInputStream().readNBytes(limit + 1);
    if (bytes.length > limit) throw new IllegalArgumentException("Ledger request too large");
    try {
      T value = json.readValue(bytes, type);
      if (value == null) throw new IllegalArgumentException("Complete ledger request required");
      return value;
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid ledger fields or types");
    } finally {
      Arrays.fill(bytes, (byte) 0);
    }
  }

  @GetMapping
  public LedgerService.Position position(@RequestParam("asOf") String date) {
    return service.position(LocalDate.parse(date));
  }
  @GetMapping("/shared")
  public LedgerService.Position shared(@RequestParam("asOf")String date){return service.sharedPosition(LocalDate.parse(date));}

  @PostMapping("/accounts")
  public LedgerService.AccountView create(HttpServletRequest r) throws IOException {
    return service.create(body(r, LedgerService.AccountInput.class));
  }

  @PutMapping("/accounts/{id}/opening")
  public LedgerService.AccountView correctOpening(
      @PathVariable("id") String id, HttpServletRequest r) throws IOException {
    return service.correctOpening(id, body(r, LedgerService.OpeningCorrection.class));
  }

  @DeleteMapping("/accounts/{id}")
  public Map<String, Boolean> removeUnusedAccount(
      @PathVariable("id") String id, HttpServletRequest r) throws IOException {
    service.removeUnusedAccount(id, body(r, LedgerService.RemoveUnusedAccount.class));
    return Map.of("removed", true);
  }

  @PutMapping("/accounts/{id}/sharing")
  public LedgerService.AccountView share(@PathVariable("id") String id,HttpServletRequest r)throws IOException {
    return service.share(id,body(r,LedgerService.Sharing.class));
  }

  @GetMapping("/accounts/{id}/activity")
  public List<LedgerEntry> activity(@PathVariable("id") String id) {
    return service.activity(id);
  }

  @GetMapping("/accounts/{id}/history")
  public List<LedgerAudit> history(@PathVariable("id") String id) {
    return service.history(id);
  }

  @GetMapping("/history")
  public List<LedgerAudit> publications() {
    return service.history("HOUSEHOLD");
  }

  @PostMapping("/accounts/{id}/preview")
  public LedgerService.Preview preview(@PathVariable("id") String id, HttpServletRequest r)
      throws IOException {
    return service.preview(id, body(r, LedgerService.PreviewInput.class));
  }

  @PostMapping("/accounts/{id}/previews/{key}/approve")
  public LedgerService.Imported approve(
      @PathVariable("id") String id, @PathVariable("key") String key, HttpServletRequest r)
      throws IOException {
    return service.approve(id, key, body(r, LedgerService.Approval.class));
  }

  @PostMapping("/accounts/{id}/activity")
  public LedgerService.AccountView manual(@PathVariable("id") String id, HttpServletRequest r)
      throws IOException {
    return service.manual(id, body(r, LedgerService.Manual.class));
  }

  @PutMapping("/accounts/{id}/activity/{entry}")
  public LedgerService.AccountView correct(
      @PathVariable("id") String id, @PathVariable("entry") String entry, HttpServletRequest r)
      throws IOException {
    return service.correct(id, entry, body(r, LedgerService.Correction.class));
  }

  @PostMapping("/accounts/{id}/reconcile")
  public LedgerService.Reconciliation reconcile(
      @PathVariable("id") String id, @RequestParam("asOf") String asOf, HttpServletRequest r)
      throws IOException {
    return service.reconcile(id, LocalDate.parse(asOf), body(r, LedgerService.Reconcile.class));
  }

  @PostMapping("/publish")
  public com.householdfinance.service.CashPositionService.Snapshot publish(HttpServletRequest r)
      throws IOException {
    return service.publish(body(r, LedgerService.Publish.class));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    ArithmeticException.class,
    java.time.DateTimeException.class
  })
  public ResponseEntity<Map<String, String>> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "error",
                e instanceof java.time.DateTimeException
                    ? "Invalid ledger date; use YYYY-MM-DD"
                    : Objects.toString(e.getMessage(), "Invalid ledger request")));
  }
}
