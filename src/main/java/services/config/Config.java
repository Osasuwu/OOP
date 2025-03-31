package services.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger(Config.class);
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "config/app.json";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    private Map<String, Object> settings;

    public static Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    private Config() {
        settings = new HashMap<>();
        createConfigDirectory();
        loadConfig();
    }

    private void createConfigDirectory() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                settings = gson.fromJson(reader, Map.class);
                if (settings == null) {
                    settings = new HashMap<>();
                }
            } catch (IOException e) {
                LOGGER.error("Failed to load config", e);
                settings = new HashMap<>();
            }
        }
        setDefaults();
    }

    private void setDefaults() {
        // Core settings
        settings.putIfAbsent("firstBoot", true);
        settings.putIfAbsent("interface", "cli");
        settings.putIfAbsent("lastLoginId", null);
        settings.putIfAbsent("lastUsername", null);
        settings.putIfAbsent("defaultUserId", "default");
        settings.putIfAbsent("dataDirectory", "./data");
        
        // API keys
        settings.putIfAbsent("apiKeys", new HashMap<String, String>());
        Map<String, String> apiKeys = (Map<String, String>) settings.get("apiKeys");
        apiKeys.putIfAbsent("spotify", "");
        apiKeys.putIfAbsent("lastfm", "");
        apiKeys.putIfAbsent("musicbrainz", "");
        
        // UI preferences
        settings.putIfAbsent("uiPreferences", new HashMap<String, Object>());
        Map<String, Object> uiPrefs = (Map<String, Object>) settings.get("uiPreferences");
        uiPrefs.putIfAbsent("theme", "light");
        uiPrefs.putIfAbsent("fontScale", 1.0);
        uiPrefs.putIfAbsent("showNotifications", true);
        
        saveConfig();
    }

    public void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(settings, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }

    public Object get(String key) {
        return settings.get(key);
    }

    public void set(String key, Object value) {
        settings.put(key, value);
        saveConfig();
    }

    public boolean isFirstBoot() {
        // Convert to primitive boolean to handle null case
        Object value = settings.get("firstBoot");
        return value == null || Boolean.TRUE.equals(value);
    }

    public void setFirstBootCompleted() {
        set("firstBoot", false);
        saveConfig(); // Ensure it's saved immediately
        LOGGER.info("First boot completed, configuration saved");
    }

    public String getInterface() {
        return (String) settings.getOrDefault("interface", "cli");
    }

    public void setLastLogin(String userId, String username, String email) {
        set("email", email);
        set("lastLoginId", userId);
        set("lastUsername", username);
    }

    public Map<String, String> getLastLogin() {
        Map<String, String> login = new HashMap<>();
        login.put("userId", (String) settings.get("lastLoginId"));
        login.put("username", (String) settings.get("lastUsername"));
        return login;
    }

    public String getApiKey(String service) {
        Map<String, String> apiKeys = (Map<String, String>) settings.getOrDefault("apiKeys", new HashMap<>());
        return apiKeys.getOrDefault(service, "");
    }

    public void setApiKey(String service, String key) {
        Map<String, String> apiKeys = (Map<String, String>) settings.getOrDefault("apiKeys", new HashMap<>());
        apiKeys.put(service, key);
        settings.put("apiKeys", apiKeys);
        saveConfig();
    }

    public Object getUiPreference(String key) {
        Map<String, Object> uiPrefs = (Map<String, Object>) settings.getOrDefault("uiPreferences", new HashMap<>());
        return uiPrefs.get(key);
    }

    public void setUiPreference(String key, Object value) {
        Map<String, Object> uiPrefs = (Map<String, Object>) settings.getOrDefault("uiPreferences", new HashMap<>());
        uiPrefs.put(key, value);
        settings.put("uiPreferences", uiPrefs);
        saveConfig();
    }

    public void clearLogin() {
        set("lastLoginId", null);
        set("lastUsername", null);
        LOGGER.info("Login data cleared from configuration");
    }
}
