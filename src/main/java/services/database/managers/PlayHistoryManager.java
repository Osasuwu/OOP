package services.database.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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

    public void savePlayHistory(Connection conn, List<PlayHistory> playHistory) throws SQLException {
        if (isOfflineMode()) {
            savePlayHistoryOffline(playHistory);
            return;
        }
        
        String sql = """
            INSERT INTO play_history (user_id, song_id, listened_at)
            VALUES (?, ?, ?)
            ON CONFLICT (user_id, song_id, listened_at) DO NOTHING
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (PlayHistory entry : playHistory) {
                if (entry.getSong() == null || entry.getTimestamp() == null) {
                    continue;
                }
                
                // Convert user ID to UUID
                stmt.setObject(1, UUID.fromString(user.getId()));
                // Convert song ID to UUID
                stmt.setObject(2, UUID.fromString(entry.getSong().getId()));
                stmt.setObject(3, new java.sql.Timestamp(entry.getTimestamp().getTime()));
                stmt.addBatch();
            }
            stmt.executeBatch();
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
