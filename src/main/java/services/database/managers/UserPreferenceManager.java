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
import services.database.MusicDatabaseManager;
import models.Song;
import models.Artist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserPreferenceManager extends BaseDatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserPreferenceManager.class);
    private final Gson gson = new Gson();
    private final Path storageDir;
    private MusicDatabaseManager dbManager;


    public UserPreferenceManager(boolean isOnline, User user, MusicDatabaseManager dbManager) {
        super(isOnline, user);
        this.dbManager = dbManager; // Use the passed instance instead of creating a new one
        this.storageDir = Paths.get(getOfflineStoragePath(), "preferences");
        if (isOfflineMode()) {
            try {
                Files.createDirectories(storageDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create offline storage directory", e);
            }
        }
    }

    /**
     * Retrieves the current user preferences from the database.
     * @param conn The database connection.
     * @return A map of user preferences.
     * @throws SQLException If an error occurs while accessing the database.
     */
    public Map<String, List<Object>> getCurrentUserPreferences(Connection conn) throws SQLException {
        if (isOfflineMode()) {
            return getCurrentUserPreferencesOffline();
        }
        
        Map<String, List<Object>> preferences = new HashMap<>();
        String sql = "SELECT type, item_name, item_id, score FROM user_preferences WHERE user_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(user.getId()));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    String itemName = rs.getString("item_name");
                    String itemId = rs.getString("item_id");
                    double score = rs.getDouble("score");
                    
                    // Group by type
                    switch (type) {
                        case "artist":
                            Artist artist = new Artist(itemName);
                            artist.setId(itemId);
                            artist.setScore(score);
                            if(!preferences.containsKey("favorite_artist")) {
                                preferences.put("favorite_artist", new ArrayList<>());
                            }
                            preferences.get("favorite_artist").add(artist);
                            break;
                        case "song":
                            Song song = new Song(itemName, null);
                            song.setId(itemId);
                            song.setScore(score);
                            song.setArtist(dbManager.getArtistBySong(song));
                            if(!preferences.containsKey("favorite_song")) {
                                preferences.put("favorite_song", new ArrayList<>());
                            }
                            preferences.get("favorite_song").add(song);
                            break;
                        case "genre":
                            String genre = itemName;
                            if(!preferences.containsKey("favorite_genre")) {
                                preferences.put("favorite_genre", new ArrayList<>());
                            }
                            preferences.get("favorite_genre").add(genre);
                            break;
                        case "mood":
                            if (!preferences.containsKey("favorite_mood")) {
                                preferences.put("favorite_mood", new ArrayList<>());
                            }
                            preferences.get("favorite_mood").add(itemName);
                            break;
                        default:
                            // Handle other custom types
                            if (!preferences.containsKey(type)) {
                                preferences.put(type, new ArrayList<>());
                            }
                            preferences.get(type).add(itemId);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to retrieve user preferences from database", e);
            throw e;
        }
        
        return preferences;
    }

    private Map<String, List<Object>> getCurrentUserPreferencesOffline() {
        Map<String, List<Object>> preferences = new HashMap<>();
        Path filePath = storageDir.resolve("preferences.json");
        
        if (Files.exists(filePath)) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
                preferences = gson.fromJson(reader, new TypeToken<Map<String, List<Object>>>() {}.getType());
            } catch (IOException e) {
                LOGGER.error("Failed to read user preferences from offline file", e);
            }
        } else {
            LOGGER.warn("No offline preferences file found for user: " + user.getId());
        }
        
        return preferences;
        }

    public void updateCurrentUserPreferences(Connection conn, Map<String, List<Object>> preferences) throws SQLException {
        if (isOfflineMode()) {
            updateCurrentUserPreferencesOffline(preferences);
            return;
        }
        
        String sql = """
            INSERT INTO user_preferences (user_id, type, item_name, item_id, score)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id, item_name, item_id, type) DO UPDATE SET score = EXCLUDED.score
        """;

        UUID userId = UUID.fromString(user.getId());

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Map.Entry<String, List<Object>> entry : preferences.entrySet()) {
                String entryKey = entry.getKey();
                switch (entryKey) {
                    case "favorite_artists":
                        for (Object obj : entry.getValue()) {
                            Artist favoriteArtist = (Artist) obj;
                            stmt.setObject(1, userId);
                            stmt.setString(2, "artist");
                            stmt.setString(3, favoriteArtist.getName());
                            stmt.setString(4, favoriteArtist.getId());
                            stmt.setDouble(5, 1.0); // Example score
                            stmt.addBatch();
                        }
                        break;
                    case "favorite_songs":
                        for (Object obj : entry.getValue()) {
                            Song favoriteSong = (Song) obj;
                            stmt.setObject(1, userId);
                            stmt.setString(2, "song");
                            stmt.setString(3, favoriteSong.getTitle());
                            stmt.setString(4, favoriteSong.getId());
                            stmt.setDouble(5, 1.0); // Example score
                            stmt.addBatch();
                        }
                        break;
                    case "favorite_genres":
                        for (Object obj : entry.getValue()) {
                            String favoriteGenre = (String) obj;
                            stmt.setObject(1, userId);
                            stmt.setString(2, "genre");
                            stmt.setString(3, favoriteGenre);
                            stmt.setString(4, null);
                            stmt.setDouble(5, 1.0); // Example score
                            stmt.addBatch();
                        }
                        break;
                    case "favorite_moods":
                        for (Object obj : entry.getValue()) {
                            String favoriteMood = (String) obj;
                            stmt.setObject(1, userId);
                            stmt.setString(2, "mood");
                            stmt.setString(3, favoriteMood);
                            stmt.setString(4, null);
                            stmt.setDouble(5, 1.0); // Example score
                            stmt.addBatch();
                        }
                        break;

                    default:
                        // Handle other custom types
                        for (Object obj : entry.getValue()) {
                            String itemName = (String) obj;
                            stmt.setObject(1, userId);
                            stmt.setString(2, entryKey);
                            stmt.setString(3, itemName);
                            stmt.setString(4, null); // Assuming no ID for custom types
                            stmt.setDouble(5, 1.0); // Example score
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

    private void updateCurrentUserPreferencesOffline(Map<String, List<Object>> preferences) {
        Path filePath = storageDir.resolve("preferences.json");
        
        try (Writer writer = Files.newBufferedWriter(filePath)) {
            gson.toJson(preferences, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to write user preferences to offline file", e);
        }
    }
}
