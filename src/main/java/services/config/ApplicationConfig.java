package services.config;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for storing and managing application configuration parameters
 */
public class ApplicationConfig {
    private boolean firstRun = true;
    private String defaultUserId = "default";
    private String dataDirectory = "./data";
    private String databaseType = "sqlite";
    private String databasePath = "./data/musicdb.sqlite";
    private boolean offlineMode = false;
    private boolean collectAnonymousStats = true;
    private String playerType = "system";
    private Map<String, String> apiKeys = new HashMap<>();
    private List<String> importPaths = new ArrayList<>();
    private Map<String, Object> uiPreferences = new HashMap<>();

    public ApplicationConfig() {
        // Initialize default API keys (empty)
        apiKeys.put("spotify", "");
        apiKeys.put("lastfm", "");
        apiKeys.put("musicbrainz", "");
        
        // Initialize default UI preferences
        uiPreferences.put("theme", "light");
        uiPreferences.put("fontScale", 1.0);
        uiPreferences.put("showNotifications", true);
    }
    
    /**
     * Create ApplicationConfig from JSON string
     */
    public static ApplicationConfig fromJson(String jsonString) {
        JSONObject json = new JSONObject(jsonString);
        ApplicationConfig config = new ApplicationConfig();
        
        config.firstRun = json.optBoolean("firstRun", true);
        config.defaultUserId = json.optString("defaultUserId", "default");
        config.dataDirectory = json.optString("dataDirectory", "./data");
        config.databaseType = json.optString("databaseType", "sqlite");
        config.databasePath = json.optString("databasePath", "./data/musicdb.sqlite");
        config.offlineMode = json.optBoolean("offlineMode", false);
        config.collectAnonymousStats = json.optBoolean("collectAnonymousStats", true);
        config.playerType = json.optString("playerType", "system");
        
        // Load API keys
        if (json.has("apiKeys")) {
            JSONObject apiKeysJson = json.getJSONObject("apiKeys");
            for (String key : apiKeysJson.keySet()) {
                config.apiKeys.put(key, apiKeysJson.getString(key));
            }
        }
        
        // Load import paths
        if (json.has("importPaths")) {
            config.importPaths.clear();
            for (Object path : json.getJSONArray("importPaths")) {
                config.importPaths.add(path.toString());
            }
        }
        
        // Load UI preferences
        if (json.has("uiPreferences")) {
            JSONObject uiJson = json.getJSONObject("uiPreferences");
            for (String key : uiJson.keySet()) {
                config.uiPreferences.put(key, uiJson.get(key));
            }
        }
        
        return config;
    }
    
    /**
     * Convert to JSON string
     */
    public String toJson() {
        JSONObject json = new JSONObject();
        
        json.put("firstRun", firstRun);
        json.put("defaultUserId", defaultUserId);
        json.put("dataDirectory", dataDirectory);
        json.put("databaseType", databaseType);
        json.put("databasePath", databasePath);
        json.put("offlineMode", offlineMode);
        json.put("collectAnonymousStats", collectAnonymousStats);
        json.put("playerType", playerType);
        json.put("apiKeys", new JSONObject(apiKeys));
        json.put("importPaths", importPaths);
        json.put("uiPreferences", new JSONObject(uiPreferences));
        
        return json.toString(2);
    }
    
    // Getters and setters
    
    public boolean isFirstRun() {
        return firstRun;
    }
    
    public void setFirstRun(boolean firstRun) {
        this.firstRun = firstRun;
    }
    
    public String getDefaultUserId() {
        return defaultUserId;
    }
    
    public void setDefaultUserId(String defaultUserId) {
        this.defaultUserId = defaultUserId;
    }
    
    public String getDataDirectory() {
        return dataDirectory;
    }
    
    public void setDataDirectory(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }
    
    public String getDatabaseType() {
        return databaseType;
    }
    
    public void setDatabaseType(String databaseType) {
        this.databaseType = databaseType;
    }
    
    public String getDatabasePath() {
        return databasePath;
    }
    
    public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
    }
    
    public boolean isOfflineMode() {
        return offlineMode;
    }
    
    public void setOfflineMode(boolean offlineMode) {
        this.offlineMode = offlineMode;
    }
    
    public boolean isCollectAnonymousStats() {
        return collectAnonymousStats;
    }
    
    public void setCollectAnonymousStats(boolean collectAnonymousStats) {
        this.collectAnonymousStats = collectAnonymousStats;
    }
    
    public String getPlayerType() {
        return playerType;
    }
    
    public void setPlayerType(String playerType) {
        this.playerType = playerType;
    }
    
    public Map<String, String> getApiKeys() {
        return apiKeys;
    }
    
    public void setApiKeys(Map<String, String> apiKeys) {
        this.apiKeys = apiKeys;
    }
    
    public String getApiKey(String service) {
        return apiKeys.getOrDefault(service, "");
    }
    
    public void setApiKey(String service, String key) {
        apiKeys.put(service, key);
    }
    
    public List<String> getImportPaths() {
        return importPaths;
    }
    
    public void setImportPaths(List<String> importPaths) {
        this.importPaths = importPaths;
    }
    
    public void addImportPath(String path) {
        if (!importPaths.contains(path)) {
            importPaths.add(path);
        }
    }
    
    public Map<String, Object> getUiPreferences() {
        return uiPreferences;
    }
    
    public void setUiPreferences(Map<String, Object> uiPreferences) {
        this.uiPreferences = uiPreferences;
    }
    
    public Object getUiPreference(String key) {
        return uiPreferences.getOrDefault(key, null);
    }
    
    public void setUiPreference(String key, Object value) {
        uiPreferences.put(key, value);
    }
}