package services.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import services.database.OfflineDataManager;

public class MusicDatabaseManager {
    private static final String DB_URL = "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres";
    private static final String DB_USER = "postgres.ovinvbshhlfiazazcsaw";
    private static final String DB_PASSWORD = "BS1l7MtXTDZ2pfd5";

    private boolean isOnline;
    private String userId;
    private OfflineDataManager offlineManager;

    public MusicDatabaseManager(boolean isOnline, String userId) {
        this.isOnline = isOnline;
        this.userId = userId;
        this.offlineManager = new OfflineDataManager(userId);
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
}