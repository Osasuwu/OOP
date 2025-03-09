package services.database;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.*;
import org.json.JSONObject;
import org.json.JSONArray;

public class OfflineDataManager {
    private static final String CACHE_DIR = "cache";
    private static final String CACHE_FILE = "music_cache.json";
    private static final int CACHE_SIZE_LIMIT = 1000;

    private JSONObject cache;
    private String userId;

    public OfflineDataManager(String userId) {
        this.userId = userId;
        loadCache();
    }

    private void loadCache() {
        File cacheFile = new File(CACHE_DIR, CACHE_FILE);
        if (cacheFile.exists()) {
            try {
                String content = new String(Files.readAllBytes(cacheFile.toPath()));
                cache = new JSONObject(content);
            } catch (Exception e) {
                cache = new JSONObject();
            }
        } else {
            cache = new JSONObject();
            initializeCacheStructure();
        }
    }

    private void initializeCacheStructure() {
        cache.put("users", new JSONObject());
        cache.put("artists", new JSONObject());
        cache.put("songs", new JSONObject());
        cache.put("genres", new JSONObject());
        cache.put("playlists", new JSONObject());
        saveCache();
    }

    public void saveCache() {
        try {
            File cacheDir = new File(CACHE_DIR);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(new File(CACHE_DIR, CACHE_FILE))) {
                writer.write(cache.toString(2));
            }
        } catch (Exception e) {
            System.err.println("Error saving cache: " + e.getMessage());
        }
    }

    public Map<String, Object> getUserPreferences() {
        JSONObject users = cache.getJSONObject("users");
        if (users.has(userId)) {
            return jsonToMap(users.getJSONObject(userId));
        }
        return new HashMap<>();
    }

    public void updateUserPreferences(Map<String, Object> preferences) {
        JSONObject users = cache.getJSONObject("users");
        if (!users.has(userId)) {
            users.put(userId, new JSONObject());
        }
        JSONObject userData = users.getJSONObject(userId);
        mapToJson(preferences, userData);
        saveCache();
    }

    private Map<String, Object> jsonToMap(JSONObject json) {
        Map<String, Object> map = new HashMap<>();
        for (String key : json.keySet()) {
            Object value = json.get(key);
            if (value instanceof JSONObject) {
                map.put(key, jsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.put(key, jsonArrayToList((JSONArray) value));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private List<Object> jsonArrayToList(JSONArray array) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONObject) {
                list.add(jsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                list.add(jsonArrayToList((JSONArray) value));
            } else {
                list.add(value);
            }
        }
        return list;
    }

    private void mapToJson(Map<String, Object> map, JSONObject json) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                JSONObject nestedJson = new JSONObject();
                mapToJson((Map<String, Object>) value, nestedJson);
                json.put(key, nestedJson);
            } else if (value instanceof List) {
                json.put(key, new JSONArray((List<?>) value));
            } else {
                json.put(key, value);
            }
        }
    }
} 