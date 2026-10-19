package com.example.spendtracker.ui.dashboard;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Selection follows persisted IDs, never RecyclerView positions or date headers. */
final class TransactionSelection {
    private final Set<Integer> selected = new LinkedHashSet<>();
    private boolean active;

    boolean isActive() { return active; }
    boolean contains(int id) { return selected.contains(id); }
    int size() { return selected.size(); }
    void toggle(int id) {
        active = true;
        if (!selected.add(id)) selected.remove(id);
    }
    void selectAll(Collection<Integer> ids) {
        active = true;
        selected.clear();
        selected.addAll(ids);
    }
    void retain(Collection<Integer> ids) {
        selected.retainAll(ids);
        if (ids.isEmpty()) clear();
    }
    void clear() { selected.clear(); active = false; }
}
