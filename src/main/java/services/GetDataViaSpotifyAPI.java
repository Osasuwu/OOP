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
import org.json.JSONArray;
import org.json.JSONObject;
import utils.SpotifyAccessToken;

// This class is responsible for fetching genre data for artists from Spotify and MusicBrainz APIs
public class GetDataViaSpotifyAPI {

    // Spotify and MusicBrainz API URLs
    private static final String SPOTIFY_API_URL = "https://api.spotify.com/v1/search";
    private static final String MUSICBRAINZ_API_URL = "https://musicbrainz.org/ws/2/artist/";
    private static final String ACCESS_TOKEN;

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
        
        // Fetch artists with missing genre data from the database
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String selectSql = "SELECT name FROM artists WHERE genre IS NULL";
            try (PreparedStatement selectStatement = connection.prepareStatement(selectSql);
                 ResultSet resultSet = selectStatement.executeQuery()) {

                // Iterate over the result set and fetch genre data for each artist
                while (resultSet.next()) {
                    String artistName = resultSet.getString("name");

                    try {
                        String genre = getGenre(connection, artistName);
                        if (genre != null) {
                            saveGenreToDatabase(connection, artistName, genre);
                        } else {
                            System.out.println("Genre not found for artist: " + artistName);
                        }
                        System.out.println();
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Fetch genre data for an artist from Spotify first and then MusicBrainz APIs
    private static String getGenre(Connection connection, String artistName) throws IOException, InterruptedException, SQLException {

        // Fetch artist data from Spotify API
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

            // Get the first artist from the list, fetch genre data and artist ID and save it to the database
            JSONObject artist = artists.getJSONObject(0);
            String artistId = artist.getString("id");
            saveArtistIdToDatabase(connection, artistName, artistId);

            // Check if the artist has genres and return the first genre, otherwise fetch genre data from MusicBrainz API
            if (!artist.getJSONArray("genres").isEmpty()) {
                String genre = artist.getJSONArray("genres").getString(0);
                return genre;
            } else {
                System.out.println("No genres found on Spotify for artist: " + artistName);
                return getGenreFromMusicBrainz(artistName);
            }
        } else {
            System.out.println("No artists found on Spotify for name: " + artistName);
            return getGenreFromMusicBrainz(artistName);
        }
    }

    // Fetch genre data for an artist from MusicBrainz API
    private static String getGenreFromMusicBrainz(String artistName) throws IOException, InterruptedException {

        // Fetch artist data from MusicBrainz API
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

                // Get the first artist from the list and return the genre
                JSONObject artist = artists.getJSONObject(0);
                if (artist.has("tags") && artist.getJSONArray("tags").length() > 0) {
                    String genre = artist.getJSONArray("tags").getJSONObject(0).getString("name");
                    return genre;
                } else {
                    System.out.println("No genres found on MusicBrainz for artist: " + artistName);
                }
            } else {
                System.out.println("No artists found on MusicBrainz for artist : " + artistName);
            }
        } else {
            System.out.println("No artists found on MusicBrainz for artist : " + artistName);
        }
        return null;
    }

    private static void saveGenreToDatabase(Connection connection, String artistName, String genre) throws SQLException {
        String sql = "UPDATE artists SET genre = ? WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, genre);
            statement.setString(2, artistName);
            statement.executeUpdate();
        }
        System.out.println("Genre saved for artist: " + artistName);
    }

    private static void saveArtistIdToDatabase(Connection connection, String artistName, String artistId) throws SQLException {
        String sql = "UPDATE artists SET spotify_id = ? WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, artistId);
            statement.setString(2, artistName);
            statement.executeUpdate();
        }
    }
}
