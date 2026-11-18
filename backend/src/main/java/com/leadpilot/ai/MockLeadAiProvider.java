package com.leadpilot.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "leadpilot.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockLeadAiProvider implements LeadAiProvider {
    private static final Pattern REQUIREMENT = phrase(
            "\\b(?:[1-6]\\s*bhk(?:\\s+(?:homes?|flats?|apartments?))?|studio\\s+(?:flats?|apartments?)"
                    + "|(?:office|retail|commercial)\\s+(?:spaces?|units?|shops?|properties|property)"
                    + "|independent\\s+(?:houses?|floors?)|villas?|plots?|warehouses?|shops?)\\b");
    private static final Pattern LOCATION = phrase(
            "\\b(?:Greater Noida West|Greater Noida|Noida Extension|(?:Noida\\s+)?Sector[ -]?\\d{1,3}[a-z]?"
                    + "|Techzone[ -]?[1-5]|Gaur City|Noida)\\b");
    private static final Pattern BUDGET = phrase(
            "(?:₹\\s*|\\b(?:INR|Rs\\.?)\\s*)\\d[\\d,]*(?:\\.\\d+)?(?:\\s*(?:lakhs?|lacs?|crores?|cr|k))?\\b"
                    + "|\\b\\d+(?:\\.\\d+)?\\s*(?:lakhs?|lacs?|crores?|cr|k)\\b");
    private static final Pattern TIMELINE = phrase(
            "\\b(?:(?:within|in|by|before|after|next)\\s+(?:(?:a|an|the|\\d+|one|two|three|four|five|six|few)\\s+)?"
                    + "(?:days?|weeks?|months?|years?|Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?"
                    + "|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
                    + "|this\\s+(?:weekend|week|month|year)|immediately|as soon as possible|today|tomorrow)\\b");
    private static final Pattern BROWSING = phrase(
            "\\b(?:just\\s+(?:browsing|curious|researching|exploring|looking around)|only\\s+(?:browsing|researching)"
                    + "|not\\s+(?:looking to buy|ready|planning)|no\\s+(?:plans?|purchase plans|immediate plans)"
                    + "|general information only|for a college assignment|for a school project"
                    + "|please (?:do not|don't) (?:call|contact))\\b");
    private static final Pattern VISIT_SIGNAL = phrase(
            "\\b(?:site\\s+visit|(?:book|schedule|arrange|confirm|tour|view|see|visit)\\b.{0,35}\\b"
                    + "(?:viewing|tour|appointment|meeting|showing|property|apartment|flat|villa|office|unit))\\b");
    private static final Pattern READY_SIGNAL = phrase(
            "\\b(?:ready\\s+to\\s+(?:buy|book|sign|move|lease)|"
                    + "(?:want|need)\\s+to\\s+(?:book|sign|reserve|apply))\\b");
    private static final Pattern MEDIUM_SIGNAL = phrase(
            "\\b(?:shortlist(?:ed|ing)?|looking to (?:buy|rent|lease)|compare|exploring options|considering|"
                    + "interested in|for rent|floor plan|possession|payment plan|rental|lease terms)\\b");
    private static final Pattern RENTAL = phrase("\\b(?:rent(?:al|ing)?|leases?|leasing|tenant)\\b");
    private static final Pattern COMMERCIAL = phrase("\\b(?:office|retail|commercial|warehouse|shop)\\b");
    private static final Pattern PURCHASE = phrase("\\b(?:buy(?:ing)?|purchase|bhk|villas?|plots?)\\b");

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public AnalysisResult analyze(String enquiry) {
        String propertyType = first(REQUIREMENT, enquiry);
        String location = first(LOCATION, enquiry);
        String requirement = propertyType;
        if (propertyType != null && location != null) {
            requirement = propertyType + " in " + location;
        } else if (location != null) {
            requirement = "Mentioned area: " + location;
        }

        String budget = first(BUDGET, enquiry);
        String timeline = first(TIMELINE, enquiry);
        List<String> extracted = new ArrayList<>();
        if (requirement != null) {
            extracted.add("requirement: " + requirement);
        }
        if (budget != null) {
            extracted.add("budget: " + budget);
        }
        if (timeline != null) {
            extracted.add("timeline: " + timeline);
        }
        String summary = extracted.isEmpty()
                ? "The enquiry leaves the property requirement, budget and timeline unspecified."
                : "The customer mentioned " + String.join(", ", extracted) + ".";

        if (BROWSING.matcher(enquiry).find()) {
            return new AnalysisResult(Intent.LOW, requirement, budget, timeline, summary,
                    "Answer the information request and ask whether they would like further contact.",
                    "The customer describes browsing, research or limited readiness.");
        }
        if (VISIT_SIGNAL.matcher(enquiry).find()) {
            return new AnalysisResult(Intent.HIGH, requirement, budget, timeline,
                    summary + " The enquiry mentions a visit or viewing.",
                    "Confirm the customer’s preferences and ask for suitable times for a visit.",
                    "The message contains a concrete visit or viewing signal.");
        }
        if (READY_SIGNAL.matcher(enquiry).find()) {
            return new AnalysisResult(Intent.HIGH, requirement, budget, timeline, summary,
                    "Confirm the stated next step and ask which details the customer needs to proceed.",
                    "The message contains a concrete next step or explicit readiness.");
        }
        if (propertyType != null || budget != null || timeline != null || MEDIUM_SIGNAL.matcher(enquiry).find()) {
            return new AnalysisResult(Intent.MEDIUM, requirement, budget, timeline, summary,
                    "Confirm the customer’s requirements and ask which details would help them narrow the options.",
                    "The enquiry contains preferences or questions, with no concrete next step identified.");
        }
        return new AnalysisResult(Intent.LOW, requirement, budget, timeline, summary,
                "Answer the initial question and ask for a property type or preferred area.",
                "There is limited evidence about purchase or rental intent.");
    }

    @Override
    public ReplyTemplate suggestReply(String enquiry) {
        if (!BROWSING.matcher(enquiry).find() && VISIT_SIGNAL.matcher(enquiry).find()) {
            return ReplyTemplate.SITE_VISIT;
        }
        if (COMMERCIAL.matcher(enquiry).find()) {
            return ReplyTemplate.COMMERCIAL;
        }
        if (RENTAL.matcher(enquiry).find()) {
            return ReplyTemplate.RENTAL;
        }
        if (PURCHASE.matcher(enquiry).find() || REQUIREMENT.matcher(enquiry).find()) {
            return ReplyTemplate.PURCHASE;
        }
        return ReplyTemplate.GENERAL;
    }

    private static Pattern phrase(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static String first(Pattern pattern, String enquiry) {
        Matcher matcher = pattern.matcher(enquiry);
        return matcher.find() ? matcher.group() : null;
    }
}
