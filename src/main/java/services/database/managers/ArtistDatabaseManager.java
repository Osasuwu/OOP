package services.database.managers;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import models.Artist;
import models.Song;
import models.User;
import utils.GenreMapper;

public class ArtistDatabaseManager extends BaseDatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArtistDatabaseManager.class);
    private final Gson gson = new Gson();
    private final Path storageDir;

    
    public ArtistDatabaseManager(boolean isOnline, User user) {
        super(isOnline, user);
        this.storageDir = Paths.get(getOfflineStoragePath(), "artists");
        if (isOfflineMode()) {
            try {
                Files.createDirectories(storageDir);
            } catch (IOException e) {
                LOGGER.error("Failed to create offline storage directory", e);
            }
        }
    }

    public Map<String, UUID> getExistingArtistIds(Connection conn, List<Artist> artists) throws SQLException {
        if (isOfflineMode()) {
            return getExistingArtistIdsOffline(artists);
        }

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
        return artistIds;
    }

    private Map<String, UUID> getExistingArtistIdsOffline(List<Artist> artists) {
        Map<String, UUID> artistIds = loadArtistIdsFromFile();
        artists.stream()
            .filter(a -> a != null && a.getName() != null)
            .forEach(artist -> {
                if (!artistIds.containsKey(artist.getName())) {
                    artistIds.put(artist.getName(), UUID.randomUUID());
                }
            });
        saveArtistIdsToFile(artistIds);
        return artistIds;
    }

    private Map<String, UUID> loadArtistIdsFromFile() {
        Path filePath = storageDir.resolve("artist_ids.json");
        if (Files.exists(filePath)) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
                return gson.fromJson(reader, new TypeToken<Map<String, UUID>>(){}.getType());
            } catch (IOException e) {
                LOGGER.error("Failed to load artist IDs from file", e);
            }
        }
        return new HashMap<>();
    }

    private void saveArtistIdsToFile(Map<String, UUID> artistIds) {
        try (Writer writer = Files.newBufferedWriter(storageDir.resolve("artist_ids.json"))) {
            gson.toJson(artistIds, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save artist IDs to file", e);
        }
    }

    /**
     * Saves a list of artists to the database or offline storage using batch for performance improvement.
     * @param conn The database connection.
     * @param artists The list of artists to save.
     * @throws SQLException If an error occurs while saving to the database.
     */
    public void saveArtists(Connection conn, List<Artist> artists) throws SQLException {
        if (isOfflineMode()) {
            saveArtistsOffline(artists);
            return;
        }

        // First, get all existing artists by name to avoid conflicts with the unique constraint
        Map<String, UUID> existingArtistsByName = getExistingArtistsByName(conn, artists);
        
        // Update UUIDs for any artists with matching names in the database
        for (Artist artist : artists) {
            if (existingArtistsByName.containsKey(artist.getName().toLowerCase())) {
                // Use the existing UUID from database instead of the one we generated
                artist.setId(existingArtistsByName.get(artist.getName().toLowerCase()).toString());
                LOGGER.debug("Using existing ID for artist '{}': {}", artist.getName(), artist.getId());
            }
        }

        String sql = """
            INSERT INTO artists (id, name, spotify_id, popularity, spotify_link, image_url)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET 
            name = EXCLUDED.name,
            spotify_id = CASE WHEN EXCLUDED.spotify_id IS NOT NULL THEN EXCLUDED.spotify_id ELSE artists.spotify_id END,
            popularity = CASE WHEN EXCLUDED.popularity > 0 THEN EXCLUDED.popularity ELSE artists.popularity END,
            spotify_link = CASE WHEN EXCLUDED.spotify_link IS NOT NULL THEN EXCLUDED.spotify_link ELSE artists.spotify_link END,
            image_url = CASE WHEN EXCLUDED.image_url IS NOT NULL THEN EXCLUDED.image_url ELSE artists.image_url END
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int batchCount = 0;
            for (Artist artist : artists) {
                if (artist.getId() == null) {
                    continue;
                }
                stmt.setObject(1, UUID.fromString(artist.getId()));
                stmt.setString(2, artist.getName());
                stmt.setString(3, artist.getSpotifyId());
                stmt.setInt(4, artist.getPopularity());
                stmt.setString(5, artist.getSpotifyLink());
                stmt.setString(6, artist.getImageUrl());
                stmt.addBatch();
                batchCount++;
                
                if (batchCount % 100 == 0) {
                    stmt.executeBatch(); // Execute every 100 records to avoid memory issues
                    LOGGER.info("Saved {}/{} artists", batchCount, artists.size());
                }
            }
            if (batchCount > 0) {
                stmt.executeBatch();
                LOGGER.info("Saved all {}/{} artists", batchCount, artists.size());
            }
        } catch (SQLException e) {
            LOGGER.error("Error saving artists to database", e);
            throw e;
        }
    }

    /**
     * Gets existing artists by name from the database
     * @param conn The database connection
     * @param artists The list of artists to check
     * @return Map of artist names (lowercase) to their UUIDs in the database
     * @throws SQLException If an error occurs while querying the database
     */
    private Map<String, UUID> getExistingArtistsByName(Connection conn, List<Artist> artists) throws SQLException {
        Map<String, UUID> existingArtists = new HashMap<>();

        // Extract all artist names
        Set<String> artistNames = artists.stream()
            .filter(a -> a != null && a.getName() != null)
            .map(Artist::getName)
            .collect(Collectors.toSet());
        
        if (artistNames.isEmpty()) {
            return existingArtists;
        }

        // Query database for existing artists with these names
        String sql = "SELECT id, name FROM artists WHERE lower(name) = ANY(?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            String[] nameArray = artistNames.stream()
                .map(String::toLowerCase)
                .toArray(String[]::new);
            stmt.setArray(1, conn.createArrayOf("text", nameArray));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name").toLowerCase();
                    UUID id = (UUID) rs.getObject("id");
                    existingArtists.put(name, id);
                }
            }
        }
        
        LOGGER.debug("Found {} existing artists by name in database", existingArtists.size());
        return existingArtists;
    }

    private void saveArtistsOffline(List<Artist> artists) {
        try {
            Map<String, Artist> artistsById = loadArtistsFromFile();
            
            // Add/update artists in the map
            for (Artist artist : artists) {
                if (artist != null && artist.getId() != null) {
                    artistsById.put(artist.getId(), artist);
                }
            }
            
            // Write the updated map back to file
            saveArtistsToFile(artistsById);
            LOGGER.info("Saved {} artists in offline mode", artists.size());
        } catch (Exception e) {
            LOGGER.error("Error saving artists in offline mode", e);
        }
    }

    private Map<String, Artist> loadArtistsFromFile() {
        Path filePath = storageDir.resolve("artists.json");
        if (Files.exists(filePath)) {
            try (Reader reader = Files.newBufferedReader(filePath)) {
                return gson.fromJson(reader, new TypeToken<Map<String, Artist>>(){}.getType());
            } catch (IOException e) {
                LOGGER.error("Failed to load artists from file", e);
            }
        }
        return new HashMap<>();
    }

    private void saveArtistsToFile(Map<String, Artist> artists) {
        try (Writer writer = Files.newBufferedWriter(storageDir.resolve("artists.json"))) {
            gson.toJson(artists, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save artists to file", e);
        }
    }

    /**
     * Saves artist genres to the database or offline storage.
     * @param conn The database connection.
     * @param artists The list of artists with genres to save.
     * @throws SQLException If an error occurs while saving to the database.
     */
    public void saveArtistGenres(Connection conn, List<Artist> artists) throws SQLException {
        if (isOfflineMode()) {
            saveArtistGenresOffline(artists);
            return;
        }

        String sql = """
            INSERT INTO artist_genres (artist_name, genre)
            VALUES (?, ?)
            ON CONFLICT (artist_name, genre) DO NOTHING
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Artist artist : artists) {
                if (artist.getName() == null || artist.getGenres() == null) {
                    continue;
                }
                for (String genre : artist.getGenres()) {
                    stmt.setString(1, artist.getName());;
                    stmt.setString(2, GenreMapper.normalizeGenre(genre));
                    stmt.addBatch();
                }
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            LOGGER.error("Error saving artist genres to database", e);
            throw e;
        }
    }

    private void saveArtistGenresOffline(List<Artist> artists) {
        try {
            Map<String, Artist> artistsById = loadArtistsFromFile();
            
            // Add/update genres in the map
            for (Artist artist : artists) {
                if (artist != null && artist.getId() != null) {
                    Artist existingArtist = artistsById.get(artist.getId());
                    if (existingArtist != null) {
                        existingArtist.setGenres(artist.getGenres());
                    }
                }
            }
            
            // Write the updated map back to file
            saveArtistsToFile(artistsById);
            LOGGER.info("Saved {} artist genres in offline mode", artists.size());
        } catch (Exception e) {
            LOGGER.error("Error saving artist genres in offline mode", e);
        }
    }

    public List<Artist> loadArtists(Connection conn) throws SQLException {
        List<Artist> artists = new ArrayList<>();
        String sql = "SELECT id, name, image_url FROM artists WHERE user_id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Artist artist = new Artist(rs.getString("name"));
                    artist.setId(rs.getString("id"));
                    artist.setSpotifyId(rs.getString("spotify_id"));
                    artist.setSpotifyLink(rs.getString("spotify_link"));
                    artist.setPopularity(rs.getInt("popularity"));
                    artist.setImageUrl(rs.getString("image_url"));
                    artists.add(artist);
                }
            }
        }

        String genreSql = "SELECT artist_id, genre FROM artist_genres WHERE artist_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(genreSql)) {
            for (Artist artist : artists) {
                stmt.setString(1, artist.getId());
                try (ResultSet rs = stmt.executeQuery()) {
                    List<String> genres = new ArrayList<>();
                    while (rs.next()) {
                        genres.add(rs.getString("genre"));
                    }
                    artist.setGenres(genres);
                }
            }
        }
        
        return artists;
    }

    public Artist getArtistBySong(Connection conn, Song song) throws SQLException {
        // Adjust the query according to your actual database schema.
        String query = "SELECT id, name FROM artists WHERE id = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            // Assuming song.getArtistId() returns a String that can be converted to UUID.
            stmt.setObject(1, UUID.fromString(song.getArtist().getId()));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Artist artist = new Artist();
                    artist.setId(rs.getString("id"));  // Assuming 'id' is stored as a UUID converted to String.
                    artist.setName(rs.getString("name"));
                    // Set any additional fields for Artist as needed.
                    return artist;
                }
            }
        }
        
        return null;  // Return null if no matching artist is found.
    }
}
