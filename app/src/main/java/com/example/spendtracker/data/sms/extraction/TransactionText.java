package com.example.spendtracker.data.sms.extraction;

import java.util.regex.Pattern;

/** Shared lexical boundaries, not a probability model. Raw messages are never modified. */
public final class TransactionText {
    private TransactionText() { }
    private static final Pattern FOOTER = Pattern.compile(
            "(?i)\\b(?:if\\s+not\\s+(?:you|u|done)|not\\s+(?:you|u)\\s*\\?|"
            + "do\\s+not\\s+share|never\\s+share|to\\s+dispute|fraud\\s*\\?)");
    private static final Pattern FUTURE_CLAUSE = Pattern.compile(
            "(?is)\\b(?:(?:will|would|shall|may)\\s+(?:be|not\\s+be|get|debit|credit)|"
            + "scheduled\\s+to|due\\s+to\\s+be|going\\s+to\\s+be)\\b"
            + ".*?(?:[;!?]|\\.(?=\\s|$)|$)");
    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?is)\\b(?:otp|one[- ]time\\s+(?:password|passcode)|verification\\s+code)\\b"
            + ".{0,35}\\b\\d{4,8}\\b|\\b\\d{4,8}\\b.{0,25}\\b(?:otp|one[- ]time\\s+password)\\b"
            + "|\\b(?:otp|one[- ]time\\s+password)\\s+(?:for|to)\\b"
            + "|\\b(?:requested\\s+(?:money|payment)|approve\\s+(?:the\\s+)?(?:request|mandate)|collect\\s+request)\\b");
    public static final Pattern ACTION = Pattern.compile(
            "(?i)\\b(?:debited|credited|deducted|deposited|withdrawn|withdrawal|spent|paid|charged|"
            + "received|sent|used|transferred|refunded|reversed|dr|cr)\\b|\\bfund\\s+transfer\\b");

    public static String core(String text) {
        if (text == null) return "";
        java.util.regex.Matcher footer = FOOTER.matcher(text);
        return (footer.find() ? text.substring(0, footer.start()) : text).trim();
    }
    public static String posted(String text) {
        return FUTURE_CLAUSE.matcher(core(text)).replaceAll(" ");
    }
    public static boolean isAuthorization(String text) {
        return AUTHORIZATION.matcher(core(text)).find();
    }
    public static boolean hasAction(String text) {
        return ACTION.matcher(posted(text)).find();
    }
}
