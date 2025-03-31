package services.database.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.io.*;
import java.nio.file.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import models.User;
import models.Song;
import models.Artist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserPreferenceManager extends BaseDatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPreferenceManager.class);
    private final Gson gson = new Gson();
    private final Path storageDir;

    public UserPreferenceManager(boolean isOnline, User user) {
        super(isOnline, user);
        this.storageDir = Paths.get(getOfflineStoragePath(), "preferences");
        if (isOfflineMode()) {
            try {
                Files.createDirectories(storageDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create offline storage directory", e);
            }
        }
    }

    public Map<String, Object> getUserPreferences(Connection conn) throws SQLException {
        if (isOfflineMode()) {
            return getUserPreferencesOffline();
        }
        
        Map<String, Object> preferences = new HashMap<>();
        String sql = "SELECT type, item_id, score FROM user_preferences WHERE user_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(user.getId()));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    String itemId = rs.getString("item_id");
                    double score = rs.getDouble("score");
                    
                    // Group by type
                    if (type.equals("genre")) {
                        @SuppressWarnings("unchecked")
                        List<String> genres = (List<String>) preferences.getOrDefault("favorite_genres", new ArrayList<String>());
                        genres.add(itemId);
                        preferences.put("favorite_genres", genres);
                    } else {
                        // Store other preferences by their type and ID
                        preferences.put(type + "_" + itemId, score);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to retrieve user preferences from database", e);
            throw e;
        }
        
        return preferences;
    }

    private Map<String, Object> getUserPreferencesOffline() {
        Path filePath = storageDir.resolve("preferences.json");
        if (Files.exists(filePath)) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
                return gson.fromJson(reader, new TypeToken<Map<String, Object>>(){}.getType());
            } catch (IOException e) {
                LOGGER.error("Failed to load preferences from file", e);
            }
        }
        return new HashMap<>();
    }

    public void updateUserPreferences(Connection conn, Map<String, Object> preferences) throws SQLException {
        if (isOfflineMode()) {
            updateUserPreferencesOffline(preferences);
            return;
        }
        
        String sql = "INSERT INTO user_preferences (user_id, item_id, type, score) VALUES (?, ?, ?, ?) ON CONFLICT (user_id, item_id, type) DO UPDATE SET score = EXCLUDED.score";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Object> entry : preferences.entrySet()) {
                String entryKey = entry.getKey();
                switch (entryKey) {
                    case "favorite_artist":
                        Artist favoriteArtist = (Artist) entry.getValue();
                        // Convert user ID to UUID
                        stmt.setObject(1, UUID.fromString(user.getId()));
                        // Convert artist ID to UUID
                        stmt.setObject(2, UUID.fromString(favoriteArtist.getId()));
                        stmt.setString(3, "artist");
                        stmt.setDouble(4, 1.0); // Example score
                        stmt.addBatch();
                        break;

                    case "favorite_song":
                        Song favoriteSong = (Song) entry.getValue();
                        // Convert user ID to UUID
                        stmt.setObject(1, UUID.fromString(user.getId()));
                        // Convert song ID to UUID
                        stmt.setObject(2, UUID.fromString(favoriteSong.getId()));
                        stmt.setString(3, "song");
                        stmt.setDouble(4, 1.0); // Example score
                        stmt.addBatch();
                        break;

                    default:
                        // For other types like genres, we might need different handling
                        if (entry.getValue() instanceof String) {
                            // Convert user ID to UUID
                            stmt.setObject(1, UUID.fromString(user.getId()));
                            stmt.setString(2, (String) entry.getValue());
                            stmt.setString(3, entryKey);
                            stmt.setDouble(4, 1.0);
                            stmt.addBatch();
                        }
                        break;
                }
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("Failed to update user preferences in database", e);
            throw e;
        }
    }

    private void updateUserPreferencesOffline(Map<String, Object> preferences) {
        try (Writer writer = Files.newBufferedWriter(storageDir.resolve("preferences.json"))) {
            gson.toJson(preferences, writer);
            LOGGER.info("Saved user preferences in offline mode");
        } catch (IOException e) {
            LOGGER.error("Failed to save preferences to file", e);
        }
    }

    public void saveGenres(Connection conn, List<String> favoriteGenres) throws SQLException {
        if (isOfflineMode()) {
            saveGenresOffline(favoriteGenres);
            return;
        }
        // ...existing saveGenres code...
    }

    private void saveGenresOffline(List<String> favoriteGenres) {
        Map<String, Object> preferences = getUserPreferencesOffline();
        preferences.put("favorite_genres", favoriteGenres);
        updateUserPreferencesOffline(preferences);
    }
}
