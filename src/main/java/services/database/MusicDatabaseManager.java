package services.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import services.database.managers.*;
import models.UserMusicData;
import models.Artist;
import models.Song;
import models.User;

/**
 * Database manager responsible for persisting and retrieving music data.
 * Features:
 * - Online (PostgreSQL) and offline (JSON file) storage modes
 * - Transaction management
 * - Batch operations for performance
 * - Deduplication of artists and songs
 * - Automatic ID generation and resolution
 */
public class MusicDatabaseManager {
    private static final String DB_URL = "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres";
    private static final String DB_USER = "postgres.mowurhjpxbsxlyvapejv";
    private static final String DB_PASSWORD = "SomePasswordSUPABASE";
    private static final Logger LOGGER = LoggerFactory.getLogger(MusicDatabaseManager.class);
    private static final HikariDataSource connectionPool;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASSWORD);
        config.setMaximumPoolSize(5); // Reduce pool size to stay within Supabase limits
        config.setMinimumIdle(1);     // Keep minimum connections
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(120000); // 2 minutes
        config.setMaxLifetime(180000); // 3 minutes
        config.setLeakDetectionThreshold(60000);
        config.setPoolName("PlaylistGeneratorPool");

        // Disable logging to console
        config.setInitializationFailTimeout(-1);
        config.setRegisterMbeans(false);

        // Add reconnection properties
        config.addDataSourceProperty("reWriteBatchedInserts", "true");
        config.addDataSourceProperty("socketTimeout", "30");
        config.addDataSourceProperty("connectTimeout", "10");

        connectionPool = new HikariDataSource(config);
    }

    private final User user;
    private final ArtistDatabaseManager artistManager;
    private final SongDatabaseManager songManager;
    private final PlayHistoryManager playHistoryManager;
    private final UserPreferenceManager preferenceManager;

    public MusicDatabaseManager(boolean isOnline, User user) {
        this.user = user;
        this.artistManager = new ArtistDatabaseManager(isOnline, user);
        this.songManager = new SongDatabaseManager(isOnline, user);
        this.playHistoryManager = new PlayHistoryManager(isOnline, user);
        // Pass 'this' to the UserPreferenceManager constructor instead of letting it create a new instance
        this.preferenceManager = new UserPreferenceManager(isOnline, user, this);
        
        LOGGER.info("Initializing MusicDatabaseManager in {} mode for user {}",
            isOnline ? "online" : "offline", user != null ? user.getName() : "default");
    }

    private <T> T withConnection(ConnectionOperation<T> operation) throws SQLException {
        try (Connection conn = getConnection()) {
            return operation.execute(conn);
        }
    }

    @FunctionalInterface
    private interface ConnectionOperation<T> {
        T execute(Connection conn) throws SQLException;
    }

    public Map<String, List<Object>> getCurrentUserPreferences() throws SQLException {
        return withConnection(conn -> preferenceManager.getCurrentUserPreferences(conn));
    }

    public void saveUserData(UserMusicData userData) throws SQLException {
        withConnection(conn -> {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                artistManager.saveArtists(conn, userData.getArtists());
                artistManager.saveArtistGenres(conn, userData.getArtists());
                songManager.saveSongs(conn, userData.getSongs());
                
                conn.commit();
                return null;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        });
    }

    public Artist getArtistBySong(Song song) {
        try (Connection conn = getConnection()) {
            // Explicitly cast the result to Artist
            return (Artist) artistManager.getArtistBySong(conn, song);
        } catch (SQLException e) {
            LOGGER.error("Error retrieving artist by song: {}", e.getMessage(), e);
            return null;
        }
    }
    
    public void savePlayHistory(UserMusicData userData) throws SQLException {
        withConnection(conn -> {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                playHistoryManager.savePlayHistory(conn, userData.getPlayHistory(), user);
                conn.commit();
                return null;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        });
    }

    public void updateUserPreferences(Map<String, List<Object>> preferences) throws SQLException {
        withConnection(conn -> {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                preferenceManager.updateCurrentUserPreferences(conn, preferences);
                conn.commit();
                return null;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        });
    }

    public Connection getConnection() throws SQLException {
        return connectionPool.getConnection();
    }

    public void cleanup() {
        if (connectionPool != null && !connectionPool.isClosed()) {
            connectionPool.close();
        }
    }

    public User getUser() {
        return user;
    }

    /**
     * Retrieves songs by genre from the database
     * @param genre The genre to filter by
     * @return A list of songs matching the genre
     */
    public List<Song> getSongsByGenre(String genre) {
        try {
            return withConnection(conn -> songManager.getSongsByGenre(conn, genre));
        } catch (SQLException e) {
            LOGGER.error("Error retrieving songs by genre: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Retrieves songs by artist name from the database
     * @param artistName The name of the artist
     * @return A list of songs by the artist
     */
    public List<Song> getSongsByArtist(String artistName) {
        try {
            return withConnection(conn -> songManager.getSongsByArtistName(conn, artistName));
        } catch (SQLException e) {
            LOGGER.error("Error retrieving songs by artist: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Retrieves the most popular songs from the database
     * @param limit Maximum number of songs to retrieve
     * @return A list of popular songs
     */
    public List<Song> getPopularSongs(int limit) {
        try {
            return withConnection(conn -> songManager.getPopularSongs(conn, limit));
        } catch (SQLException e) {
            LOGGER.error("Error retrieving popular songs: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Retrieves the user's top songs based on play history
     * @param limit Maximum number of songs to retrieve
     * @return A list of the user's top songs
     */
    public List<Song> getTopSongs(int limit) {
        try {
            return withConnection(conn -> playHistoryManager.getUserTopSongs(conn, user, limit));
        } catch (SQLException e) {
            LOGGER.error("Error retrieving top songs: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Enriches artists and songs with complete information from the database
     * Populates missing fields like spotify IDs, links, genres, etc. if they exist in the database
     * Uses batch processing for better performance
     * 
     * @param userData The user music data to enrich with database information
     * @return The same userData object with enriched information
     */
    public UserMusicData fetchCompleteDataFromDatabase(UserMusicData userData) {
        LOGGER.info("Fetching complete information from database for {} artists and {} songs", 
                  userData.getArtists().size(), userData.getSongs().size());
        
        try {
            withConnection(conn -> {
                // Process artists in batch using IN clause
                if (!userData.getArtists().isEmpty()) {
                    fetchArtistsInBatch(conn, userData.getArtists());
                    fetchArtistGenresInBatch(conn, userData.getArtists());
                }
                
                // Process songs in batch using IN clause
                if (!userData.getSongs().isEmpty()) {
                    fetchSongsInBatch(conn, userData.getSongs());
                }
                
                return null; // Return value not used, method is for side effects
            });
            
            LOGGER.info("Database fetch completed. {} artists and {} songs enriched", 
                      userData.getArtists().size(), userData.getSongs().size());
            
        } catch (Exception e) {
            LOGGER.error("Error fetching data from database: {}", e.getMessage(), e);
        }
        
        return userData;
    }
    
    /**
     * Fetches artist data in batch from the database
     * 
     * @param conn The database connection
     * @param artists The list of artists to fetch data for
     * @throws SQLException If there's an error querying the database
     */
    private void fetchArtistsInBatch(Connection conn, List<Artist> artists) throws SQLException {
        // Get all artist names for the query
        List<String> artistNames = artists.stream()
            .map(Artist::getName)
            .collect(Collectors.toList());
        
        // Build parameterized query with the right number of placeholders
        StringBuilder queryBuilder = new StringBuilder(
            "SELECT name, id, spotify_id, spotify_link, popularity, image_url FROM artists WHERE LOWER(name) = ANY(?)"
        );
        
        try (java.sql.PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {
            // Set the array parameter with all artist names (converted to lowercase)
            String[] namesArray = artistNames.stream()
                .map(String::toLowerCase)
                .toArray(String[]::new);
            
            stmt.setArray(1, conn.createArrayOf("text", namesArray));
            
            // Execute the query and process results
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                // Create a map for quick lookup by name
                Map<String, Map<String, Object>> artistDataByName = new HashMap<>();
                
                while (rs.next()) {
                    String name = rs.getString("name");
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", rs.getString("id"));
                    data.put("spotify_id", rs.getString("spotify_id"));
                    data.put("spotify_link", rs.getString("spotify_link"));
                    data.put("popularity", rs.getInt("popularity"));
                    data.put("image_url", rs.getString("image_url"));
                    
                    artistDataByName.put(name.toLowerCase(), data);
                }
                
                // Update artists with the retrieved data
                for (Artist artist : artists) {
                    Map<String, Object> data = artistDataByName.get(artist.getName().toLowerCase());
                    if (data != null) {
                        if (artist.getSpotifyId() == null || artist.getSpotifyId().isEmpty()) {
                            artist.setSpotifyId((String) data.get("spotify_id"));
                        }
                        if (artist.getSpotifyLink() == null || artist.getSpotifyLink().isEmpty()) {
                            artist.setSpotifyLink((String) data.get("spotify_link"));
                        }
                        if (artist.getImageUrl() == null || artist.getImageUrl().isEmpty()) {
                            artist.setImageUrl((String) data.get("image_url"));
                        }
                        if (artist.getPopularity() == 0) {
                            artist.setPopularity((Integer) data.get("popularity"));
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Fetches artist genres in batch from the database
     * 
     * @param conn The database connection
     * @param artists The list of artists to fetch genres for
     * @throws SQLException If there's an error querying the database
     */
    private void fetchArtistGenresInBatch(Connection conn, List<Artist> artists) throws SQLException {
        // Get artists for which we need genres
        List<UUID> artistIds = artists.stream()
            .filter(artist -> artist.getGenres() == null || artist.getGenres().isEmpty())
            .filter(artist -> artist.getId() != null)
            .map(artist -> UUID.fromString(artist.getId()))
            .collect(Collectors.toList());
        
        if (artistIds.isEmpty()) {
            return; // No artists need genres
        }
        
        // Build query to get all genres for all artists in one query using artist_id
        String query = "SELECT ag.artist_id, g.name as genre FROM artist_genres ag " +
                       "JOIN genres g ON ag.genre_id = g.id " +
                       "WHERE ag.artist_id = ANY(?)";
        
        try (java.sql.PreparedStatement stmt = conn.prepareStatement(query)) {
            // Set the array parameter with all artist IDs
            stmt.setArray(1, conn.createArrayOf("uuid", artistIds.toArray()));
            
            // Execute the query and process results
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                // Create a map to collect genres for each artist
                Map<String, List<String>> genresByArtist = new HashMap<>();
                
                while (rs.next()) {
                    String artistId = rs.getString("artist_id");
                    String genre = rs.getString("genre");
                    
                    genresByArtist.computeIfAbsent(artistId, k -> new ArrayList<>())
                        .add(genre);
                }
                
                // Update artists with the retrieved genres
                for (Artist artist : artists) {
                    if ((artist.getGenres() == null || artist.getGenres().isEmpty()) && artist.getId() != null) {
                        List<String> genres = genresByArtist.get(artist.getId());
                        if (genres != null && !genres.isEmpty()) {
                            artist.setGenres(genres);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Fetches song data in batch from the database
     * 
     * @param conn The database connection
     * @param songs The list of songs to fetch data for
     * @throws SQLException If there's an error querying the database
     */
    private void fetchSongsInBatch(Connection conn, List<Song> songs) throws SQLException {
        // Prepare data structures for the query parameters
        List<Object[]> songParams = new ArrayList<>();
        
        for (Song song : songs) {
            // Add title and artist name as parameters
            songParams.add(new Object[] { 
                song.getTitle().toLowerCase(), 
                song.getArtist().getName().toLowerCase() 
            });
        }
        
        // Build the query with multiple OR conditions for (title, artist) pairs
        StringBuilder queryBuilder = new StringBuilder(
            "SELECT s.id, s.title, s.spotify_id, s.spotify_link, s.album_name, s.popularity, " +
            "s.release_date, s.preview_url, s.image_url, s.duration_ms, s.is_explicit, " +
            "a.name as artist_name " +
            "FROM songs s " +
            "JOIN artists a ON s.artist_id = a.id " +
            "WHERE "
        );
        
        // Add condition for each song
        List<String> conditions = new ArrayList<>();
        for (int i = 0; i < songParams.size(); i++) {
            conditions.add("(LOWER(s.title) = ? AND LOWER(a.name) = ?)");
        }
        
        queryBuilder.append(String.join(" OR ", conditions));
        
        try (java.sql.PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString())) {
            // Set all parameters
            int paramIndex = 1;
            for (Object[] params : songParams) {
                stmt.setString(paramIndex++, (String) params[0]);  // title
                stmt.setString(paramIndex++, (String) params[1]);  // artist name
            }
            
            // Execute the query and process results
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                // Create a map for quick lookup by title and artist name
                Map<String, Map<String, Object>> songDataMap = new HashMap<>();
                
                while (rs.next()) {
                    String title = rs.getString("title");
                    String artistName = rs.getString("artist_name");
                    String key = (title + "|" + artistName).toLowerCase();
                    
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", rs.getString("id"));
                    data.put("spotify_id", rs.getString("spotify_id"));
                    data.put("spotify_link", rs.getString("spotify_link"));
                    data.put("album_name", rs.getString("album_name"));
                    data.put("popularity", rs.getInt("popularity"));
                    data.put("release_date", rs.getDate("release_date"));
                    data.put("preview_url", rs.getString("preview_url"));
                    data.put("image_url", rs.getString("image_url"));
                    data.put("duration_ms", rs.getLong("duration_ms"));
                    data.put("is_explicit", rs.getBoolean("is_explicit"));
                    
                    songDataMap.put(key, data);
                }
                
                // Update songs with the retrieved data
                for (Song song : songs) {
                    String key = (song.getTitle() + "|" + song.getArtist().getName()).toLowerCase();
                    Map<String, Object> data = songDataMap.get(key);
                    
                    if (data != null) {
                        if (song.getSpotifyId() == null || song.getSpotifyId().isEmpty()) {
                            song.setSpotifyId((String) data.get("spotify_id"));
                        }
                        if (song.getSpotifyLink() == null || song.getSpotifyLink().isEmpty()) {
                            song.setSpotifyLink((String) data.get("spotify_link"));
                        }
                        if (song.getAlbumName() == null || song.getAlbumName().isEmpty()) {
                            song.setAlbumName((String) data.get("album_name"));
                        }
                        if (song.getPopularity() == 0) {
                            song.setPopularity((Integer) data.get("popularity"));
                        }
                        if (song.getReleaseDate() == null) {
                            song.setReleaseDate((java.sql.Date) data.get("release_date"));
                        }
                        if (song.getPreviewUrl() == null || song.getPreviewUrl().isEmpty()) {
                            song.setPreviewUrl((String) data.get("preview_url"));
                        }
                        if (song.getImageUrl() == null || song.getImageUrl().isEmpty()) {
                            song.setImageUrl((String) data.get("image_url"));
                        }
                        if (song.getDurationMs() == 0) {
                            song.setDurationMs((Long) data.get("duration_ms"));
                        }
                        song.setExplicit((Boolean) data.get("is_explicit"));
                    }
                }
            }
        }
    }
    
    /**
     * Saves user data to database, but only items with new enriched data
     * Filters out items that haven't been enriched to avoid unnecessary database operations
     * 
     * @param userData The user music data to save
     * @throws SQLException If a database error occurs
     */
    public void saveEnrichedUserData(UserMusicData userData) throws SQLException {
        // Filter artists that have been enriched with new data
        List<Artist> enrichedArtists = userData.getArtists().stream()
            .filter(artist -> 
                (artist.getSpotifyId() != null && !artist.getSpotifyId().isEmpty()) ||
                (artist.getSpotifyLink() != null && !artist.getSpotifyLink().isEmpty()) ||
                (artist.getImageUrl() != null && !artist.getImageUrl().isEmpty()) ||
                (artist.getGenres() != null && !artist.getGenres().isEmpty()) ||
                artist.getPopularity() > 0)
            .collect(Collectors.toList());
        
        // Filter songs that have been enriched with new data
        List<Song> enrichedSongs = userData.getSongs().stream()
            .filter(song -> 
                (song.getSpotifyId() != null && !song.getSpotifyId().isEmpty()) ||
                (song.getSpotifyLink() != null && !song.getSpotifyLink().isEmpty()) ||
                (song.getImageUrl() != null && !song.getImageUrl().isEmpty()) ||
                (song.getPreviewUrl() != null && !song.getPreviewUrl().isEmpty()) ||
                (song.getAlbumName() != null && !song.getAlbumName().isEmpty()) ||
                song.getReleaseDate() != null ||
                song.getPopularity() > 0 ||
                song.getDurationMs() > 0)
            .collect(Collectors.toList());
        
        LOGGER.info("Saving {} enriched artists and {} enriched songs to database (filtered from {} artists and {} songs)",
                  enrichedArtists.size(), enrichedSongs.size(), userData.getArtists().size(), userData.getSongs().size());
        
        if (enrichedArtists.isEmpty() && enrichedSongs.isEmpty()) {
            LOGGER.info("No enriched data to save");
            return;
        }
        
        // Create temporary UserMusicData object with only enriched items
        UserMusicData enrichedData = new UserMusicData();
        enrichedArtists.forEach(enrichedData::addArtist);
        enrichedSongs.forEach(enrichedData::addSong);
        
        // Use the existing saveUserData method to save only the enriched data
        saveUserData(enrichedData);
    }
}