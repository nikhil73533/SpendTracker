package com.example.spendtracker.data.sms.extraction;
import java.util.*;
import java.util.regex.*;

/** Deterministic entity spans supplement amount regexes; this is not a trained NER model. */
public final class NumericEntityRoles {
    public enum Kind { DATE, ACCOUNT, REFERENCE }
    public static final class Span {
        public final int start, end; public final Kind kind;
        Span(int start,int end,Kind kind) { this.start=start; this.end=end; this.kind=kind; }
        public boolean overlaps(int from,int to) { return from<end && to>start; }
    }
    private static final Pattern[] PATTERNS = {
        Pattern.compile("(?i)(?<![\\w])(?:\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}|\\d{1,2}[-/.](?:\\d{1,2}|[a-z]{3,9})[-/.]\\d{2,4}|\\d{1,2}\\s+[a-z]{3,9}\\s+\\d{2,4})(?!\\d)"),
        Pattern.compile("(?i)\\b(?:a/c|acct|account|card)\\s*(?:no\\.?|number|ending|xx|x|[*])*\\s*[:.-]?\\s*[xX*]*\\d+"),
        Pattern.compile("(?i)\\b(?:ref(?:erence)?(?:\\s*no\\.?)?|rrn|utr|txn\\s*(?:id|no))\\s*[:/#-]?\\s*[a-z0-9]{5,}")
    };
    public static List<Span> identify(String text) {
        List<Span> result=new ArrayList<>();
        for(int i=0;i<PATTERNS.length;i++) { Matcher m=PATTERNS[i].matcher(text); while(m.find()) result.add(new Span(m.start(),m.end(),Kind.values()[i])); }
        return result;
    }
    public static boolean overlaps(List<Span> spans,int start,int end) { for(Span span:spans) if(span.overlaps(start,end)) return true; return false; }
}
