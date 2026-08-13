package com.example.spendtracker.domain.model;

public class Category {
    private int id;
    private String name;
    private String icon;
    private boolean isDefault;

    public Category(int id, String name, String icon, boolean isDefault) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.isDefault = isDefault;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getIcon() { return icon; }
    public boolean isDefault() { return isDefault; }
}
