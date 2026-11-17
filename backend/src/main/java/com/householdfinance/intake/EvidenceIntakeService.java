package com.householdfinance.intake;

import com.householdfinance.service.CashPositionService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class EvidenceIntakeService {
    public record Proposal(int lineNumber, String kind, String label, String amountEuro, String quote, String uncertainty) {}
    public record View(String id, Instant expiresAt, String method, List<Proposal> proposals, List<String> warnings) {}
    public record Row(int lineNumber, String kind, String label, long amountMinor) {}
    public record Approval(LocalDate asOfDate, long minimumReserveMinor, boolean acknowledged, List<Row> rows) {}
    private static final Pattern EURO = Pattern.compile("(?i)(?:EUR|€)\\s*((?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]{1,2})?)(?![0-9.,])");
    private static final Duration TTL = Duration.ofMinutes(10);
    private static class Draft {
        String reference; List<String> lines; Instant expires; Approval approval; CashPositionService.Snapshot saved;
        Draft(String reference, List<String> lines, Instant expires) { this.reference = reference; this.lines = lines; this.expires = expires; }
    }
    private final Map<String, Draft> drafts = new HashMap<>();
    private final CashPositionService cash;
    private final EvidenceModelGateway model;
    private final Clock clock;
    @Autowired public EvidenceIntakeService(CashPositionService cash, EvidenceModelGateway model) { this(cash, model, Clock.systemUTC()); }
    EvidenceIntakeService(CashPositionService cash, EvidenceModelGateway model, Clock clock) { this.cash = cash; this.model = model; this.clock = clock; }
    @Configuration @EnableScheduling static class ExpiryConfiguration {}
    @Scheduled(fixedDelay = 60000) public synchronized void purge() { drafts.values().removeIf(d -> !d.expires.isAfter(clock.instant())); }

    public View create(String source, String reference, boolean allowModel) {
        com.householdfinance.access.AccessContext.requireLegacy();
        if (source == null || source.isBlank() || source.length() > 4000 || source.split("\\R", -1).length > 80)
            throw new IllegalArgumentException("Provide 1–4000 characters in at most 80 source lines");
        if (reference == null || reference.isBlank() || reference.strip().length() > 100)
            throw new IllegalArgumentException("Provide a source reference of 1–100 characters");
        List<String> lines = List.of(source.split("\\R", -1));
        var warnings = new ArrayList<String>();
        String method = "LOCAL";
        List<Proposal> proposals = local(lines);
        if (allowModel) {
            try {
                var extracted = model.extract(source, true);
                if (extracted.isPresent()) {
                    var validated = new ArrayList<Proposal>();
                    for (var candidate : extracted.get()) validated.add(validateModel(candidate, lines));
                    if (validated.isEmpty() && !proposals.isEmpty()) throw new IllegalArgumentException("Model omitted locally supported facts");
                    proposals = List.copyOf(validated); method = "MODEL";
                } else warnings.add("Model assistance is not configured. Local suggestions are shown instead.");
            } catch (Exception ignored) {
                if (ignored instanceof InterruptedException) Thread.currentThread().interrupt();
                warnings.add("Model assistance was unavailable or its output failed source checks. Local suggestions are shown instead.");
            }
        }
        warnings.add("Suggestions are unverified. Check account type, amounts and whether bills are unpaid; credit limits and future income are not available cash.");
        if (proposals.isEmpty()) warnings.add("No supported EUR amounts found. Add source-linked facts manually; no values were invented.");
        if (proposals.size() > 30) { proposals = List.copyOf(proposals.subList(0, 30)); warnings.add("Only the first 30 suggestions are included. Review any omitted source lines separately."); }
        String id = UUID.randomUUID().toString(); Instant expires = clock.instant().plus(TTL);
        synchronized (this) {
            purge();
            if (drafts.size() >= 32) throw new ResponseStatusException(TOO_MANY_REQUESTS, "Too many active drafts; discard a draft or wait for expiry");
            drafts.put(id, new Draft(reference.strip(), lines, expires));
        }
        return new View(id, expires, method, proposals, List.copyOf(warnings));
    }
    private List<Proposal> local(List<String> lines) {
        var result = new ArrayList<Proposal>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (unsupportedLine(line)) continue;
            Matcher match = EURO.matcher(line);
            while (match.find()) {
                String label = line.substring(0, match.start()).replaceAll("[:|\\s]+$", "").strip();
                if (label.isEmpty()) label = "Source line " + (i + 1);
                if (label.length() > 100) label = label.substring(0, 100);
                String lower = line.toLowerCase(Locale.ROOT);
                String kind = lower.matches(".*\\b(bill|due|commitment|invoice)\\b.*") ? "COMMITMENT" : "CASH";
                String amount = match.group(1).replace(",", "");
                try { minor(amount); } catch (IllegalArgumentException ignored) { continue; }
                result.add(new Proposal(i + 1, kind, label, new BigDecimal(amount).toPlainString(), line,
                    "Type and label are suggestions, not verified facts. Check this quoted line."));
            }
        }
        return result;
    }
    private boolean unsupportedLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        // Be conservative: negative balances and credit/income are not supported cash.
        return lower.matches(".*\\b(credit limit|available credit|overdraft limit|salary|income)\\b.*")
            || Pattern.compile("(?i)(?:[-−]\\s*(?:EUR|€)|(?:EUR|€)\\s*[-−]|\\([^)]*(?:EUR|€)|(?:EUR|€).*\\bDR\\b)").matcher(line).find();
    }
    private Proposal validateModel(EvidenceModelGateway.Candidate candidate, List<String> lines) {
        if (candidate.lineNumber() < 1 || candidate.lineNumber() > lines.size() || !Set.of("CASH", "COMMITMENT").contains(candidate.kind())
            || candidate.label().isBlank() || candidate.label().length() > 100)
            throw new IllegalArgumentException("Invalid suggested fact");
        String quote = lines.get(candidate.lineNumber() - 1);
        if (unsupportedLine(quote)) throw new IllegalArgumentException("Unsupported cash source");
        if (!quote.toLowerCase(Locale.ROOT).contains(candidate.label().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Label absent from source");
        long amount = minor(candidate.amountEuro()); boolean found = false;
        Matcher matcher = EURO.matcher(quote);
        while (matcher.find()) if (minor(matcher.group(1).replace(",", "")) == amount) found = true;
        if (!found) throw new IllegalArgumentException("Amount absent from cited line");
        return new Proposal(candidate.lineNumber(), candidate.kind(), candidate.label(), candidate.amountEuro(), quote,
            "Model suggestion; exact amount checked against source, meaning and account type still require your review.");
    }
    public synchronized CashPositionService.Snapshot approve(String id, Approval approval) {
        com.householdfinance.access.AccessContext.requireLegacy();
        purge(); Draft draft = drafts.get(id);
        if (draft == null) throw new ResponseStatusException(NOT_FOUND, "Draft expired or was discarded; extract again");
        if (draft.saved != null) {
            if (!draft.approval.equals(approval)) throw new ResponseStatusException(CONFLICT, "This draft was already approved with different values");
            return draft.saved;
        }
        if (approval == null || !approval.acknowledged() || approval.rows() == null || approval.rows().isEmpty() || approval.rows().size() > 30)
            throw new IllegalArgumentException("Explicit review acknowledgement and 1–30 reviewed facts are required");
        var balances = new ArrayList<CashPositionService.Line>(); var bills = new ArrayList<CashPositionService.Line>();
        Set<Row> seen = new HashSet<>();
        for (var row : approval.rows()) {
            if (row == null || row.lineNumber() < 1 || row.lineNumber() > draft.lines.size() || !("CASH".equals(row.kind()) || "COMMITMENT".equals(row.kind()))
                || row.label() == null || row.label().isBlank() || row.label().length() > 100 || row.amountMinor() < 0)
                throw new IllegalArgumentException("Every reviewed fact needs a source line, kind, label and nonnegative amount");
            if (!seen.add(row)) throw new IllegalArgumentException("Duplicate reviewed fact; remove the repeated row");
            var fact = new CashPositionService.Line(row.label().strip(), row.amountMinor());
            (row.kind().equals("CASH") ? balances : bills).add(fact);
        }
        var saved = cash.save(new CashPositionService.Input(approval.asOfDate(), "Reviewed intake: " + draft.reference + " (source text not retained)",
            "REVIEWED", balances, bills, approval.minimumReserveMinor()));
        draft.approval = approval; draft.saved = saved; draft.lines = List.of(); // Drop source references immediately on approval.
        return saved;
    }
    public synchronized void discard(String id) {
        com.householdfinance.access.AccessContext.requireLegacy(); drafts.remove(id); }
    public static long minor(String euro) {
        if (euro == null || euro.length() > 18 || !euro.matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Amounts must be nonnegative euro text with at most two decimals");
        try { long cents = new BigDecimal(euro).movePointRight(2).longValueExact();
            if (cents > 9_007_199_254_740_991L) throw new ArithmeticException(); return cents;
        } catch (ArithmeticException e) { throw new IllegalArgumentException("Amount exceeds the supported range"); }
    }
}
