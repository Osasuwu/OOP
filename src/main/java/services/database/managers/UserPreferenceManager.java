package services.database.managers;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import models.Artist;
import models.Genre;
import models.Mood;
import models.Song;
import models.User;
import services.database.MusicDatabaseManager;

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
        
        // Use the get_user_preferences stored procedure
        String sql = "SELECT * FROM get_user_preferences(?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(user.getId()));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    String itemName = rs.getString("item_name");
                    UUID itemId = rs.getObject("item_id", UUID.class);
                    double score = rs.getDouble("score");
                    
                    // Group by type
                    switch (type) {
                        case "artist":
                            Artist artist = new Artist(itemName);
                            artist.setId(itemId.toString());
                            artist.setScore(score);
                            if(!preferences.containsKey("favorite_artist")) {
                                preferences.put("favorite_artist", new ArrayList<>());
                            }
                            preferences.get("favorite_artist").add(artist);
                            break;
                        case "song":
                            Song song = new Song(itemName, null);
                            song.setId(itemId.toString());
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
                            preferences.get(type).add(itemId.toString());
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
        
        // Use the save_user_preference stored procedure
        String sql = "SELECT save_user_preference(?, ?, ?, ?, ?)";

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
                            stmt.setObject(4, UUID.fromString(favoriteArtist.getId()));
                            stmt.setDouble(5, favoriteArtist.getScore());
                            stmt.addBatch();
                        }
                        break;
                    case "favorite_songs":
                        for (Object obj : entry.getValue()) {
                            Song favoriteSong = (Song) obj;
                            stmt.setObject(1, userId);
                            stmt.setString(2, "song");
                            stmt.setString(3, favoriteSong.getTitle());
                            stmt.setObject(4, UUID.fromString(favoriteSong.getId()));
                            stmt.setDouble(5, favoriteSong.getScore());
                            stmt.addBatch();
                        }
                        break;
                    case "favorite_genres":
                        for (Object obj : entry.getValue()) {
                            Genre favoriteGenre = (Genre) obj;
                            stmt.setObject(1, userId);
                            stmt.setString(2, "genre");
                            stmt.setString(3, favoriteGenre.getName());
                            stmt.setObject(4, null);
                            stmt.setDouble(5, favoriteGenre.getScore());
                            stmt.addBatch();
                        }
                        break;
                    case "favorite_moods":
                        for (Object obj : entry.getValue()) {
                            Mood favoriteMood = (Mood) obj;
                            stmt.setObject(1, userId);
                            stmt.setString(2, "mood");
                            stmt.setString(3, favoriteMood.getName());
                            stmt.setObject(4, null);
                            stmt.setDouble(5, favoriteMood.getScore());
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
                            stmt.setObject(4, null); // Assuming no ID for custom types
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
