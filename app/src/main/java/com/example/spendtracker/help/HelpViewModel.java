package com.example.spendtracker.help;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HelpViewModel extends AndroidViewModel {
    private final MutableLiveData<List<HelpItem>> items = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<List<String>> categories = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> loadFailed = new MutableLiveData<>(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private List<HelpItem> allItems = Collections.emptyList();
    private String query = "";
    private String category = "";

    public HelpViewModel(@NonNull Application application) {
        super(application);
        load();
    }

    public LiveData<List<HelpItem>> getItems() { return items; }
    public LiveData<List<String>> getCategories() { return categories; }
    public LiveData<Boolean> getLoadFailed() { return loadFailed; }

    public void setQuery(String query) {
        this.query = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        filter();
    }

    public void setCategory(String category) {
        this.category = category == null ? "" : category;
        filter();
    }

    private void load() {
        executor.execute(() -> {
            try (InputStream input = getApplication().getAssets().open("help/faq.json")) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                JSONArray array = new JSONArray(output.toString(StandardCharsets.UTF_8.name()));
                List<HelpItem> parsed = new ArrayList<>();
                Set<String> categorySet = new LinkedHashSet<>();
                for (int index = 0; index < array.length(); index++) {
                    JSONObject entry = array.getJSONObject(index);
                    HelpItem item = new HelpItem(entry.getString("id"), entry.getString("category"),
                            entry.getString("question"), entry.getString("answer"));
                    parsed.add(item);
                    categorySet.add(item.category);
                }
                allItems = Collections.unmodifiableList(parsed);
                categories.postValue(new ArrayList<>(categorySet));
                filter();
            } catch (Exception ignored) {
                loadFailed.postValue(true);
                items.postValue(Collections.emptyList());
            }
        });
    }

    private void filter() {
        List<HelpItem> source = allItems;
        List<HelpItem> result = new ArrayList<>();
        for (HelpItem item : source) {
            boolean categoryMatches = category.isEmpty() || category.equals(item.category);
            boolean queryMatches = query.isEmpty()
                    || item.question.toLowerCase(Locale.ROOT).contains(query)
                    || item.answer.toLowerCase(Locale.ROOT).contains(query)
                    || item.category.toLowerCase(Locale.ROOT).contains(query);
            if (categoryMatches && queryMatches) result.add(item);
        }
        items.postValue(result);
    }

    @Override protected void onCleared() {
        executor.shutdownNow();
    }
}
