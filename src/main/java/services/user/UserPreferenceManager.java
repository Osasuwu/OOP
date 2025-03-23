package services.user;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
    private final Map<String, String> preferences;

    /**
     * Constructor initializes user preferences.
     */
    public UserPreferenceManager() {
        this.localStorageManager = new LocalStorageManager(PREFERENCES_FILE);
        this.cloudSyncService = new CloudSyncService();
        this.logger = Logger.getInstance();
        this.preferences = new HashMap<>();

        loadPreferences();
    }

    /**
     * Sets a user preference and saves it.
     *
     * @param key   The preference key (e.g., "theme", "volume", "favoriteGenre").
     * @param value The value to be saved.
     */
    public void setPreference(String key, String value) {
        preferences.put(key, value);
        savePreferences();
        logger.info("User preference updated: " + key + " = " + value);
    }

    /**
     * Retrieves a user preference.
     *
     * @param key The preference key.
     * @return The preference value, or an empty Optional if not found.
     */
    public Optional<String> getPreference(String key) {
        return Optional.ofNullable(preferences.get(key));
    }

    /**
     * Removes a specific user preference.
     *
     * @param key The preference key to remove.
     * @return True if removed successfully, false otherwise.
     */
    public boolean removePreference(String key) {
        if (preferences.containsKey(key)) {
            preferences.remove(key);
            savePreferences();
            logger.info("User preference removed: " + key);
            return true;
        }
        logger.warning("Attempted to remove non-existent preference: " + key);
        return false;
    }

    /**
     * Clears all user preferences.
     */
    public void clearPreferences() {
        preferences.clear();
        savePreferences();
        logger.info("All user preferences cleared.");
    }

    /**
     * Saves user preferences to local storage and syncs with the cloud.
     */
    private void savePreferences() {
        localStorageManager.savePreferences(preferences);
        cloudSyncService.syncPreferences(preferences);
    }

    /**
     * Loads user preferences from local storage.
     */
    private void loadPreferences() {
        Map<String, String> loadedPreferences = localStorageManager.loadPreferences();
        if (loadedPreferences != null) {
            preferences.putAll(loadedPreferences);
            logger.info("User preferences loaded: " + preferences.size() + " settings.");
        } else {
            logger.warning("No saved preferences found. Using defaults.");
        }
    }

    /**
     * Fetches all user preferences.
     *
     * @return A copy of the preferences map.
     */
    public Map<String, String> getAllPreferences() {
        return new HashMap<>(preferences);
    }
    }