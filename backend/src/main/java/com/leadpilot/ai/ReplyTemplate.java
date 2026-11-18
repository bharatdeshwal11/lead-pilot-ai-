package com.leadpilot.ai;

enum ReplyTemplate {
    SITE_VISIT("""
            Thank you for asking about a site visit. Could you share the area or property type you are interested in and two times that suit you? We can then check the next steps for arranging a visit.
            """),
    RENTAL("""
            Thank you for your rental enquiry. Could you share your preferred area, property type, approximate monthly budget and move-in date? That will help us understand your requirements.
            """),
    COMMERCIAL("""
            Thank you for your interest in commercial space. Could you share the intended use, preferred location, approximate space needed and move-in timeline? We can use those details to understand what you are looking for.
            """),
    PURCHASE("""
            Thank you for your property enquiry. Could you share any priorities you have for the location, layout or move-in date? If you already have a shortlist, feel free to include it so we can discuss your requirements.
            """),
    GENERAL("""
            Thank you for reaching out. Could you tell us what kind of property you are interested in and which areas you are considering? If you have any questions about getting started, please send them along.
            """);

    private final String message;

    ReplyTemplate(String message) {
        this.message = message.strip();
    }

    String body() {
        return "Hello,\n\n" + message + "\n\nRegards,\nThe Sales Team";
    }
}
