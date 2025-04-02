package services.database.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.io.*;
import java.nio.file.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import models.PlayHistory;
import models.User;

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

        // Ensure we have a valid batch size - too large can cause memory issues
        int batchSize = 100;
        int totalRecords = playHistories.size();
        
        String sql = "INSERT INTO play_history (user_id, song_id, listened_at) VALUES (?, ?, ?) " +
                     "ON CONFLICT (user_id, song_id, listened_at) DO NOTHING";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            int count = 0;
            
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
}
