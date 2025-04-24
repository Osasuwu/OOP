package services.database.managers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.io.*;
import java.nio.file.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import models.Artist;
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

        // First, get all existing songs by Spotify ID to avoid conflicts with the unique constraint
        Map<String, UUID> existingSongsBySpotifyId = getExistingSongsBySpotifyId(conn, songs);
        Map<String, UUID> existingSongsByTitleAndArtist = getExistingSongsByTitleAndArtist(conn, songs);
        
        for (Song song : songs) {
            // First check by Spotify ID
            if (song.getSpotifyId() != null && existingSongsBySpotifyId.containsKey(song.getSpotifyId())) {
                // Use the existing UUID from database instead of the one we generated
                song.setId(existingSongsBySpotifyId.get(song.getSpotifyId()).toString());
                LOGGER.debug("Using existing ID for song with Spotify ID '{}': {}", song.getSpotifyId(), song.getId());
            }
            // Then check by title and artist if still not found
            else if (existingSongsByTitleAndArtist.containsKey(song.getTitle().toLowerCase())) {
                song.setId(existingSongsByTitleAndArtist.get(song.getTitle().toLowerCase()).toString());
                LOGGER.debug("Using existing ID for song '{}': {}", song.getTitle(), song.getId());
            }
        }

        String sql = """
            INSERT INTO songs (id, artist_id, spotify_id, title, spotify_link, popularity, album_name, release_date, preview_url, image_url, duration_ms, is_explicit)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
            artist_id = EXCLUDED.artist_id,
            spotify_id = CASE WHEN EXCLUDED.spotify_id IS NOT NULL AND EXCLUDED.spotify_id != '' THEN EXCLUDED.spotify_id ELSE songs.spotify_id END,
            title = EXCLUDED.title,
            spotify_link = CASE WHEN EXCLUDED.spotify_link IS NOT NULL AND EXCLUDED.spotify_link != '' THEN EXCLUDED.spotify_link ELSE songs.spotify_link END,
            popularity = CASE WHEN EXCLUDED.popularity > 0 THEN EXCLUDED.popularity ELSE songs.popularity END,
            album_name = CASE WHEN EXCLUDED.album_name IS NOT NULL AND EXCLUDED.album_name != '' THEN EXCLUDED.album_name ELSE songs.album_name END,
            release_date = CASE WHEN EXCLUDED.release_date IS NOT NULL THEN EXCLUDED.release_date ELSE songs.release_date END,
            preview_url = CASE WHEN EXCLUDED.preview_url IS NOT NULL AND EXCLUDED.preview_url != '' THEN EXCLUDED.preview_url ELSE songs.preview_url END,
            image_url = CASE WHEN EXCLUDED.image_url IS NOT NULL AND EXCLUDED.image_url != '' THEN EXCLUDED.image_url ELSE songs.image_url END,
            duration_ms = CASE WHEN EXCLUDED.duration_ms > 0 THEN EXCLUDED.duration_ms ELSE songs.duration_ms END,
            is_explicit = EXCLUDED.is_explicit            
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int batchCount = 0;
            for (Song song : songs) {
                if (song.getId() == null) {
                    continue;
                }
                stmt.setObject(1, UUID.fromString(song.getId()));
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
                    stmt.executeBatch(); // Execute every 100 records to avoid memory issues
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

    /**
     * Get existing songs by Spotify ID to avoid unique constraint violations
     * @param conn Database connection
     * @param songs List of songs to check
     * @return Map of Spotify IDs to song UUIDs in the database
     * @throws SQLException If an error occurs while querying the database
     */
    private Map<String, UUID> getExistingSongsBySpotifyId(Connection conn, List<Song> songs) {
        Map<String, UUID> existingSongs = new HashMap<>();
        
        // Extract all non-null Spotify IDs
        List<String> spotifyIds = songs.stream()
            .filter(s -> s != null && s.getSpotifyId() != null)
            .map(Song::getSpotifyId)
            .filter(id -> !id.isEmpty())
            .distinct()
            .toList();
        
        if (spotifyIds.isEmpty()) {
            return existingSongs;
        }
        
        // Query database for existing songs with these Spotify IDs
        String sql = "SELECT id, spotify_id FROM songs WHERE spotify_id = ANY(?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setArray(1, conn.createArrayOf("text", spotifyIds.toArray()));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String spotifyId = rs.getString("spotify_id");
                    UUID id = (UUID) rs.getObject("id");
                    existingSongs.put(spotifyId, id);
                }
            }
            
            LOGGER.debug("Found {} existing songs by Spotify ID in database", existingSongs.size());
        } catch (SQLException e) {
            LOGGER.error("Error fetching existing songs by Spotify ID", e);
        }
        
        return existingSongs;
    }

    private Map<String, UUID> getExistingSongsByTitleAndArtist(Connection conn, List<Song> songs) {
        Map<String, UUID> existingSongs = new HashMap<>();
        
        // Build list of song titles and artist IDs
        if (songs.isEmpty()) {
            return existingSongs;
        }
        
        // Create query to find songs with matching titles and artist IDs
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT id, title FROM songs WHERE (");
        
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            conditions.add("(LOWER(title) = ? AND artist_id = ?)");
        }
        
        queryBuilder.append(String.join(" OR ", conditions));
        queryBuilder.append(")");
        
        String sql = queryBuilder.toString();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int paramIndex = 1;
            for (Song song : songs) {
                stmt.setString(paramIndex++, song.getTitle().toLowerCase());
                stmt.setObject(paramIndex++, UUID.fromString(song.getArtist().getId()));
            }
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String title = rs.getString("title").toLowerCase();
                UUID id = (UUID) rs.getObject("id");
                existingSongs.put(title, id);
            }
        } catch (SQLException e) {
            LOGGER.error("Error fetching existing songs by title and artist", e);
        }
        
        return existingSongs;
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

    public List<Song> loadSongs(Connection conn) throws SQLException {
        List<Song> songs = new ArrayList<>();
        
        if (isOnline) {
            String query = "SELECT id, title, artist_id, duration, genre FROM songs WHERE user_id = ?";
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setObject(1, UUID.fromString(user.getId()));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Song song = new Song(rs.getString("title"), rs.getString("artist_name"));
                        song.setId(rs.getString("id"));
                        song.setArtistId(rs.getString("artist_id"));
                        song.setDurationMs(rs.getLong("duration"));
                        songs.add(song);
                    }
                }
            }
        } else {
            // Load from local storage implementation
        }
        
        return songs;
    }

    /**
     * Retrieves songs by genre from the database.
     * @param conn The database connection.
     * @param genre The genre to filter by.
     * @return A list of songs matching the genre.
     * @throws SQLException If an error occurs while querying the database.
     */
    public List<Song> getSongsByGenre(Connection conn, String genre) throws SQLException {
        List<Song> songs = new ArrayList<>();
        
        if (isOfflineMode()) {
            return getSongsByGenreOffline(genre);
        }

        // Use the get_songs_by_genre stored procedure
        String sql = "SELECT * FROM get_songs_by_genre(?, 100)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, genre);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    songs.add(createSongFromResultSet(rs));
                }
            }
        }
        
        LOGGER.debug("Found {} songs with genre '{}'", songs.size(), genre);
        return songs;
    }
    
    /**
     * Retrieves songs by artist name from the database.
     * @param conn The database connection.
     * @param artistName The name of the artist.
     * @return A list of songs by the artist.
     * @throws SQLException If an error occurs while querying the database.
     */
    public List<Song> getSongsByArtistName(Connection conn, String artistName) throws SQLException {
        List<Song> songs = new ArrayList<>();
        
        if (isOfflineMode()) {
            return getSongsByArtistNameOffline(artistName);
        }

        // Use the get_songs_by_artist stored procedure
        String sql = "SELECT * FROM get_songs_by_artist(?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, artistName);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    songs.add(createSongFromResultSet(rs));
                }
            }
        }
        
        LOGGER.debug("Found {} songs by artist '{}'", songs.size(), artistName);
        return songs;
    }
    
    /**
     * Retrieves the most popular songs from the database.
     * @param conn The database connection.
     * @param limit Maximum number of songs to retrieve.
     * @return A list of popular songs.
     * @throws SQLException If an error occurs while querying the database.
     */
    public List<Song> getPopularSongs(Connection conn, int limit) throws SQLException {
        List<Song> songs = new ArrayList<>();
        
        if (isOfflineMode()) {
            return getPopularSongsOffline(limit);
        }

        // Use the get_popular_songs stored procedure
        String sql = "SELECT * FROM get_popular_songs(?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    songs.add(createSongFromResultSet(rs));
                }
            }
        }
        
        LOGGER.debug("Found {} popular songs", songs.size());
        return songs;
    }
    
    /**
     * Helper method to create a Song object from a ResultSet.
     * @param rs The ResultSet containing song data.
     * @return A Song object.
     * @throws SQLException If an error occurs while accessing the ResultSet.
     */
    private Song createSongFromResultSet(ResultSet rs) throws SQLException {
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
        
        return song;
    }
    
    // Offline fallback implementations
    
    private List<Song> getSongsByGenreOffline(String genre) {
        List<Song> result = new ArrayList<>();
        Map<String, Song> songs = loadSongsFromFile();
        
        for (Song song : songs.values()) {
            if (song.getGenres() != null && 
                song.getGenres().stream()
                    .anyMatch(g -> g.toLowerCase().contains(genre.toLowerCase()))) {
                result.add(song);
            }
        }
        
        return result;
    }
    
    private List<Song> getSongsByArtistNameOffline(String artistName) {
        List<Song> result = new ArrayList<>();
        Map<String, Song> songs = loadSongsFromFile();
        
        for (Song song : songs.values()) {
            if (song.getArtist() != null && 
                song.getArtist().getName().toLowerCase().contains(artistName.toLowerCase())) {
                result.add(song);
            }
        }
        
        return result;
    }
    
    private List<Song> getPopularSongsOffline(int limit) {
        Map<String, Song> songs = loadSongsFromFile();
        
        return songs.values().stream()
            .sorted((s1, s2) -> Integer.compare(s2.getPopularity(), s1.getPopularity()))
            .limit(limit)
            .collect(Collectors.toList());
    }
}
