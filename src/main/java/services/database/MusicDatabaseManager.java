package services.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.sql.Timestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.stream.Collectors;

import services.database.OfflineDataManager;
import models.*;
import utils.GenreMapper;

/**
 * Database manager responsible for persisting and retrieving music data.
 * Supports both online (PostgreSQL) and offline (file-based) storage modes.
 * Features:
 * - Transaction management
 * - Batch operations for performance
 * - Deduplication of artists and songs
 * - Automatic ID generation and resolution
 */
public class MusicDatabaseManager {
    private static final String DB_URL = "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres";
    private static final String DB_USER = "postgres.ovinvbshhlfiazazcsaw";
    private static final String DB_PASSWORD = "BS1l7MtXTDZ2pfd5";
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

    private boolean isOnline;
    private String userId;
    private OfflineDataManager offlineManager;

    /**
     * Creates a new database manager instance
     * @param isOnline Whether to use online or offline storage
     * @param userId User identifier for data operations
     */
    public MusicDatabaseManager(boolean isOnline, String userId) {
        LOGGER.info("Initializing MusicDatabaseManager in " + (isOnline ? "online" : "offline") + " mode");
        this.isOnline = isOnline;
        this.userId = userId; 
        this.offlineManager = new OfflineDataManager(userId);
    }

    /**
     * Safely executes code with a database connection, ensuring it's always closed
     * even if an exception is thrown.
     * 
     * @param operation The database operation to execute with the connection
     * @return The result of the operation
     * @throws SQLException if a database error occurs
     */
    private <T> T withConnection(ConnectionOperation<T> operation) throws SQLException {
        try (Connection conn = getConnection()) {
            return operation.execute(conn);
        }
    }

    /**
     * Functional interface for database operations using a connection
     */
    @FunctionalInterface
    private interface ConnectionOperation<T> {
        T execute(Connection conn) throws SQLException;
    }

    /**
     * Retrieves user preferences from the database
     * @return Map of user preferences
     * @throws SQLException if database access fails
     */
    public Map<String, Object> getUserPreferences() throws SQLException {
        LOGGER.info("Retrieving preferences for user: " + userId);
        if (!isOnline) {
            return offlineManager.getUserPreferences();
        }

        return withConnection(conn -> {
            Map<String, Object> preferences = new HashMap<>();
            String sql = """
                SELECT 
                    favorite_genres,
                    favorite_artists,
                    favorite_songs,
                    disliked_genres,
                    disliked_artists,
                    disliked_songs,
                    preferred_tempo_range,
                    preferred_danceability,
                    preferred_energy,
                    preferred_valence
                FROM user_preferences
                WHERE user_id = CAST(? AS UUID)
            """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, UUID.fromString(userId));
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    preferences.put("favorite_genres", rs.getArray("favorite_genres"));
                    preferences.put("favorite_artists", rs.getArray("favorite_artists"));
                    preferences.put("favorite_songs", rs.getArray("favorite_songs"));
                    preferences.put("disliked_genres", rs.getArray("disliked_genres"));
                    preferences.put("disliked_artists", rs.getArray("disliked_artists"));
                    preferences.put("disliked_songs", rs.getArray("disliked_songs"));
                    preferences.put("preferred_tempo_range", rs.getArray("preferred_tempo_range"));
                    preferences.put("preferred_danceability", rs.getDouble("preferred_danceability"));
                    preferences.put("preferred_energy", rs.getDouble("preferred_energy"));
                    preferences.put("preferred_valence", rs.getDouble("preferred_valence"));
                }
            }
            return preferences;
        });
    }

    /**
     * Saves complete user music data to the database
     * Executes as a single transaction to maintain data consistency
     * @param userData The user data to save
     * @throws SQLException if database access fails
     */
    public void saveUserData(UserMusicData userData) throws SQLException {
        if (!isOnline) {
            LOGGER.info("Operating in offline mode, delegating to offline manager");
            offlineManager.saveUserData(userData);
            return;
        }

        LOGGER.info("Starting transaction to save user data");
        withConnection(conn -> {
            boolean originalAutoCommit = conn.getAutoCommit();
            
            try {
                conn.setAutoCommit(false);
                LOGGER.info("Saving artists...");
                saveArtists(conn, userData.getArtists());
                
                LOGGER.info("Saving songs...");
                saveSongs(conn, userData.getSongs());
                
                LOGGER.info("Saving genres...");
                saveGenres(conn, userData.getFavoriteGenres());
                
                conn.commit();
                LOGGER.info("Transaction committed successfully");
                return null;
            } catch (SQLException e) {
                LOGGER.error("Transaction failed, rolling back", e);
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        });
    }
    
    /**
     * Resolves or creates an artist ID by name
     * @param conn Active database connection
     * @param artistName Name of the artist to resolve
     * @return UUID of the existing or new artist
     */
    private Map<String, UUID> resolveArtistIds(Connection conn, List<Artist> artists) throws SQLException {
        Map<String, UUID> artistIds = new HashMap<>();
        Set<String> artistNames = artists.stream()
            .filter(a -> a != null && a.getName() != null)
            .map(Artist::getName)
            .collect(Collectors.toSet());

        // Batch lookup existing artists
        String lookupSql = "SELECT id, name FROM artists WHERE name = ANY(?)";
        try (PreparedStatement stmt = conn.prepareStatement(lookupSql)) {
            stmt.setArray(1, conn.createArrayOf("text", artistNames.toArray()));
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                artistIds.put(rs.getString("name"), (UUID) rs.getObject("id"));
            }
        }

        // Generate new UUIDs for missing artists
        artistNames.stream()
            .filter(name -> !artistIds.containsKey(name))
            .forEach(name -> artistIds.put(name, UUID.randomUUID()));

        return artistIds;
    }

    /**
     * Saves a list of artists to the database with genre information
     * Updates existing artists if they already exist
     * @param conn Active database connection
     * @param artists List of artists to save
     */
    private void saveArtists(Connection conn, List<Artist> artists) throws SQLException {
        LOGGER.info(String.format("Starting artist save operation for %d artists", artists.size()));
        long startTime = System.currentTimeMillis();

        // Batch resolve all artist IDs at once
        Map<String, UUID> artistIds = resolveArtistIds(conn, artists);
        LOGGER.info("Artist ID resolution completed in " + (System.currentTimeMillis() - startTime) + "ms");

        // Update artist objects with resolved IDs
        artists.forEach(artist -> {
            if (artist != null && artist.getName() != null) {
                UUID id = artistIds.get(artist.getName());
                artist.setId(id.toString());
            }
        });

        // Prepare batch size
        final int BATCH_SIZE = 100;
        int batchCount = 0;
        int totalSaved = 0;
        int totalUpdated = 0;

        String sql = """
            INSERT INTO artists (id, name, popularity) 
            VALUES (CAST(? AS UUID), ?, ?)
            ON CONFLICT (name) DO UPDATE SET
            popularity = EXCLUDED.popularity
            RETURNING id
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Artist artist : artists) {
                if (artist == null || artist.getName() == null) {
                    LOGGER.warn("Skipping null artist entry");
                    continue;
                }
                
                UUID id = artistIds.get(artist.getName());
                stmt.setObject(1, id);
                stmt.setString(2, artist.getName());
                stmt.setInt(3, artist.getPopularity());
                stmt.addBatch();
                batchCount++;

                if (batchCount >= BATCH_SIZE) {
                    int[] results = stmt.executeBatch();
                    totalSaved += countSuccessfulInserts(results);
                    LOGGER.info(String.format("Processed batch of %d artists (%d/%d total)", 
                        results.length, totalSaved, artists.size()));
                    batchCount = 0;
                }
            }
            
            // Execute remaining batch
            if (batchCount > 0) {
                int[] results = stmt.executeBatch();
                totalSaved += countSuccessfulInserts(results);
                LOGGER.info(String.format("Final batch processed: %d artists", results.length));
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        LOGGER.info(String.format("Artist save operation completed in %dms. Saved/Updated %d artists", 
            duration, totalSaved));

        // Save artist genres with batch processing
        saveArtistGenres(conn, artists);
    }

    private int countSuccessfulInserts(int[] results) {
        int count = 0;
        for (int result : results) {
            if (result >= 0 || result == Statement.SUCCESS_NO_INFO) {
                count++;
            }
        }
        return count;
    }

    private void saveArtistGenres(Connection conn, List<Artist> artists) throws SQLException {
        LOGGER.info("Starting genre associations save...");

        // Get existing genre associations to avoid duplicates
        Map<String, Set<String>> existingArtistGenres = new HashMap<>();
        String lookupSql = "SELECT artist_name, genre FROM artist_genres";
        try (PreparedStatement stmt = conn.prepareStatement(lookupSql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String artistName = rs.getString("artist_name");
                String genre = rs.getString("genre").toLowerCase();
                
                if (!existingArtistGenres.containsKey(artistName)) {
                    existingArtistGenres.put(artistName, new HashSet<>());
                }
                existingArtistGenres.get(artistName).add(genre);
            }
        }
        
        LOGGER.info("Found {} existing artist-genre associations", 
            existingArtistGenres.values().stream().mapToInt(Set::size).sum());

        // Use artist_name instead of artist_id for the associations
        String genreSql = "INSERT INTO artist_genres (artist_name, genre) VALUES (?, ?) " +
                 "ON CONFLICT (artist_name, genre) DO NOTHING";
        
        LOGGER.info("Using SQL: {}", genreSql);
        
        // Process in small batches to avoid large batch failures
        final int BATCH_SIZE = 10;
        int batchCount = 0;
        int totalGenres = 0;
        int totalSkipped = 0;
        
        try (PreparedStatement stmt = conn.prepareStatement(genreSql)) {
            for (Artist artist : artists) {
                if (artist == null || artist.getGenres() == null || artist.getName() == null) {
                    continue;
                }
                
                String artistName = artist.getName();
                Set<String> existingGenres = existingArtistGenres.getOrDefault(artistName, new HashSet<>());
                
                for (String genre : artist.getGenres()) {
                    if (genre == null || genre.trim().isEmpty()) {
                        continue;
                    }
                    
                    String normalizedGenre = GenreMapper.normalizeGenre(genre);
                    
                    // Skip if this association already exists
                    if (existingGenres.contains(normalizedGenre)) {
                        totalSkipped++;
                        continue;
                    }
                    
                    stmt.setString(1, artistName);
                    stmt.setString(2, genre);
                    stmt.addBatch();
                    batchCount++;
                    totalGenres++;
                    
                    // Add to set to avoid duplicates in the same run
                    existingGenres.add(normalizedGenre);
                    
                    if (batchCount >= BATCH_SIZE) {
                        try {
                            stmt.executeBatch();
                            LOGGER.debug("Executed genre batch of {} entries", batchCount);
                            batchCount = 0;
                        } catch (SQLException e) {
                            LOGGER.error("Error inserting genre batch: {}", e.getMessage());
                            // Continue with next batch despite errors
                            batchCount = 0;
                        }
                    }
                }
            }
            
            // Execute remaining batch
            if (batchCount > 0) {
                try {
                    stmt.executeBatch();
                    LOGGER.debug("Executed final genre batch of {} entries", batchCount);
                } catch (SQLException e) {
                    LOGGER.error("Error inserting final genre batch: {}", e.getMessage());
                }
            }
        }
        
        LOGGER.info("Genre associations completed: {} inserted, {} skipped as duplicates", 
                totalGenres, totalSkipped);
    }

    /**
     * Saves songs to the database, handling duplicates and artist relations.
     * Process:
     * 1. Group songs by artist for efficient processing
     * 2. Resolve artist IDs for each group
     * 3. Check for existing songs to avoid duplicates
     * 4. Insert new songs and update existing ones
     *
     * @param conn Database connection
     * @param songs List of songs to save
     * @throws SQLException if database operation fails
     */
    private void saveSongs(Connection conn, List<Song> songs) throws SQLException {
        LOGGER.info("Starting song save operation for {} songs", songs.size());
        long startTime = System.currentTimeMillis();
        
        // First deduplicate songs by artist and title
        Map<String, Song> uniqueSongs = new HashMap<>();
        for (Song song : songs) {
            if (song != null && song.getTitle() != null && song.getArtistName() != null) {
                String key = (song.getTitle() + "|" + song.getArtistName()).toLowerCase();
                uniqueSongs.putIfAbsent(key, song);
            }
        }
        
        LOGGER.debug("Deduplicated {} songs to {} unique entries", 
            songs.size(), uniqueSongs.size());

        // Group songs by artist
        Map<String, List<Song>> songsByArtist = uniqueSongs.values().stream()
            .collect(Collectors.groupingBy(Song::getArtistName));

        LOGGER.debug("Grouped songs into {} artist groups", songsByArtist.size());

        // Process each artist's songs
        int totalProcessed = 0;
        int totalSaved = 0;
        int totalUpdated = 0;

        for (Map.Entry<String, List<Song>> entry : songsByArtist.entrySet()) {
            String artistName = entry.getKey();
            List<Song> artistSongs = entry.getValue();

            LOGGER.debug("Processing {} songs for artist: {}", artistSongs.size(), artistName);

            // Get artist ID
            String artistSql = "SELECT id FROM artists WHERE name = ?";
            UUID artistId;
            try (PreparedStatement stmt = conn.prepareStatement(artistSql)) {
                stmt.setString(1, artistName);
                ResultSet rs = stmt.executeQuery();
                if (!rs.next()) {
                    LOGGER.warn("Artist not found: " + artistName);
                    continue;
                }
                artistId = (UUID) rs.getObject("id");
            }

            // Check existing songs for this artist
            String checkSql = """
                SELECT id, title, spotify_id
                FROM songs 
                WHERE artist_id = ? AND title = ANY(?)
            """;
            
            Set<String> existingTitles = new HashSet<>();
            Map<String, UUID> existingSongIds = new HashMap<>();
            
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                String[] titles = artistSongs.stream()
                    .map(Song::getTitle)
                    .toArray(String[]::new);
                    
                stmt.setObject(1, artistId);
                stmt.setArray(2, conn.createArrayOf("text", titles));
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    existingTitles.add(rs.getString("title").toLowerCase());
                    existingSongIds.put(rs.getString("title").toLowerCase(), 
                        (UUID) rs.getObject("id"));
                }
            }

            // Insert only new songs
            String insertSql = """
                INSERT INTO songs (id, title, artist_id, album_name, duration_ms, popularity, spotify_id)
                VALUES (CAST(? AS UUID), ?, CAST(? AS UUID), ?, ?, ?, ?)
                ON CONFLICT (title, artist_id) DO UPDATE SET
                    album_name = EXCLUDED.album_name,
                    duration_ms = EXCLUDED.duration_ms,
                    popularity = GREATEST(songs.popularity, EXCLUDED.popularity),
                    spotify_id = COALESCE(songs.spotify_id, EXCLUDED.spotify_id)
                RETURNING id
            """;

            int savedCount = 0;
            int updatedCount = 0;

            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                for (Song song : artistSongs) {
                    String titleKey = song.getTitle().toLowerCase();
                    
                    // Update existing song's ID in our object
                    if (existingTitles.contains(titleKey)) {
                        song.setId(existingSongIds.get(titleKey).toString());
                        updatedCount++;
                        continue;
                    }

                    // Insert new song
                    String songId = UUID.randomUUID().toString();
                    song.setId(songId);
                    
                    stmt.setObject(1, UUID.fromString(songId));
                    stmt.setString(2, song.getTitle());
                    stmt.setObject(3, artistId);
                    stmt.setString(4, song.getAlbum());
                    stmt.setLong(5, song.getDurationMs() > 0 ? song.getDurationMs() : 180000);
                    stmt.setInt(6, song.getPopularity());
                    stmt.setString(7, song.getSpotifyId());
                    
                    stmt.executeUpdate();
                    savedCount++;
                }
            }

            LOGGER.info("Artist batch complete - {} saved, {} updated for: {}", 
                savedCount, updatedCount, artistName);
            totalProcessed += artistSongs.size();
            totalSaved += savedCount;
            totalUpdated += updatedCount;
        }

        long duration = System.currentTimeMillis() - startTime;
        LOGGER.info("Song save operation completed in {}ms. Processed: {}, Saved: {}, Updated: {}", 
            duration, totalProcessed, totalSaved, totalUpdated);
    }

    private void savePlayHistory(Connection conn, List<PlayHistory> playHistory) throws SQLException {
        String sql = """
            INSERT INTO play_history (user_id, song_id, listened_at)
            VALUES (CAST(? AS UUID), ?, ?)
            ON CONFLICT (user_id, song_id, listened_at) DO NOTHING
        """;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (PlayHistory entry : playHistory) {
                if (entry.getSong() == null || entry.getTimestamp() == null) {
                    continue;
                }
                
                stmt.setObject(1, UUID.fromString(userId));
                stmt.setString(2, entry.getSong().getId());
                stmt.setTimestamp(3, new java.sql.Timestamp(entry.getTimestamp().getTime()));
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
    
    private void saveGenres(Connection conn, List<String> favoriteGenres) throws SQLException {
        // Fix the SQL query - add missing commas between parameters
        String genreSql = """
            INSERT INTO user_preferences (user_id, item_id, type, score)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, item_id, type) DO UPDATE SET
            score = EXCLUDED.score
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(genreSql)) {
            for (String genre : favoriteGenres) {
                if (genre == null || genre.trim().isEmpty()) continue;
                
                stmt.setString(1, userId);
                stmt.setString(2, genre);
                stmt.setString(3, "genre");
                stmt.setDouble(4, 1.0);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

        public void saveListenHistory(List<PlayHistory> history) throws SQLException {
            LOGGER.info("Starting batch save of {} play history entries to database", history.size());
            long startTime = System.currentTimeMillis();
            
            // First, create placeholder entries for missing songs to maintain referential integrity
            Set<String> songIds = new HashSet<>();
            Set<String> artistNames = new HashSet<>();
            
            for (PlayHistory entry : history) {
                if (entry.getSong() != null) {
                    String songId = entry.getSong().getId();
                    if (songId == null) {
                        songId = UUID.randomUUID().toString();
                        entry.getSong().setId(songId);
                    }
                    songIds.add(songId);
                    artistNames.add(entry.getSong().getArtistName());
                }
            }

            withConnection(conn -> {
                boolean originalAutoCommit = conn.getAutoCommit();
                
                try {
                    conn.setAutoCommit(false);

                    // Now save the actual listening history
                    String historySql = """
                        INSERT INTO listen_history (user_id, song_id, listened_at)
                        VALUES (CAST(? AS UUID), ?, ?)
                        ON CONFLICT (user_id, song_id, listened_at) DO NOTHING
                    """;

                    final int BATCH_SIZE = 100;
                    int batchCount = 0;
                    int totalProcessed = 0;
                    
                    LOGGER.debug("Using batch size of {} for history entries", BATCH_SIZE);
                    
                    try (PreparedStatement stmt = conn.prepareStatement(historySql)) {
                        for (PlayHistory entry : history) {
                            if (entry.getSong() == null || entry.getTimestamp() == null) {
                                LOGGER.debug("Skipping invalid history entry (null song or timestamp)");
                                continue;
                            }
                            
                            stmt.setObject(1, UUID.fromString(userId));
                            stmt.setString(2, entry.getSong().getId());
                            stmt.setTimestamp(3, new Timestamp(entry.getTimestamp().getTime()));
                            stmt.addBatch();
                            batchCount++;
                            
                            if (batchCount >= BATCH_SIZE) {
                                int[] results = stmt.executeBatch();
                                totalProcessed += countSuccessfulInserts(results);
                                LOGGER.debug("Processed batch of {} history entries ({} total)", 
                                    results.length, totalProcessed);
                                batchCount = 0;
                            }
                        }
                        
                        // Process remaining items
                        if (batchCount > 0) {
                            int[] results = stmt.executeBatch();
                            totalProcessed += countSuccessfulInserts(results);
                            LOGGER.debug("Processed final batch of {} history entries", results.length);
                        }
                    }

                    conn.commit();
                    long duration = System.currentTimeMillis() - startTime;
                    LOGGER.info("Listen history save completed in {}ms. Successfully processed {} entries", 
                        duration, totalProcessed);
                    return null;
                } catch (SQLException e) {
                    LOGGER.error("Error saving listen history: {}", e.getMessage(), e);
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(originalAutoCommit);
                }
            });
        }

        public Set<String> getExistingArtists(Set<String> artistNames) throws SQLException {
        return withConnection(conn -> {
            Set<String> existing = new HashSet<>();
            String sql = "SELECT name FROM artists WHERE name = ANY(?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setArray(1, conn.createArrayOf("text", artistNames.toArray()));
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    existing.add(rs.getString("name"));
                }
            }
            return existing;
        });
    }

    public Set<String> getExistingSongIds(Set<String> spotifyIds) throws SQLException {
        return withConnection(conn -> {
            Set<String> existing = new HashSet<>();
            String sql = "SELECT spotify_id FROM songs WHERE spotify_id = ANY(?)";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setArray(1, conn.createArrayOf("text", spotifyIds.toArray()));
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    existing.add(rs.getString("spotify_id"));
                }
            }
            return existing;
        });
    }

    public void saveEnrichedData(UserMusicData enrichedData) throws SQLException {
        withConnection(conn -> {
            boolean originalAutoCommit = conn.getAutoCommit();
            
            try {
                conn.setAutoCommit(false);
                saveArtists(conn, enrichedData.getArtists());
                saveSongs(conn, enrichedData.getSongs());
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

    /**
     * Saves artists and songs in a more efficient batch process
     * @param rawData The raw user data to save
     * @return UserMusicData with the saved artists and songs
     * @throws SQLException if a database error occurs
     */
    public UserMusicData saveSongsAndArtists(UserMusicData rawData) throws SQLException {
        LOGGER.info("Starting optimized saveSongsAndArtists operation for {} songs and {} artists", 
            rawData.getSongs().size(), rawData.getArtists().size());
        long startTime = System.currentTimeMillis();
        
        return withConnection(conn -> {
            UserMusicData savedData = new UserMusicData();
            boolean originalAutoCommit = conn.getAutoCommit();
            
            try {
                conn.setAutoCommit(false);
                
                // Step 1: Batch all artists at once
                LOGGER.info("Step 1: Preparing artist batch...");
                Map<String, String> artistIdMap = new HashMap<>();
                final int ARTIST_BATCH_SIZE = 100;
                
                // First check which artists already exist to avoid unnecessary inserts
                Set<String> artistNames = rawData.getArtists().stream()
                    .filter(a -> a != null && a.getName() != null)
                    .map(Artist::getName)
                    .collect(Collectors.toSet());
                
                LOGGER.info("Checking for {} existing artists", artistNames.size());
                String lookupSql = "SELECT id, name FROM artists WHERE name = ANY(?)";
                try (PreparedStatement stmt = conn.prepareStatement(lookupSql)) {
                    stmt.setArray(1, conn.createArrayOf("text", artistNames.toArray()));
                    ResultSet rs = stmt.executeQuery();
                    int existingCount = 0;
                    while (rs.next()) {
                        String name = rs.getString("name");
                        String id = rs.getObject("id").toString();
                        artistIdMap.put(name, id);
                        
                        // Add to result data
                        Artist savedArtist = new Artist(name);
                        savedArtist.setId(id);
                        savedData.addArtist(savedArtist);
                        existingCount++;
                    }
                    LOGGER.info("Found {} existing artists in database", existingCount);
                }
                
                // Find artists that need to be inserted
                List<Artist> artistsToInsert = rawData.getArtists().stream()
                    .filter(a -> a != null && a.getName() != null && !artistIdMap.containsKey(a.getName()))
                    .collect(Collectors.toList());
                
                LOGGER.info("Preparing to insert {} new artists", artistsToInsert.size());
                if (!artistsToInsert.isEmpty()) {
                    String insertSql = "INSERT INTO artists (id, name) VALUES (CAST(? AS UUID), ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                        int batchCount = 0;
                        int totalInserted = 0;
                        
                        // Pre-generate all UUIDs
                        for (Artist artist : artistsToInsert) {
                            String id = UUID.randomUUID().toString();
                            artist.setId(id);
                            
                            stmt.setString(1, id);
                            stmt.setString(2, artist.getName());
                            stmt.addBatch();
                            
                            // Cache ID mapping
                            artistIdMap.put(artist.getName(), id);
                            
                            // Add to result data
                            Artist savedArtist = new Artist(artist.getName());
                            savedArtist.setId(id);
                            savedData.addArtist(savedArtist);
                            
                            batchCount++;
                            if (batchCount >= ARTIST_BATCH_SIZE) {
                                LOGGER.debug("Executing artist batch of size {}", batchCount);
                                stmt.executeBatch();
                                totalInserted += batchCount;
                                LOGGER.debug("Inserted {} artists so far", totalInserted);
                                batchCount = 0;
                            }
                        }
                        
                        if (batchCount > 0) {
                            LOGGER.debug("Executing final artist batch of size {}", batchCount);
                            stmt.executeBatch();
                            totalInserted += batchCount;
                        }
                        
                        LOGGER.info("Successfully inserted {} new artists in {} ms", 
                            totalInserted, System.currentTimeMillis() - startTime);
                    }
                }
                
                // Step 2: Batch insert all songs
                LOGGER.info("Step 2: Preparing song batch operation...");
                long songStartTime = System.currentTimeMillis();
                
                // Create a map of song keys for deduplication
                Map<String, Song> uniqueSongs = new HashMap<>();
                for (Song song : rawData.getSongs()) {
                    if (song != null && song.getTitle() != null && song.getArtistName() != null) {
                        String key = (song.getTitle() + "|" + song.getArtistName()).toLowerCase();
                        uniqueSongs.putIfAbsent(key, song);
                    }
                }
                LOGGER.info("Deduplicated {} songs to {} unique songs", 
                    rawData.getSongs().size(), uniqueSongs.size());
                
                // Find which songs already exist in the database
                LOGGER.info("Checking for existing songs in database...");
                Map<String, String> songIdMap = new HashMap<>();
                
                String songSelectSql = 
                    "SELECT s.id, s.title, a.name as artist_name " +
                    "FROM songs s JOIN artists a ON s.artist_id = a.id " +
                    "WHERE (a.name, s.title) IN (";
                
                // Build query parameters
                List<Object> songQueryParams = new ArrayList<>();
                StringBuilder paramsBuilder = new StringBuilder();
                
                int paramIndex = 0;
                for (Song song : uniqueSongs.values()) {
                    if (paramIndex > 0) {
                        paramsBuilder.append(",");
                    }
                    paramsBuilder.append("(?,?)");
                    songQueryParams.add(song.getArtistName());
                    songQueryParams.add(song.getTitle());
                    paramIndex += 2;
                }
                
                // Only run query if we have songs to check
                if (!songQueryParams.isEmpty()) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            songSelectSql + paramsBuilder.toString() + ")")) {
                        
                        // Set all parameters
                        for (int i = 0; i < songQueryParams.size(); i++) {
                            stmt.setObject(i + 1, songQueryParams.get(i));
                        }
                        
                        ResultSet rs = stmt.executeQuery();
                        int existingCount = 0;
                        while (rs.next()) {
                            String id = rs.getObject("id").toString();
                            String title = rs.getString("title");
                            String artistName = rs.getString("artist_name");
                            String key = (title + "|" + artistName).toLowerCase();
                            
                            songIdMap.put(key, id);
                            existingCount++;
                        }
                        LOGGER.info("Found {} existing songs in database", existingCount);
                    }
                }
                
                // Find songs that need to be inserted
                List<Song> songsToInsert = uniqueSongs.values().stream()
                    .filter(s -> {
                        String key = (s.getTitle() + "|" + s.getArtistName()).toLowerCase();
                        return !songIdMap.containsKey(key);
                    })
                    .collect(Collectors.toList());
                
                LOGGER.info("Preparing to insert {} new songs", songsToInsert.size());
                if (!songsToInsert.isEmpty()) {
                    // Create a prepared statement for batch insertion
                    String songInsertSql = 
                        "INSERT INTO songs (id, title, artist_id, spotify_id) " +
                        "VALUES (CAST(? AS UUID), ?, CAST(? AS UUID), ?)";
                        
                    try (PreparedStatement stmt = conn.prepareStatement(songInsertSql)) {
                        final int SONG_BATCH_SIZE = 200;
                        int batchCount = 0;
                        int totalInserted = 0;
                        int skippedCount = 0;
                        
                        for (Song song : songsToInsert) {
                            // Get artist ID from our mapping
                            String artistId = artistIdMap.get(song.getArtistName());
                            if (artistId == null) {
                                skippedCount++;
                                continue;
                            }
                            
                            // Generate a UUID for this song
                            String songId = UUID.randomUUID().toString();
                            song.setId(songId);
                            
                            // Add to prepared statement
                            stmt.setString(1, songId);
                            stmt.setString(2, song.getTitle());
                            stmt.setString(3, artistId);
                            stmt.setString(4, song.getSpotifyId());
                            stmt.addBatch();
                            
                            // Add to result data
                            Song savedSong = new Song(song.getTitle(), song.getArtistName());
                            savedSong.setId(songId);
                            savedSong.setSpotifyId(song.getSpotifyId());
                            savedData.addSong(savedSong);
                            
                            // Calculate the key for our map
                            String key = (song.getTitle() + "|" + song.getArtistName()).toLowerCase();
                            songIdMap.put(key, songId);
                            
                            batchCount++;
                            if (batchCount >= SONG_BATCH_SIZE) {
                                LOGGER.debug("Executing song batch of size {}", batchCount);
                                stmt.executeBatch();
                                totalInserted += batchCount;
                                LOGGER.debug("Inserted {} songs so far", totalInserted);
                                batchCount = 0;
                            }
                        }
                        
                        if (batchCount > 0) {
                            LOGGER.debug("Executing final song batch of size {}", batchCount);
                            stmt.executeBatch();
                            totalInserted += batchCount;
                        }
                        
                        LOGGER.info("Successfully inserted {} new songs, skipped {} songs with missing artists", 
                            totalInserted, skippedCount);
                    }
                }
                
                // Step 3: Add existing songs to the result data
                LOGGER.info("Step 3: Adding existing songs to result data...");
                for (Song song : uniqueSongs.values()) {
                    String key = (song.getTitle() + "|" + song.getArtistName()).toLowerCase();
                    String songId = songIdMap.get(key);
                    
                    if (songId != null && !savedData.getSongs().stream()
                            .anyMatch(s -> s.getId() != null && s.getId().equals(songId))) {
                        Song savedSong = new Song(song.getTitle(), song.getArtistName());
                        savedSong.setId(songId);
                        savedSong.setSpotifyId(song.getSpotifyId());
                        savedData.addSong(savedSong);
                    }
                }
                
                long songDuration = System.currentTimeMillis() - songStartTime;
                LOGGER.info("Song operations completed in {} ms", songDuration);
                
                conn.commit();
                long totalDuration = System.currentTimeMillis() - startTime;
                LOGGER.info("Successfully completed saveSongsAndArtists in {} ms. " +
                           "Saved {} artists and {} songs", 
                           totalDuration, savedData.getArtists().size(), savedData.getSongs().size());
                
                return savedData;
            } catch (SQLException e) {
                LOGGER.error("Error saving songs and artists: {}", e.getMessage(), e);
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        });
    }

    public void updateUserPreferences(Map<String, Object> preferences) throws SQLException {
        withConnection(conn -> {
            boolean originalAutoCommit = conn.getAutoCommit();
            
            try {
                conn.setAutoCommit(false);
                
                String sql = """
                    INSERT INTO user_preferences (user_id, type, item_id, score)
                    VALUES (CAST(? AS UUID), ?, ?, ?)
                    ON CONFLICT (user_id, type, item_id) DO UPDATE SET
                        score = EXCLUDED.score
                """;
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    // Save genre preferences
                    @SuppressWarnings("unchecked")
                    List<String> genres = (List<String>) preferences.get("favorite_genres");
                    for (int i = 0; i < genres.size(); i++) {
                        stmt.setObject(1, UUID.fromString(userId));
                        stmt.setString(2, "genre");
                        stmt.setString(3, genres.get(i));
                        stmt.setDouble(4, 1.0 - (i * 0.1)); // Decreasing score by position
                        stmt.addBatch();
                    }
                    
                    // Save artist preferences
                    @SuppressWarnings("unchecked")
                    List<String> artists = (List<String>) preferences.get("favorite_artists");
                    for (int i = 0; i < artists.size(); i++) {
                        stmt.setString(1, userId);
                        stmt.setString(2, "artist");
                        stmt.setString(3, artists.get(i));
                        stmt.setDouble(4, 1.0 - (i * 0.1));
                        stmt.addBatch();
                    }
                    
                    stmt.executeBatch();
                }
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

    // Add cleanup method
    public void cleanup() {
        if (connectionPool != null && !connectionPool.isClosed()) {
            connectionPool.close();
        }
    }
}