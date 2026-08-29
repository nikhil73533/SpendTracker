package com.example.spendtracker.data.sms.config;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads, caches, and provides bank configurations from {@code assets/bank_configs/}.
 */
public class BankConfigProvider {

    private final Context context;
    private final ConcurrentHashMap<String, BankConfig> configCache = new ConcurrentHashMap<>();
    private List<BankConfig> allConfigs;
    private volatile boolean loaded = false;

    public BankConfigProvider(Context context) {
        this.context = context;
    }

    /** No-arg constructor for tests without Android context. */
    public BankConfigProvider() {
        this.context = null;
    }

    /**
     * Returns all loaded bank configurations. Loads from assets on first call.
     */
    public List<BankConfig> getAllConfigs() {
        ensureLoaded();
        return allConfigs != null ? allConfigs : Collections.emptyList();
    }

    /**
     * Returns the bank config for the given canonical bank name, or null.
     */
    public BankConfig getConfigForBank(String bankName) {
        ensureLoaded();
        return bankName != null ? configCache.get(bankName.toLowerCase()) : null;
    }

    private synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;

        if (context == null) {
            allConfigs = Collections.emptyList();
            return;
        }

        List<BankConfig> configs = new ArrayList<>();
        try {
            String[] files = context.getAssets().list("bank_configs");
            if (files == null) {
                allConfigs = Collections.emptyList();
                return;
            }

            for (String file : files) {
                BankConfig config = loadConfig("bank_configs/" + file);
                if (config != null) {
                    configs.add(config);
                    configCache.put(config.getBankName().toLowerCase(), config);
                }
            }
        } catch (Exception ignored) {}

        allConfigs = Collections.unmodifiableList(configs);
    }

    private BankConfig loadConfig(String assetPath) {
        try (InputStream is = context.getAssets().open(assetPath)) {
            byte[] buf = new byte[is.available()];
            is.read(buf);
            String json = new String(buf, StandardCharsets.UTF_8);

            JSONObject obj = new JSONObject(json);
            BankConfig config = new BankConfig();
            config.setBankName(obj.getString("bankName"));

            // Parse senders array (optional)
            List<String> senders = new ArrayList<>();
            if (obj.has("senders")) {
                JSONArray sendersArr = obj.getJSONArray("senders");
                for (int i = 0; i < sendersArr.length(); i++) {
                    senders.add(sendersArr.getString(i));
                }
            }
            config.setSenders(senders);

            // Parse patterns
            List<BankConfig.PatternConfig> patterns = new ArrayList<>();
            JSONArray patternsArr = obj.getJSONArray("patterns");
            for (int i = 0; i < patternsArr.length(); i++) {
                JSONObject p = patternsArr.getJSONObject(i);
                BankConfig.PatternConfig pc = new BankConfig.PatternConfig();
                pc.setName(p.optString("name", ""));
                pc.setRegex(p.getString("regex"));
                pc.setAmountGroup(p.optInt("amountGroup", 0));
                pc.setAccountGroup(p.optInt("accountGroup", 0));
                pc.setReceiverGroup(p.optInt("receiverGroup", 0));
                pc.setUpiGroup(p.optInt("upiGroup", 0));
                pc.setDateGroup(p.optInt("dateGroup", 0));
                pc.setType(p.optString("type", "EXPENSE"));
                pc.setSourceType(p.optString("sourceType", "Account"));
                patterns.add(pc);
            }
            config.setPatterns(patterns);

            return config;
        } catch (Exception e) {
            return null;
        }
    }
}
