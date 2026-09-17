package com.example.spendtracker.data.sms.preprocessing;

import java.util.*;

/** Android delivers the ordered segments of one multipart SMS in one broadcast. */
public final class SmsMessageAssembler {
    private SmsMessageAssembler() { }
    public static final class Part {
        public final String sender;
        public final String body;
        public final long timestamp;
        public Part(String sender, String body, long timestamp) {
            this.sender = sender == null ? "" : sender;
            this.body = body == null ? "" : body;
            this.timestamp = timestamp;
        }
    }
    public static List<Part> assemble(List<Part> broadcastParts) {
        Map<String, StringBuilder> bodies = new LinkedHashMap<>();
        Map<String, Long> dates = new HashMap<>();
        for (Part part : broadcastParts) {
            if (part == null) continue;
            bodies.computeIfAbsent(part.sender, ignored -> new StringBuilder()).append(part.body);
            dates.putIfAbsent(part.sender, part.timestamp);
        }
        List<Part> messages = new ArrayList<>();
        for (Map.Entry<String, StringBuilder> entry : bodies.entrySet())
            messages.add(new Part(entry.getKey(), entry.getValue().toString(), dates.get(entry.getKey())));
        return messages;
    }
}
