package services.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.json.JSONObject;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import services.offline.OfflineDataManager;

public class MusicDatabaseManager {
    private static final String DB_URL = "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres";
    private static final String DB_USER = "postgres.ovinvbshhlfiazazcsaw";
    private static final String DB_PASSWORD = "BS1l7MtXTDZ2pfd5";
    private static final String CACHE_DIR = "cache";
    private static final String CACHE_FILE = "music_cache.json";
    private static final int CACHE_SIZE_LIMIT = 1000; // Maximum number of items to cache

    private JSONObject cache;
    private boolean isOnline;
    private String userId;
    private OfflineDataManager offlineManager;

    public MusicDatabaseManager(boolean isOnline, String userId) {
        this.isOnline = isOnline;
        this.userId = userId;
        this.offlineManager = new OfflineDataManager(userId);
        loadCache();
    }

    private void loadCache() {
        File cacheFile = new File(CACHE_DIR, CACHE_FILE);
        if (cacheFile.exists()) {
            try {
                String content = new String(Files.readAllBytes(cacheFile.toPath()));
                cache = new JSONObject(content);
            } catch (Exception e) {
                cache = new JSONObject();
            }
        } else {
            cache = new JSONObject();
        }
    }

    public void saveCache() {
        try {
            File cacheDir = new File(CACHE_DIR);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(new File(CACHE_DIR, CACHE_FILE))) {
                writer.write(cache.toString(2));
            }
        } catch (Exception e) {
            System.err.println("Error saving cache: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getTopArtists(int limit) throws SQLException {
        if (!isOnline) {
            return getTopArtistsFromCache(limit);
        }

        String sql = """
            SELECT a.name, a.spotify_id, a.popularity, a.image_url,
                   COUNT(DISTINCT s.id) as song_count,
                   AVG(s.popularity) as avg_song_popularity
            FROM artists a
            LEFT JOIN songs s ON a.name = s.artist_name
            GROUP BY a.name, a.spotify_id, a.popularity, a.image_url
            ORDER BY a.popularity DESC, song_count DESC
            LIMIT ?
        """;

        List<Map<String, Object>> artists = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> artist = new HashMap<>();
                artist.put("name", rs.getString("name"));
                artist.put("spotify_id", rs.getString("spotify_id"));
                artist.put("popularity", rs.getInt("popularity"));
                artist.put("image_url", rs.getString("image_url"));
                artist.put("song_count", rs.getInt("song_count"));
                artist.put("avg_song_popularity", rs.getDouble("avg_song_popularity"));
                artists.add(artist);
            }
        }
        return artists;
    }

    public List<Map<String, Object>> getTopSongs(int limit) throws SQLException {
        if (!isOnline) {
            return getTopSongsFromCache(limit);
        }

        String sql = """
            SELECT s.title, s.artist_name, s.spotify_id, s.popularity, 
                   s.duration_ms, s.album_name, s.release_date, s.image_url,
                   COUNT(*) OVER (PARTITION BY s.artist_name) as artist_song_count
            FROM songs s
            ORDER BY s.popularity DESC, artist_song_count DESC
            LIMIT ?
        """;

        List<Map<String, Object>> songs = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> song = new HashMap<>();
                song.put("title", rs.getString("title"));
                song.put("artist_name", rs.getString("artist_name"));
                song.put("spotify_id", rs.getString("spotify_id"));
                song.put("popularity", rs.getInt("popularity"));
                song.put("duration_ms", rs.getInt("duration_ms"));
                song.put("album_name", rs.getString("album_name"));
                song.put("release_date", rs.getString("release_date"));
                song.put("image_url", rs.getString("image_url"));
                song.put("artist_song_count", rs.getInt("artist_song_count"));
                songs.add(song);
            }
        }
        return songs;
    }

    private List<Map<String, Object>> getTopArtistsFromCache(int limit) {
        // Implementation for getting top artists from cache
        // This would use the cache data structure we created
        return new ArrayList<>();
    }

    private List<Map<String, Object>> getTopSongsFromCache(int limit) {
        // Implementation for getting top songs from cache
        // This would use the cache data structure we created
        return new ArrayList<>();
    }

    public void updateArtistData(String artistName, Map<String, Object> data) throws SQLException {
        if (!isOnline) {
            updateArtistCache(artistName, data);
            return;
        }

        // Implementation for updating artist data in database
    }

    public void updateSongData(String title, String artistName, Map<String, Object> data) throws SQLException {
        if (!isOnline) {
            updateSongCache(title, artistName, data);
            return;
        }

        // Implementation for updating song data in database
    }

    private void updateArtistCache(String artistName, Map<String, Object> data) {
        // Implementation for updating artist data in cache
    }

    private void updateSongCache(String title, String artistName, Map<String, Object> data) {
        // Implementation for updating song data in cache
    }

    public Map<String, Object> getUserPreferences() throws SQLException {
        if (!isOnline) {
            return offlineManager.getUserPreferences();
        }

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
            WHERE user_id = ?
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Map<String, Object> preferences = new HashMap<>();
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
                return preferences;
            }
        }
        return new HashMap<>();
    }

    public List<Map<String, Object>> getRecommendedSongs(int limit) throws SQLException {
        if (!isOnline) {
            return getRecommendedSongsFromCache(limit);
        }

        String sql = """
            WITH user_prefs AS (
                SELECT favorite_genres, favorite_artists, disliked_genres, disliked_artists
                FROM user_preferences
                WHERE user_id = ?
            ),
            user_history AS (
                SELECT song_id, COUNT(*) as play_count
                FROM listen_history
                WHERE user_id = ?
                GROUP BY song_id
            ),
            song_scores AS (
                SELECT 
                    s.id,
                    s.title,
                    s.artist_name,
                    s.spotify_id,
                    s.popularity,
                    s.duration_ms,
                    s.album_name,
                    s.release_date,
                    s.image_url,
                    sf.danceability,
                    sf.energy,
                    sf.valence,
                    sf.tempo,
                    CASE 
                        WHEN ag.genre = ANY(up.favorite_genres) THEN 2
                        WHEN ag.genre = ANY(up.disliked_genres) THEN -2
                        ELSE 0
                    END as genre_score,
                    CASE 
                        WHEN s.artist_name = ANY(up.favorite_artists) THEN 2
                        WHEN s.artist_name = ANY(up.disliked_artists) THEN -2
                        ELSE 0
                    END as artist_score,
                    COALESCE(uh.play_count, 0) as play_count
                FROM songs s
                JOIN song_features sf ON s.id = sf.song_id
                JOIN artist_genres ag ON s.artist_name = ag.artist_name
                CROSS JOIN user_prefs up
                LEFT JOIN user_history uh ON s.id = uh.song_id
            )
            SELECT *
            FROM song_scores
            ORDER BY 
                (genre_score + artist_score + (popularity * 0.1) + (play_count * 0.5)) DESC,
                RANDOM()
            LIMIT ?
        """;

        List<Map<String, Object>> songs = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, userId);
            stmt.setInt(3, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> song = new HashMap<>();
                song.put("id", rs.getString("id"));
                song.put("title", rs.getString("title"));
                song.put("artist_name", rs.getString("artist_name"));
                song.put("spotify_id", rs.getString("spotify_id"));
                song.put("popularity", rs.getInt("popularity"));
                song.put("duration_ms", rs.getInt("duration_ms"));
                song.put("album_name", rs.getString("album_name"));
                song.put("release_date", rs.getString("release_date"));
                song.put("image_url", rs.getString("image_url"));
                song.put("danceability", rs.getDouble("danceability"));
                song.put("energy", rs.getDouble("energy"));
                song.put("valence", rs.getDouble("valence"));
                song.put("tempo", rs.getDouble("tempo"));
                song.put("genre_score", rs.getDouble("genre_score"));
                song.put("artist_score", rs.getDouble("artist_score"));
                song.put("play_count", rs.getInt("play_count"));
                songs.add(song);
            }
        }
        return songs;
    }

    public List<Map<String, Object>> getSimilarArtists(String artistName, int limit) throws SQLException {
        if (!isOnline) {
            return getSimilarArtistsFromCache(artistName, limit);
        }

        String sql = """
            WITH target_artist_genres AS (
                SELECT genre
                FROM artist_genres
                WHERE artist_name = ?
            ),
            artist_similarity AS (
                SELECT 
                    a.name,
                    a.spotify_id,
                    a.popularity,
                    a.image_url,
                    COUNT(DISTINCT ag.genre) as matching_genres,
                    COUNT(DISTINCT ag.genre)::float / 
                        (SELECT COUNT(*) FROM target_artist_genres) as genre_similarity
                FROM artists a
                JOIN artist_genres ag ON a.name = ag.artist_name
                WHERE ag.genre IN (SELECT genre FROM target_artist_genres)
                AND a.name != ?
                GROUP BY a.name, a.spotify_id, a.popularity, a.image_url
            )
            SELECT *
            FROM artist_similarity
            ORDER BY genre_similarity DESC, popularity DESC
            LIMIT ?
        """;

        List<Map<String, Object>> artists = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, artistName);
            stmt.setString(2, artistName);
            stmt.setInt(3, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> artist = new HashMap<>();
                artist.put("name", rs.getString("name"));
                artist.put("spotify_id", rs.getString("spotify_id"));
                artist.put("popularity", rs.getInt("popularity"));
                artist.put("image_url", rs.getString("image_url"));
                artist.put("matching_genres", rs.getInt("matching_genres"));
                artist.put("genre_similarity", rs.getDouble("genre_similarity"));
                artists.add(artist);
            }
        }
        return artists;
    }

    public void updateUserPreferences(Map<String, Object> preferences) throws SQLException {
        if (!isOnline) {
            updateUserPreferencesCache(preferences);
            return;
        }

        String sql = """
            INSERT INTO user_preferences (
                user_id, favorite_genres, favorite_artists, favorite_songs,
                disliked_genres, disliked_artists, disliked_songs,
                preferred_tempo_range, preferred_danceability, preferred_energy, preferred_valence
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE SET
                favorite_genres = EXCLUDED.favorite_genres,
                favorite_artists = EXCLUDED.favorite_artists,
                favorite_songs = EXCLUDED.favorite_songs,
                disliked_genres = EXCLUDED.disliked_genres,
                disliked_artists = EXCLUDED.disliked_artists,
                disliked_songs = EXCLUDED.disliked_songs,
                preferred_tempo_range = EXCLUDED.preferred_tempo_range,
                preferred_danceability = EXCLUDED.preferred_danceability,
                preferred_energy = EXCLUDED.preferred_energy,
                preferred_valence = EXCLUDED.preferred_valence
        """;

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setArray(2, (java.sql.Array) preferences.get("favorite_genres"));
            stmt.setArray(3, (java.sql.Array) preferences.get("favorite_artists"));
            stmt.setArray(4, (java.sql.Array) preferences.get("favorite_songs"));
            stmt.setArray(5, (java.sql.Array) preferences.get("disliked_genres"));
            stmt.setArray(6, (java.sql.Array) preferences.get("disliked_artists"));
            stmt.setArray(7, (java.sql.Array) preferences.get("disliked_songs"));
            stmt.setArray(8, (java.sql.Array) preferences.get("preferred_tempo_range"));
            stmt.setDouble(9, (Double) preferences.get("preferred_danceability"));
            stmt.setDouble(10, (Double) preferences.get("preferred_energy"));
            stmt.setDouble(11, (Double) preferences.get("preferred_valence"));
            stmt.executeUpdate();
        }
    }

    private Map<String, Object> getUserPreferencesFromCache() {
        // Implementation for getting user preferences from cache
        return new HashMap<>();
    }

    private void updateUserPreferencesCache(Map<String, Object> preferences) {
        // Implementation for updating user preferences in cache
    }
} 