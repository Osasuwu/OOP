package services;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.UUID;
import java.nio.file.Path;

import models.UserMusicData;
import models.Song;
import models.Artist;
import models.PlayHistory;
import services.importer.ImportException;

/**
 * Service for importing data directly to the database
 * Adapted from utils.ConvertCSVtoSQL with improvements
 */
public class DatabaseImportService {
    private static final String DB_URL = "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres";
    private static final String DB_USER = "postgres.ovinvbshhlfiazazcsaw";
    private static final String DB_PASSWORD = "BS1l7MtXTDZ2pfd5";
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("\"MMMM d, yyyy 'at' hh:mma\"", Locale.ENGLISH);

    /**
     * Import CSV data into the database
     * @param csvPath Path to the CSV file
     * @param username Username for the database
     * @param email User email for the database
     * @return UserMusicData object with imported data
     */
    public UserMusicData importCSVtoDatabase(Path csvPath, String username, String email) throws ImportException {
        UserMusicData userData = new UserMusicData();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            UUID userId = getUserId(conn, username, email);
            if (userId == null) {
                userId = createUser(conn, username, email);
            }

            try (BufferedReader br = new BufferedReader(new FileReader(csvPath.toFile()))) {
                String line;
                int count = 0;
                
                while ((line = br.readLine()) != null) {
                    String[] values = parseCSVLine(line);
                    
                    // Make sure we have the expected format
                    if (values.length < 5) continue;
                    
                    String dateTime = values[0];
                    String songName = values[1];
                    String artistName = values[2];
                    String spotifySongID = values[3];
                    String spotifyLink = values[4];

                    try {
                        Timestamp timestamp = new Timestamp(dateFormat.parse(dateTime).getTime());

                        // Get or create artist
                        UUID artistId = getArtistId(conn, artistName);
                        if (artistId == null) {
                            artistId = createArtist(conn, artistName);
                        }
                        
                        // Add to our model objects
                        Artist artist = new Artist(artistName);
                        artist.setId(artistId.toString());
                        userData.addArtist(artist);

                        // Get or create song
                        UUID songId = getSongId(conn, spotifySongID);
                        if (songId == null) {
                            songId = createSong(conn, artistId, spotifySongID, songName, spotifyLink);
                        }
                        
                        // Add to our model objects
                        Song song = new Song(songName, artistName);
                        song.setSpotifyId(spotifySongID);
                        userData.addSong(song);

                        // Create listen history entry
                        if (!listenHistoryExists(conn, userId, songId, timestamp)) {
                            insertListenHistory(conn, userId, songId, timestamp);
                            count++;
                            
                            // Add to our model objects
                            PlayHistory playHistory = new PlayHistory(song, dateFormat.parse(dateTime));
                            userData.getPlayHistory().add(playHistory);
                        }
                    } catch (ParseException e) {
                        // Skip entries with unparseable dates
                        System.err.println("Could not parse date: " + dateTime);
                    }
                }
                
                System.out.println("Inserted " + count + " listen history entries");
            }
        } catch (SQLException | IOException e) {
            throw new ImportException("Database import error: " + e.getMessage(), e);
        }
        
        return userData;
    }

    private UUID getUserId(Connection conn, String username, String email) throws SQLException {
        String query = "SELECT id FROM users WHERE username = ? AND email = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("id"));
            }
        }
        return null;
    }

    private UUID createUser(Connection conn, String username, String email) throws SQLException {
        String query = "INSERT INTO users (id, username, email) VALUES (?, ?, ?)";
        UUID userId = UUID.randomUUID();
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setObject(1, userId);
            stmt.setString(2, username);
            stmt.setString(3, email);
            stmt.executeUpdate();
        }
        return userId;
    }

    private UUID getArtistId(Connection conn, String artistName) throws SQLException {
        String query = "SELECT id FROM artists WHERE name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, artistName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("id"));
            }
        }
        return null;
    }

    private UUID createArtist(Connection conn, String artistName) throws SQLException {
        String query = "INSERT INTO artists (id, name) VALUES (?, ?)";
        UUID artistId = UUID.randomUUID();
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setObject(1, artistId);
            stmt.setString(2, artistName);
            stmt.executeUpdate();
        }
        return artistId;
    }

    private UUID getSongId(Connection conn, String spotifyId) throws SQLException {
        String query = "SELECT id FROM songs WHERE spotify_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, spotifyId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return UUID.fromString(rs.getString("id"));
            }
        }
        return null;
    }

    private UUID createSong(Connection conn, UUID artistId, String spotifyId, String title, String spotifyLink) throws SQLException {
        String query = "INSERT INTO songs (id, artist_id, spotify_id, title, spotify_link) VALUES (?, ?, ?, ?, ?)";
        UUID songId = UUID.randomUUID();
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setObject(1, songId);
            stmt.setObject(2, artistId);
            stmt.setString(3, spotifyId);
            stmt.setString(4, title);
            stmt.setString(5, spotifyLink);
            stmt.executeUpdate();
        }
        return songId;
    }

    private boolean listenHistoryExists(Connection conn, UUID userId, UUID songId, Timestamp listenedAt) throws SQLException {
        String query = "SELECT id FROM listen_history WHERE user_id = ? AND song_id = ? AND listened_at = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, songId);
            stmt.setTimestamp(3, listenedAt);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    private void insertListenHistory(Connection conn, UUID userId, UUID songId, Timestamp listenedAt) throws SQLException {
        String query = "INSERT INTO listen_history (id, user_id, song_id, listened_at) VALUES (?, ?, ?, ?)";
        UUID listenHistoryId = UUID.randomUUID();
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setObject(1, listenHistoryId);
            stmt.setObject(2, userId);
            stmt.setObject(3, songId);
            stmt.setTimestamp(4, listenedAt);
            stmt.executeUpdate();
        }
    }

    private String[] parseCSVLine(String line) {
        // Split the line by comma, but ignore commas inside double quotes
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
    }
}
