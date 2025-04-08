package services.database;

    import java.sql.Connection;
    import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
    import java.util.List;
    import java.util.Map;
    
    import org.slf4j.Logger;
    import org.slf4j.LoggerFactory;
    
    import com.zaxxer.hikari.HikariConfig;
    import com.zaxxer.hikari.HikariDataSource;

    import models.Artist; // Added this import for Song.
    import models.Song; // Added this import for Artist.
    import models.User;
    import models.UserMusicData;
    import services.database.managers.ArtistDatabaseManager;
    import services.database.managers.PlayHistoryManager;
    import services.database.managers.SongDatabaseManager;
    import services.database.managers.UserPreferenceManager;
    
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
    
            // Disable logging to console and other optional settings
            config.setInitializationFailTimeout(-1);
            config.setRegisterMbeans(false);
    
            // Add reconnection properties
            config.addDataSourceProperty("reWriteBatchedInserts", "true");
            config.addDataSourceProperty("socketTimeout", "30");
            config.addDataSourceProperty("connectTimeout", "10");
    
            connectionPool = new HikariDataSource(config);
        }
    
        private final boolean isOnline;
        private final User user;
        private final ArtistDatabaseManager artistManager;
        private final SongDatabaseManager songManager;
        private final PlayHistoryManager playHistoryManager;
        private final UserPreferenceManager preferenceManager;
    
        public MusicDatabaseManager(boolean isOnline, User user) {
            this.isOnline = isOnline;
            this.user = user;
            this.artistManager = new ArtistDatabaseManager(isOnline, user);
            this.songManager = new SongDatabaseManager(isOnline, user);
            this.playHistoryManager = new PlayHistoryManager(isOnline, user);
            this.preferenceManager = new UserPreferenceManager(isOnline, user);
            
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
    
        public void saveUserData(UserMusicData userData) throws SQLException {
            withConnection(conn -> {
                boolean originalAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    artistManager.saveArtists(conn, userData.getArtists());
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
    
        public java.util.Map<String, java.util.List<Object>> getCurrentUserPreferences() {
            // Build a preferences map with keys as the name of the object and values as lists of objects.
            // Here we provide empty lists for "songs", "artists", "genres", and "moods".
            Map<String, List<Object>> preferences = new HashMap<>();
            preferences.put("songs", new ArrayList<>());
            preferences.put("artists", new ArrayList<>());
            preferences.put("genres", new ArrayList<>());
            preferences.put("moods", new ArrayList<>());
            return preferences;
        }
        
        public UserMusicData loadUserData() {
            // Create a new instance of UserMusicData, ensuring all collections are initialized (if needed)
            UserMusicData data = new UserMusicData();
            return data;
        }
    
    
    }