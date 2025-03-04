package services;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.SpotifyAccessToken;
import utils.GenreMapper;
import java.io.File;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

// This class is responsible for fetching essential artist and song data for playlist generation
public class GetDataViaSpotifyAPI {

    private static final String SPOTIFY_API_URL = "https://api.spotify.com/v1/search";
    private static final String MUSICBRAINZ_API_URL = "https://musicbrainz.org/ws/2/artist/";
    private static final String LASTFM_API_URL = "http://ws.audioscrobbler.com/2.0/";
    private static final String LASTFM_API_KEY = "c37d56d0eec9000538301510740a3ca8";
    private static final String ACCESS_TOKEN;
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private static final String OFFLINE_CACHE_DIR = "offline_cache";
    private static final String ARTISTS_CACHE_FILE = "artists_cache.json";
    private static final String SONGS_CACHE_FILE = "songs_cache.json";
    private static final String LOCAL_DB_FILE = "local_music_data.json";
    private static boolean isOnline = true;
    private static JSONObject localDatabase;

    // Fetch Spotify access token
    static {
        String token = "";
        try {
            token = SpotifyAccessToken.getAccessToken();
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        ACCESS_TOKEN = token;
    }

    // Database connection details
    private static final String DB_URL = "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres";
    private static final String DB_USER = "postgres.ovinvbshhlfiazazcsaw";
    private static final String DB_PASSWORD = "BS1l7MtXTDZ2pfd5";

    public static void main(String[] args) {
        // Create offline cache directory if it doesn't exist
        createOfflineCacheDirectory();
        
        // Load or initialize local database
        loadLocalDatabase();
        
        // Check internet connectivity
        isOnline = checkInternetConnectivity();
        
        if (isOnline) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Online mode - sync with server and update local cache
                syncWithServer(connection);
                saveToOfflineCache();
            } catch (SQLException e) {
                System.err.println("Database connection error");
                e.printStackTrace();
            }
        } else {
            // Offline mode - use local data only
            System.out.println("Working in offline mode. Using local data...");
            processLocalData();
        }
    }

    private static void createOfflineCacheDirectory() {
        File cacheDir = new File(OFFLINE_CACHE_DIR);
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
    }

    private static boolean checkInternetConnectivity() {
        try {
            URL url = new URL("https://api.spotify.com");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static void loadLocalDatabase() {
        File localDbFile = new File(OFFLINE_CACHE_DIR, LOCAL_DB_FILE);
        if (localDbFile.exists()) {
            try {
                String content = new String(Files.readAllBytes(localDbFile.toPath()));
                localDatabase = new JSONObject(content);
            } catch (Exception e) {
                System.err.println("Error loading local database: " + e.getMessage());
                localDatabase = new JSONObject();
            }
        } else {
            localDatabase = new JSONObject();
            localDatabase.put("artists", new JSONObject());
            localDatabase.put("songs", new JSONObject());
        }
    }

    private static void saveLocalDatabase() {
        try (FileWriter writer = new FileWriter(new File(OFFLINE_CACHE_DIR, LOCAL_DB_FILE))) {
            writer.write(localDatabase.toString(2));
        } catch (Exception e) {
            System.err.println("Error saving local database: " + e.getMessage());
        }
    }

    private static void syncWithServer(Connection connection) throws SQLException {
        // Sync artists
        String artistSql = "SELECT name, spotify_id, spotify_link, image_url, popularity FROM artists";
        try (PreparedStatement stmt = connection.prepareStatement(artistSql);
             ResultSet rs = stmt.executeQuery()) {
            JSONObject artists = localDatabase.getJSONObject("artists");
            while (rs.next()) {
                JSONObject artist = new JSONObject();
                artist.put("name", rs.getString("name"));
                artist.put("spotify_id", rs.getString("spotify_id"));
                artist.put("spotify_link", rs.getString("spotify_link"));
                artist.put("image_url", rs.getString("image_url"));
                artist.put("popularity", rs.getInt("popularity"));
                artists.put(rs.getString("name"), artist);
            }
        }

        // Sync songs
        String songSql = "SELECT title, artist_name, spotify_id, spotify_link, popularity, duration_ms, album_name, release_date FROM songs";
        try (PreparedStatement stmt = connection.prepareStatement(songSql);
             ResultSet rs = stmt.executeQuery()) {
            JSONObject songs = localDatabase.getJSONObject("songs");
            while (rs.next()) {
                JSONObject song = new JSONObject();
                song.put("title", rs.getString("title"));
                song.put("artist_name", rs.getString("artist_name"));
                song.put("spotify_id", rs.getString("spotify_id"));
                song.put("spotify_link", rs.getString("spotify_link"));
                song.put("popularity", rs.getInt("popularity"));
                song.put("duration_ms", rs.getInt("duration_ms"));
                song.put("album_name", rs.getString("album_name"));
                song.put("release_date", rs.getString("release_date"));
                songs.put(rs.getString("title") + "|" + rs.getString("artist_name"), song);
            }
        }

        saveLocalDatabase();
    }

    private static void processLocalData() {
        // Process artists from local database
        JSONObject artists = localDatabase.getJSONObject("artists");
        for (String artistName : artists.keySet()) {
            JSONObject artist = artists.getJSONObject(artistName);
            if (artist.isNull("spotify_id") || artist.isNull("popularity") || artist.isNull("spotify_link")) {
                System.out.println("Artist needs updating: " + artistName);
                // Mark for update when online
                artist.put("needs_update", true);
            }
        }

        // Process songs from local database
        JSONObject songs = localDatabase.getJSONObject("songs");
        for (String key : songs.keySet()) {
            JSONObject song = songs.getJSONObject(key);
            if (song.isNull("spotify_id") || song.isNull("popularity") || song.isNull("duration_ms")) {
                System.out.println("Song needs updating: " + song.getString("title"));
                // Mark for update when online
                song.put("needs_update", true);
            }
        }

        saveLocalDatabase();
    }

    private static void processArtists(Connection connection) throws SQLException {
        String selectSql = "SELECT name FROM artists WHERE spotify_id IS NULL OR popularity IS NULL OR spotify_link IS NULL";
            try (PreparedStatement selectStatement = connection.prepareStatement(selectSql);
                 ResultSet resultSet = selectStatement.executeQuery()) {

                while (resultSet.next()) {
                try {
                    getArtistData(connection, resultSet.getString("name"));
                    Thread.sleep(1000); // Rate limiting
                    } catch (IOException | InterruptedException e) {
                    System.err.println("Error processing artist: " + resultSet.getString("name"));
                        e.printStackTrace();
                    }
                }
            }
    }

    private static void processSongs(Connection connection) throws SQLException {
        String selectSql = "SELECT title, artist_name FROM songs WHERE " +
                          "spotify_id IS NULL OR popularity IS NULL OR duration_ms IS NULL OR " +
                          "spotify_link IS NULL OR album_name IS NULL OR release_date IS NULL";
        try (PreparedStatement selectStatement = connection.prepareStatement(selectSql);
             ResultSet resultSet = selectStatement.executeQuery()) {

            while (resultSet.next()) {
                try {
                    getSongData(connection, resultSet.getString("title"), resultSet.getString("artist_name"));
                    Thread.sleep(1000); // Rate limiting
                } catch (IOException | InterruptedException e) {
                    System.err.println("Error processing song: " + resultSet.getString("title"));
            e.printStackTrace();
        }
    }
        }
    }

    private static void getArtistData(Connection connection, String artistName) throws IOException, InterruptedException, SQLException {
        System.out.println("\nProcessing artist: " + artistName);
        
        if (!isOnline) {
            System.out.println("Offline mode: Using local data for artist: " + artistName);
            return;
        }

        String spotifyId = null;
        String spotifyLink = null;
        String imageUrl = null;
        Integer popularity = null;
        Set<String> genres = new HashSet<>();

        // Try Spotify first
        try {
            SpotifyData spotifyData = getSpotifyData(artistName);
            if (spotifyData != null) {
                spotifyId = spotifyData.id;
                spotifyLink = "https://open.spotify.com/artist/" + spotifyId;
                popularity = spotifyData.popularity;
                imageUrl = spotifyData.imageUrl;
                genres.addAll(spotifyData.genres);
            }
        } catch (Exception e) {
            System.err.println("Error fetching Spotify data: " + e.getMessage());
        }

        // Try MusicBrainz and Last.fm for additional genres
        try {
            List<String> mbGenres = getGenresFromMusicBrainz(artistName);
            genres.addAll(mbGenres);
        } catch (Exception e) {
            System.err.println("Error fetching MusicBrainz data: " + e.getMessage());
        }

        try {
            List<String> lastfmGenres = getGenresFromLastFm(artistName);
            genres.addAll(lastfmGenres);
        } catch (Exception e) {
            System.err.println("Error fetching Last.fm data: " + e.getMessage());
        }

        // Normalize and categorize genres
        Set<String> normalizedGenres = normalizeGenres(genres);
        
        // Save all the data
        saveArtistDataToDatabase(connection, artistName, spotifyId, spotifyLink, imageUrl, popularity, normalizedGenres);
    }

    private static class SpotifyData {
        String id;
        int popularity;
        List<String> genres;
        String imageUrl;

        SpotifyData(String id, int popularity, List<String> genres, String imageUrl) {
            this.id = id;
            this.popularity = popularity;
            this.genres = genres;
            this.imageUrl = imageUrl;
        }
    }

    private static SpotifyData getSpotifyData(String artistName) throws IOException, InterruptedException {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
        HttpClient client = HttpClient.newHttpClient();
        String query = String.format("q=artist:%s&type=artist", URLEncoder.encode(artistName, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SPOTIFY_API_URL + "?" + query))
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject jsonResponse = new JSONObject(response.body());
        JSONArray artists = jsonResponse.getJSONObject("artists").getJSONArray("items");

        if (artists.length() > 0) {
                    JSONObject artist = artists.getJSONObject(0);
                    String id = artist.getString("id");
                    int popularity = artist.getInt("popularity");
                    
                    // Get image URL from images array
                    String imageUrl = null;
                    if (artist.has("images") && artist.getJSONArray("images").length() > 0) {
                        imageUrl = artist.getJSONArray("images").getJSONObject(0).getString("url");
                    }

                    List<String> genres = new ArrayList<>();
                    JSONArray genreArray = artist.getJSONArray("genres");
                    for (int i = 0; i < genreArray.length(); i++) {
                        genres.add(genreArray.getString(i));
                    }
                    
                    return new SpotifyData(id, popularity, genres, imageUrl);
                }
                return null;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
            }
        }
        return null;
    }

    private static List<String> getGenresFromMusicBrainz(String artistName) throws IOException, InterruptedException {
        List<String> genres = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
        HttpClient client = HttpClient.newHttpClient();
        String query = String.format("query=artist:%s&fmt=json", URLEncoder.encode(artistName, StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MUSICBRAINZ_API_URL + "?" + query))
                .header("User-Agent", "GetDataViaSpotifyAPI/1.0 ( petrkudr2@gmail.com )")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject jsonResponse = new JSONObject(response.body());
        if (jsonResponse.has("artists")) {
            JSONArray artists = jsonResponse.getJSONArray("artists");
                    if (artists.length() > 0) {
                        JSONObject artist = artists.getJSONObject(0);
                        if (artist.has("tags")) {
                            JSONArray tags = artist.getJSONArray("tags");
                            for (int i = 0; i < tags.length(); i++) {
                                genres.add(tags.getJSONObject(i).getString("name"));
                            }
                        }
                    }
                }
                return genres;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
            }

        }
        return genres;
    }

    private static List<String> getGenresFromLastFm(String artistName) throws IOException, InterruptedException {
        List<String> genres = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                String query = String.format("method=artist.getinfo&artist=%s&api_key=%s&format=json",
                        URLEncoder.encode(artistName, StandardCharsets.UTF_8),
                        LASTFM_API_KEY);
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(LASTFM_API_URL + "?" + query))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject jsonResponse = new JSONObject(response.body());
                
                if (jsonResponse.has("artist") && !jsonResponse.isNull("artist")) {
                    JSONObject artist = jsonResponse.getJSONObject("artist");
                    if (artist.has("tags") && !artist.isNull("tags")) {
                        JSONObject tags = artist.getJSONObject("tags");
                        if (tags.has("tag")) {
                            JSONArray tagArray = tags.getJSONArray("tag");
                            for (int i = 0; i < tagArray.length(); i++) {
                                genres.add(tagArray.getJSONObject(i).getString("name"));
                            }
                        }
                    }
                }
                return genres;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
            }
        }
        return genres;
    }

    private static Set<String> normalizeGenres(Set<String> genres) {
        return GenreMapper.normalizeGenres(genres);
    }

    private static void saveArtistDataToDatabase(Connection connection, String artistName, 
            String spotifyId, String spotifyLink, String imageUrl, Integer popularity, Set<String> genres) 
            throws SQLException {
        // Update main artist record
        String mainSql = "UPDATE artists SET spotify_id = ?, spotify_link = ?, image_url = ?, popularity = ? WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(mainSql)) {
            statement.setObject(1, spotifyId);
            statement.setObject(2, spotifyLink);
            statement.setObject(3, imageUrl);
            statement.setObject(4, popularity);
            statement.setString(5, artistName);
            statement.executeUpdate();
        }

        // Handle genres with genre categories
        if (!genres.isEmpty()) {
            // Delete existing genres
            String deleteSql = "DELETE FROM artist_genres WHERE artist_name = ?";
            try (PreparedStatement statement = connection.prepareStatement(deleteSql)) {
                statement.setString(1, artistName);
            statement.executeUpdate();
        }

            // Insert new genres and ensure they exist in genre_categories
            String insertGenreCatSql = "INSERT INTO genre_categories (genre) VALUES (?) ON CONFLICT (genre) DO NOTHING";
            String insertArtistGenreSql = "INSERT INTO artist_genres (artist_name, genre) VALUES (?, ?)";
            
            try (PreparedStatement genreCatStmt = connection.prepareStatement(insertGenreCatSql);
                 PreparedStatement artistGenreStmt = connection.prepareStatement(insertArtistGenreSql)) {
                
                for (String genre : genres) {
                    // Ensure genre exists in genre_categories
                    genreCatStmt.setString(1, genre);
                    genreCatStmt.addBatch();

                    // Add to artist_genres
                    artistGenreStmt.setString(1, artistName);
                    artistGenreStmt.setString(2, genre);
                    artistGenreStmt.addBatch();
                }
                
                genreCatStmt.executeBatch();
                artistGenreStmt.executeBatch();
            }
        }

        System.out.println("Artist data saved for: " + artistName + 
                          (spotifyId != null ? " (Spotify ID found)" : "") +
                          (imageUrl != null ? " (Image found)" : "") +
                          (popularity != null ? " (popularity: " + popularity + ")" : "") +
                          (!genres.isEmpty() ? " (genres: " + String.join(", ", genres) + ")" : ""));
    }

    private static void getSongData(Connection connection, String title, String artistName) 
            throws IOException, InterruptedException, SQLException {
        System.out.println("\nProcessing song: " + title + " by " + artistName);
        
        if (!isOnline) {
            System.out.println("Offline mode: Using local data for song: " + title);
            return;
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                String query = String.format("q=track:%s artist:%s&type=track", 
                    URLEncoder.encode(title, StandardCharsets.UTF_8),
                    URLEncoder.encode(artistName, StandardCharsets.UTF_8));
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SPOTIFY_API_URL + "?" + query))
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject jsonResponse = new JSONObject(response.body());
                JSONArray tracks = jsonResponse.getJSONObject("tracks").getJSONArray("items");

                if (tracks.length() > 0) {
                    JSONObject track = tracks.getJSONObject(0);
                    String trackId = track.getString("id");
                    String spotifyLink = "https://open.spotify.com/track/" + trackId;
                    int popularity = track.getInt("popularity");
                    int durationMs = track.getInt("duration_ms");
                    boolean isExplicit = track.getBoolean("explicit");
                    String previewUrl = track.isNull("preview_url") ? null : track.getString("preview_url");
                    
                    // Get album info
                    JSONObject album = track.getJSONObject("album");
                    String albumName = album.getString("name");
                    String releaseDate = album.getString("release_date");
                    
                    // Get image URL
                    String imageUrl = null;
                    if (album.has("images") && album.getJSONArray("images").length() > 0) {
                        imageUrl = album.getJSONArray("images").getJSONObject(0).getString("url");
                    }

                    saveSongDataToDatabase(connection, title, artistName, trackId, spotifyLink, 
                        popularity, durationMs, isExplicit, previewUrl, albumName, releaseDate, imageUrl);
                    
                    // Get and save audio features if needed
                    // saveAudioFeatures(connection, trackId);
                } else {
                    System.out.println("No song found on Spotify: " + title + " by " + artistName);
                    saveSongDataToDatabase(connection, title, artistName, null, null, 
                        null, null, null, null, null, null, null);
                }
                return;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
            }
        }
    }

    private static void saveSongDataToDatabase(Connection connection, String title, String artistName,
            String spotifyId, String spotifyLink, Integer popularity, Integer durationMs, 
            Boolean isExplicit, String previewUrl, String albumName, String releaseDate, String imageUrl) 
            throws SQLException {
        String sql = "UPDATE songs SET spotify_id = ?, spotify_link = ?, popularity = ?, " +
                    "duration_ms = ?, is_explicit = ?, preview_url = ?, " +
                    "album_name = ?, release_date = ?, image_url = ? " +
                    "WHERE title = ? AND artist_name = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, spotifyId);
            statement.setObject(2, spotifyLink);
            statement.setObject(3, popularity);
            statement.setObject(4, durationMs);
            statement.setObject(5, isExplicit);
            statement.setObject(6, previewUrl);
            statement.setObject(7, albumName);
            statement.setObject(8, releaseDate);
            statement.setObject(9, imageUrl);
            statement.setString(10, title);
            statement.setString(11, artistName);
            statement.executeUpdate();
        }

        System.out.println("Song data saved for: " + title + " by " + artistName +
                          (spotifyId != null ? " (Spotify ID found)" : "") +
                          (popularity != null ? " (popularity: " + popularity + ")" : "") +
                          (durationMs != null ? " (duration: " + durationMs/1000 + "s)" : "") +
                          (albumName != null ? " (album: " + albumName + ")" : ""));
    }

    private static void ensureDatabaseSchema(Connection connection) throws SQLException {
        // Check if genre_categories table exists and create if not
        String createGenreCategoriesTable = """
            CREATE TABLE IF NOT EXISTS genre_categories (
                genre varchar(255) PRIMARY KEY,
                parent_genre varchar(255),
                category varchar(255),
                CONSTRAINT valid_parent_genre FOREIGN KEY (parent_genre) 
                REFERENCES genre_categories(genre)
            )""";

        // Check if artist_genres table exists and create if not
        String createArtistGenresTable = """
            CREATE TABLE IF NOT EXISTS artist_genres (
                artist_name varchar(255),
                genre varchar(255),
                PRIMARY KEY (artist_name, genre),
                FOREIGN KEY (genre) REFERENCES genre_categories(genre)
            )""";

        // Add new columns to artists table if they don't exist
        String alterArtistsTable = """
            DO $$ 
            BEGIN 
                BEGIN
                    ALTER TABLE artists ADD COLUMN IF NOT EXISTS spotify_link text;
                    ALTER TABLE artists ADD COLUMN IF NOT EXISTS image_url text;
                EXCEPTION WHEN OTHERS THEN 
                    NULL;
                END;
            END $$;
            """;

        // Add new columns to songs table if they don't exist
        String alterSongsTable = """
            DO $$ 
            BEGIN 
                BEGIN
                    ALTER TABLE songs ADD COLUMN IF NOT EXISTS spotify_link text;
                    ALTER TABLE songs ADD COLUMN IF NOT EXISTS album_name text;
                    ALTER TABLE songs ADD COLUMN IF NOT EXISTS release_date date;
                    ALTER TABLE songs ADD COLUMN IF NOT EXISTS is_explicit boolean;
                    ALTER TABLE songs ADD COLUMN IF NOT EXISTS preview_url text;
                    ALTER TABLE songs ADD COLUMN IF NOT EXISTS image_url text;
                EXCEPTION WHEN OTHERS THEN 
                    NULL;
                END;
            END $$;
            """;

        try (PreparedStatement stmt = connection.prepareStatement(createGenreCategoriesTable)) {
            stmt.execute();
        }
        try (PreparedStatement stmt = connection.prepareStatement(createArtistGenresTable)) {
            stmt.execute();
        }
        try (PreparedStatement stmt = connection.prepareStatement(alterArtistsTable)) {
            stmt.execute();
        }
        try (PreparedStatement stmt = connection.prepareStatement(alterSongsTable)) {
            stmt.execute();
        }
    }
}
