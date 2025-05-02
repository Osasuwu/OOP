package models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User preferences for playlist generation
 */
public class UserPreferences {
    private Map<String, List<Object>> preferences; // Key: preference name, Value: list of values

    public UserPreferences(Map<String, List<Object>> preferences) {
        this.preferences = preferences;
    }

    public Map<String, List<Object>> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String, List<Object>> preferences) {
        this.preferences = preferences;
    }

    public List<Object> getPreference(String key) {
        return preferences.getOrDefault(key, new ArrayList<>());
    }

    public void setPreference(String key, List<Object> values) {
        preferences.put(key, values);
    }

    public void addPreference(String key, Object value) {
        preferences.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    public void removePreference(String key, Object value) {
        List<Object> values = preferences.get(key);
        if (values != null) {
            values.remove(value);
            if (values.isEmpty()) {
                preferences.remove(key);
            }
        }
    }
}