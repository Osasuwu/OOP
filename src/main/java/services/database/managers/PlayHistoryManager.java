package services.database.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import models.Artist;
import models.PlayHistory;
import models.Song;
import models.User;
import models.UserMusicData;

public class PlayHistoryManager extends BaseDatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(PlayHistoryManager.class);
    private final Gson gson;
    private final Path storageDir;

    public PlayHistoryManager(boolean isOnline, User user) {
        super(isOnline, user);
        this.storageDir = Paths.get(getOfflineStoragePath(), "history");
        // Use custom serializer/deserializer for Date objects
        this.gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
            .create();
            
        if (isOfflineMode()) {
            try {
                Files.createDirectories(storageDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create offline storage directory", e);
            }
        }
    }

    /**
     * Saves a list of play history records to the database
     * Uses batch processing for efficiency
     * 
     * @param connection Database connection
     * @param playHistories List of play histories to save
     * @param user The user who owns these play histories
     * @throws SQLException If a database error occurs
     */
    public void savePlayHistory(Connection connection, List<PlayHistory> playHistories, User user) throws SQLException {
        if (playHistories == null || playHistories.isEmpty()) {
            return;
        }

        if (isOfflineMode()) {
            savePlayHistoryOffline(playHistories);
            return;
        }

        // Use the save_play_history stored procedure
        String sql = "SELECT save_play_history(?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int count = 0;
            int totalRecords = playHistories.size();
            int batchSize = 100;
            
            for (PlayHistory playHistory : playHistories) {
                stmt.setObject(1, UUID.fromString(user.getId()));
                stmt.setObject(2, UUID.fromString(playHistory.getSong().getId()));
                stmt.setTimestamp(3, new Timestamp(playHistory.getTimestamp().getTime()));
                stmt.addBatch();
                count++;
                
                // Execute batch when it reaches the batch size
                if (count % batchSize == 0) {
                    stmt.executeBatch();
                    LOGGER.info("Saved {}/{} play history records", count, totalRecords);
                }
            }
            
            // Execute remaining records
            if (count % batchSize != 0) {
                stmt.executeBatch();
            }
            
            LOGGER.info("Saved all {}/{} play history records", totalRecords, totalRecords);
        } catch (SQLException e) {
            LOGGER.error("Error saving play history: {}", e.getMessage());
            throw e;
        }
    }

    private void savePlayHistoryOffline(List<PlayHistory> playHistory) {
        if (playHistory == null || playHistory.isEmpty()) {
            return;
        }

        List<PlayHistory> existingHistory = loadPlayHistoryFromFile();
        // Combine existing history with new entries
        Set<PlayHistory> uniqueHistory = new HashSet<>(existingHistory);
        uniqueHistory.addAll(playHistory);
        
        try (Writer writer = Files.newBufferedWriter(storageDir.resolve("play_history.json"))) {
            gson.toJson(new ArrayList<>(uniqueHistory), writer);
            LOGGER.info("Saved {} play history entries in offline mode", playHistory.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save play history to file", e);
        }
    }

    private List<PlayHistory> loadPlayHistoryFromFile() {
        Path filePath = storageDir.resolve("play_history.json");
        if (Files.exists(filePath)) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
                List<PlayHistory> history = gson.fromJson(reader, new TypeToken<List<PlayHistory>>(){}.getType());
                return history != null ? history : new ArrayList<>();
            } catch (IOException e) {
                LOGGER.error("Failed to load play history from file", e);
            }
        }
        return new ArrayList<>();
    }
    
    public List<PlayHistory> loadPlayHistory(Connection conn, User user) throws SQLException {
    if (!isOnline) {
        return loadPlayHistoryFromFile();
    }
    
    List<PlayHistory> history = new ArrayList<>();
    String sql = "SELECT * FROM get_play_history_raw(?)";
    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
        stmt.setObject(1, UUID.fromString(user.getId()));
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                // You'll need to adjust this to match your database schema
                PlayHistory entry = new PlayHistory(null); // Replace null with actual song
                entry.setTimestamp(rs.getTimestamp("timestamp"));
                entry.setDurationMs(rs.getLong("duration_ms"));
                entry.setCompleted(rs.getBoolean("completed"));
                entry.setSource(rs.getString("source"));
                history.add(entry);
            }
        }
    }
    return history;
}

/**
     * Gets the user's top songs based on play history.
     * 
     * @param conn Database connection
     * @param user The user whose top songs are requested
     * @param limit Maximum number of songs to return
     * @return List of the user's most played songs
     * @throws SQLException If a database error occurs
     */
    public List<Song> getUserTopSongs(Connection conn, User user, int limit) throws SQLException {
        if (isOfflineMode()) {
            return getUserTopSongsOffline(limit);
        }

        List<Song> topSongs = new ArrayList<>();
        
        // Use the get_user_top_songs stored procedure
        String sql = "SELECT * FROM get_user_top_songs(?, ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, UUID.fromString(user.getId()));
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Song song = new Song(rs.getString("title"), rs.getString("artist_name"));
                    song.setId(rs.getString("id"));
                    song.setSpotifyId(rs.getString("spotify_id"));
                    song.setAlbumName(rs.getString("album_name"));
                    song.setPopularity(rs.getInt("popularity"));
                    song.setDurationMs(rs.getLong("duration_ms"));
                    song.setExplicit(rs.getBoolean("is_explicit"));
                    
                    // Create artist object
                    Artist artist = new Artist(rs.getString("artist_name"));
                    artist.setId(rs.getString("artist_id"));
                    artist.setImageUrl(rs.getString("artist_image_url"));
                    
                    // Set artist in song
                    song.setArtist(artist);
                    
                    // Add to result list
                    topSongs.add(song);
                }
            }
        }
        
        LOGGER.debug("Found {} top songs for user {}", topSongs.size(), user.getName());
        
        // If no top songs found (perhaps new user), return empty list
        return topSongs;
    }
    
    /**
     * Gets the user's top songs from offline storage
     * 
     * @param limit Maximum number of songs to return
     * @return List of the user's most played songs from offline storage
     */
    private List<Song> getUserTopSongsOffline(int limit) {
        List<PlayHistory> history = loadPlayHistoryFromFile();
        
        if (history.isEmpty()) {
            return new ArrayList<>();
        }
        
        // Count plays per song
        Map<String, Song> songsById = new HashMap<>();
        Map<String, Integer> playCounts = new HashMap<>();
        
        for (PlayHistory entry : history) {
            if (entry.getSong() != null) {
                String songId = entry.getSong().getId();
                songsById.putIfAbsent(songId, entry.getSong());
                playCounts.put(songId, playCounts.getOrDefault(songId, 0) + 1);
            }
        }
        
        // Sort by play count
        return playCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(limit)
            .map(entry -> songsById.get(entry.getKey()))
            .collect(Collectors.toList());
    }
}
