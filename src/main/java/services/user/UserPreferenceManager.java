package services.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import services.network.CloudSyncService;
import services.storage.LocalStorageManager;
import utils.Logger;

/**
 * Manages user preferences, including playback settings, theme choices, and personalized recommendations.
 * Supports local storage and cloud synchronization.
 */
public class UserPreferenceManager {

    private static final String PREFERENCES_FILE = "user_preferences.json";

    private final LocalStorageManager localStorageManager;
    private final CloudSyncService cloudSyncService;
    private final Logger logger;
    // Changed to store objects of various types.
    private final Map<String, Object> preferences;
    // For tracking which preferences have changed.
    private final Set<String> dirtyPreferences;
    // For notifying components of changes in preferences.
    private final List<PreferenceChangeListener> listeners;
    // Default preferences provided if none exist.
    private static final Map<String, Object> defaultPreferences = Map.of(
        "theme", "light",
        "volume", "80",
        "favoriteGenre", "pop"
    );
    // For multi-user profiles.
    private String currentUser = "default";
    // Scheduled executor for batch saving to reduce disk I/O.
    private final ScheduledExecutorService scheduler;

    /**
     * Constructor initializes user preferences.
     */
    public UserPreferenceManager() {
        this.localStorageManager = new LocalStorageManager(PREFERENCES_FILE);
        this.cloudSyncService = new CloudSyncService();
        this.logger = Logger.getInstance();
        this.preferences = new HashMap<>();
        this.dirtyPreferences = new HashSet<>();
        this.listeners = new ArrayList<>();
        // Schedule saving preferences every 10 seconds.
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.scheduler.scheduleAtFixedRate(this::savePreferences, 10, 10, TimeUnit.SECONDS);
        loadPreferences();
    }

    /**
     * Sets the active user profile.
     *
     * @param userId The user identifier.
     */
    public void setProfile(String userId) {
        this.currentUser = userId;
        loadPreferences();
    }

    /**
     * Sets a user preference and marks it for syncing. Supports any data type.
     *
     * @param key   The preference key (e.g., "theme", "volume", "favoriteGenre").
     * @param value The value to be saved.
     */
    public void setPreference(String key, Object value) {
        String compositeKey = currentUser + ":" + key;
        String encryptedValue = encrypt(value.toString());
        // Only update and flag if the value is changed.
        if (!encryptedValue.equals(preferences.get(compositeKey))) {
            preferences.put(compositeKey, encryptedValue);
            dirtyPreferences.add(compositeKey);
            notifyListeners(key, value);
        }
    }

    /**
     * Retrieves a user preference. Falls back to default if not present.
     *
     * @param key The preference key.
     * @return The preference value as a String, or empty if not present.
     */
    public Optional<String> getPreference(String key) {
        String compositeKey = currentUser + ":" + key;
        if (preferences.containsKey(compositeKey)) {
            return Optional.of(decrypt(preferences.get(compositeKey).toString()));
        } else if (defaultPreferences.containsKey(key)) {
            return Optional.of(defaultPreferences.get(key).toString());
        } else {
            return Optional.empty();
        }
    }

    /**
     * Retrieves an integer preference.
     *
     * @param key The preference key.
     * @return The integer value wrapped in an Optional.
     */
    public Optional<Integer> getIntPreference(String key) {
        try {
            return Optional.of(Integer.parseInt(getPreference(key).orElse("0")));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Removes a specific user preference.
     *
     * @param key The preference key to remove.
     * @return True if removed successfully, false otherwise.
     */
    public boolean removePreference(String key) {
        String compositeKey = currentUser + ":" + key;
        if (preferences.containsKey(compositeKey)) {
            preferences.remove(compositeKey);
            dirtyPreferences.add(compositeKey);
            notifyListeners(key, null);
            return true;
        }
        logger.warning("Attempted to remove non-existent preference: " + key);
        return false;
    }

    /**
     * Clears all user preferences for the current profile.
     */
    public void clearPreferences() {
        Set<String> keysToRemove = preferences.keySet().stream()
            .filter(k -> k.startsWith(currentUser + ":"))
            .collect(Collectors.toSet());
        for (String key : keysToRemove) {
            preferences.remove(key);
            dirtyPreferences.add(key);
        }
        notifyListeners("all", null);
    }

    /**
     * Saves user preferences to local storage and syncs only the changed ones with the cloud.
     * This method is scheduled to run periodically.
     */
    private void savePreferences() {
        // Convert preferences to a map of String values for storage.
        Map<String, String> stringPrefs = preferences.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().toString()
            ));
        localStorageManager.savePreferences(stringPrefs);
        if (!dirtyPreferences.isEmpty()) {
            Map<String, String> dirtyMap = preferences.entrySet().stream()
                .filter(entry -> dirtyPreferences.contains(entry.getKey()))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().toString()
                ));
            cloudSyncService.syncPreferences(dirtyMap);
            dirtyPreferences.clear();
        }
        logger.info("Preferences saved.");
    }

    /**
     * Loads user preferences from local storage for the current profile.
     */
    private void loadPreferences() {
        Map<String, String> loadedPreferences = localStorageManager.loadPreferences();
        if (loadedPreferences != null) {
            loadedPreferences.forEach((key, value) -> {
                if (key.startsWith(currentUser + ":")) {
                    preferences.put(key, value);
                }
            });
            logger.info("User preferences loaded: " + preferences.size() + " settings for user " + currentUser + ".");
        } else {
            logger.warning("No saved preferences found. Using defaults.");
        }
    }

    /**
     * Fetches all user preferences for the current profile.
     *
     * @return A copy of the preferences map.
     */
    public Map<String, Object> getAllPreferences() {
        return preferences.entrySet().stream()
            .filter(e -> e.getKey().startsWith(currentUser + ":"))
            .collect(Collectors.toMap(
                e -> e.getKey().substring((currentUser + ":").length()),
                Map.Entry::getValue
            ));
    }

    // -------- Preference Change Listener Support --------
    
    /**
     * Adds a listener to be notified of preference changes.
     *
     * @param listener The listener to add.
     */
    public void addListener(PreferenceChangeListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Removes a previously added listener.
     *
     * @param listener The listener to remove.
     */
    public void removeListener(PreferenceChangeListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Notifies all registered listeners of a preference change.
     *
     * @param key      The preference key that changed.
     * @param newValue The new value of the preference.
     */
    private void notifyListeners(String key, Object newValue) {
        for (PreferenceChangeListener listener : listeners) {
            listener.onPreferenceChanged(key, newValue);
        }
    }

    /**
     * Listener Interface for preference changes.
     */
    public interface PreferenceChangeListener {
        void onPreferenceChanged(String key, Object newValue);
    }

    // -------- Encrypted Preferences (Dummy AES encryption implementation) --------
    
    private String encrypt(String value) {
        // Replace this with a real AES encryption implementation.
        return "ENC:" + value;
    }
    
    private String decrypt(String value) {
        // Replace this with a real AES decryption implementation.
        if (value != null && value.startsWith("ENC:")) {
            return value.substring(4);
        }
        return value;
    }
}