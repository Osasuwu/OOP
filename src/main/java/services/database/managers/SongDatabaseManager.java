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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import models.Song;
import models.User;

public class SongDatabaseManager extends BaseDatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SongDatabaseManager.class);
    private final Gson gson = new Gson();
    private final Path storageDir;
    
    public SongDatabaseManager(boolean isOnline, User user) {
        super(isOnline, user);
        this.storageDir = Paths.get(getOfflineStoragePath(), "songs");
        if (isOfflineMode()) {
            try {
                Files.createDirectories(storageDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create offline storage directory", e);
            }
        }
    }

    /**
     * Saves a list of songs to the database or offline storage using batch for performance improvement.
     * @param conn The database connection.
     * @param songs The list of songs to save.
     * @throws SQLException If an error occurs while saving to the database.
     */
    public void saveSongs(Connection conn, List<Song> songs) throws SQLException {
        if (isOfflineMode()) {
            saveSongsOffline(songs);
            return;
        }

        String sql = """
            INSERT INTO songs (id, artist_id, spotify_id, title, spotify_link, popularity, album_name, release_date, preview_url, image_url, duration_ms, is_explicit)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                artist_id = EXCLUDED.artist_id,
                spotify_id = EXCLUDED.spotify_id,
                title = EXCLUDED.title,
                spotify_link = EXCLUDED.spotify_link,
                popularity = EXCLUDED.popularity,
                album_name = EXCLUDED.album_name,
                release_date = EXCLUDED.release_date,
                preview_url = EXCLUDED.preview_url,
                image_url = EXCLUDED.image_url,
                duration_ms = EXCLUDED.duration_ms,
                is_explicit = EXCLUDED.is_explicit            
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int batchCount = 0;
            for (Song song : songs) {
                if (song == null || song.getId() == null) {
                    continue;
                }
                // Convert String ID to UUID
                stmt.setObject(1, UUID.fromString(song.getId()));
                // Convert artist ID to UUID
                stmt.setObject(2, UUID.fromString(song.getArtist().getId()));
                stmt.setString(3, song.getSpotifyId());
                stmt.setString(4, song.getTitle());
                stmt.setString(5, song.getSpotifyLink());
                stmt.setInt(6, song.getPopularity());
                stmt.setString(7, song.getAlbumName());
                stmt.setDate(8, song.getReleaseDate());
                stmt.setString(9, song.getPreviewUrl());
                stmt.setString(10, song.getImageUrl());
                stmt.setLong(11, song.getDurationMs());
                                stmt.setBoolean(12, song.isExplicit());

                stmt.addBatch();
                batchCount++;
                
                if (batchCount % 100 == 0) {
                    stmt.executeBatch();
                    LOGGER.info("Saved {}/{} songs", batchCount, songs.size());
                }
            }
            if (batchCount > 0) {
                stmt.executeBatch();
                LOGGER.info("Saved all {}/{} songs", batchCount, songs.size());
            }
            
        } catch (SQLException e) {
            LOGGER.error("Error saving songs to database", e);
            throw e;
        }
    }

    private void saveSongsOffline(List<Song> songs) {
        try {
            // Store songs by ID for quick lookup
            Map<String, Song> songsById = loadSongsFromFile();
            
            // Add/update songs in the map
            for (Song song : songs) {
                if (song != null && song.getId() != null) {
                    songsById.put(song.getId(), song);
                }
            }
            
            // Write the updated map back to file
            saveSongsToFile(songsById);
            LOGGER.info("Saved {} songs in offline mode", songs.size());
        } catch (Exception e) {
            LOGGER.error("Error saving songs in offline mode", e);
        }
    }

    private Map<String, Song> loadSongsFromFile() {
        Path filePath = storageDir.resolve("songs.json");
        if (Files.exists(filePath)) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
                return gson.fromJson(reader, new TypeToken<Map<String, Song>>(){}.getType());
            } catch (IOException e) {
                LOGGER.error("Failed to load songs from file", e);
            }
        }
        return new HashMap<>();
    }

    private void saveSongsToFile(Map<String, Song> songs) {
        try (Writer writer = Files.newBufferedWriter(storageDir.resolve("songs.json"))) {
            gson.toJson(songs, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save songs to file", e);
        }
    }

    /**
     * Retrieves existing song IDs from the database or offline storage.
     * @param conn The database connection.
     * @param SongIds The set of song IDs to check.
     * @return A set of existing song IDs.
     * @throws SQLException If an error occurs while querying the database.
     */
    public Set<String> getExistingSongIds(Connection conn, Set<String> SongIds) throws SQLException {
        if (isOfflineMode()) {
            return getExistingSongIdsOffline(SongIds);
        }

        String sql = "SELECT id FROM songs WHERE id IN (" + String.join(",", Collections.nCopies(SongIds.size(), "?")) + ")";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int index = 1;
            for (String id : SongIds) {
                stmt.setString(index++, id);
            }
            ResultSet rs = stmt.executeQuery();
            Set<String> existingIds = new HashSet<>();
            while (rs.next()) {
                existingIds.add(rs.getString("id"));
            }
            return existingIds;
        } catch (SQLException e) {
            LOGGER.error("Error fetching existing song IDs", e);
            throw e;
        }
    }

    private Set<String> getExistingSongIdsOffline(Set<String> spotifyIds) {
        Set<String> existingIds = new HashSet<>();
        Map<String, Song> songs = loadSongsFromFile();
        
        for (Song song : songs.values()) {
            if (song.getSpotifyId() != null && spotifyIds.contains(song.getSpotifyId())) {
                existingIds.add(song.getSpotifyId());
            }
        }
        
        return existingIds;
    }
}
