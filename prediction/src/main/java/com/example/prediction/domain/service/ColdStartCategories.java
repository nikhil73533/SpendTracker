package com.example.prediction.domain.service;

import java.util.*;

/** Conservative offline hints, separate from user training examples. Whole words/phrases only. */
final class ColdStartCategories {
    private static final String[][] EXPENSE = {
        {"Food|Dining", "swiggy|zomato|restaurant|cafe|coffee|canteen|bakery|dhaba|biryani|pizza|dominos|mcdonalds|kfc|burger|tea stall|tiffin|mess|food"},
        {"Groceries|Food", "bigbasket|blinkit|zepto|instamart|dmart|d mart|reliance fresh|grocery|groceries|kirana|supermarket|vegetables|dairy|milk"},
        {"Transport|Travel", "uber|ola|rapido|irctc|railway|railways|metro|bus|petrol|diesel|fuel|indianoil|iocl|bpcl|hpcl|fastag|toll|parking|taxi|indigo|air india|makemytrip|redbus"},
        {"Health|Medical|Healthcare", "hospital|clinic|pharmacy|medical|medicals|medicine|medicines|diagnostic|diagnostics|pathology|dental|dentist|doctor|netmeds|pharmeasy|apollo pharmacy"},
        {"Education", "school|college|university|tuition|coaching|udemy|coursera|unacademy|byjus|physicswallah|education|exam fee|course fee"},
        {"Shopping", "amazon|flipkart|myntra|ajio|meesho|nykaa|croma|reliance digital|decathlon|clothing|garments|footwear|electronics|furniture"},
        {"Rent", "rent|rental|landlord|house lease|pg accommodation"},
        {"Utilities|Bills|Other", "electricity|water bill|broadband|recharge|airtel|jio|bsnl|vi prepaid|bescom|mseb|msedcl|tata power|gas bill|indane|bharatgas"},
        {"Entertainment|Other", "netflix|spotify|hotstar|cinema|movie|bookmyshow|pvr|inox|youtube premium"},
        {"Insurance|Other", "insurance|lic premium|policy premium"},
        {"Fees|Other", "bank charges|service charge|sms charge|annual fee|atm fee"}
    };
    private static final String[][] INCOME = {
        {"Salary", "salary|payroll|sal credit|wages"},
        {"Bonus", "bonus|incentive"},
        {"Allowance", "allowance|stipend|pension"},
        {"Gift", "gift"},
        {"Interest|Other Income|Other Inc|Other", "interest|int credit"},
        {"Refund|Other Income|Other Inc|Other", "refund|cashback|reimbursement|reversal"},
        {"Investment|Other Income|Other Inc|Other", "dividend|redemption|maturity proceeds"}
    };
    static Map<String, Double> scores(String text, String type, Collection<String> allowed) {
        Map<String, Double> scores = new TreeMap<>();
        String words = " " + CategoryText.normalize(text) + " ";
        for (String[] rule : "INCOME".equals(type) ? INCOME : EXPENSE) {
            String category = resolve(rule[0], allowed);
            if (category == null) continue;
            for (String phrase : rule[1].split("\\|")) {
                if (words.contains(" " + phrase + " ")) {
                    scores.put(category, 1.0);
                    break;
                }
            }
        }
        return scores;
    }
    static String resolve(String aliases, Collection<String> allowed) {
        for (String category : allowed) if (category.equalsIgnoreCase(aliases)) return category;
        for (String alias : aliases.split("\\|")) {
            for (String category : allowed) if (category.equalsIgnoreCase(alias)) return category;
            String match = null;
            for (String category : allowed) {
                if (CategoryText.categoryKey(category).equals(CategoryText.categoryKey(alias))) {
                    if (match != null) return null; // Ambiguous display-name aliases need review.
                    match = category;
                }
            }
            if (match != null) return match;
        }
        return null;
    }
}
