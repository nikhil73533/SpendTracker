package com.example.spendtracker.help;

import java.util.Objects;

public final class HelpItem {
    public final String id;
    public final String category;
    public final String question;
    public final String answer;

    public HelpItem(String id, String category, String question, String answer) {
        this.id = id;
        this.category = category;
        this.question = question;
        this.answer = answer;
    }

    @Override public boolean equals(Object other) {
        return other instanceof HelpItem && Objects.equals(id, ((HelpItem) other).id);
    }

    @Override public int hashCode() { return Objects.hash(id); }
}
