    // In this code, we have a class called  GetDataViaSpotifyAPI that fetches the genre of artists from the Spotify API and saves it to a PostgreSQL database. 
    // The  main  method of this class fetches the names of artists whose genre is not yet known from the database and iterates over them. For each artist, it calls the  getGenreFromSpotify  method to fetch the genre from the Spotify API. 
    // The  getGenreFromSpotify  method sends a request to the Spotify API to search for the artist by name and fetches the genre of the first artist in the search results. It then calls the  getArtistGenre  method to fetch the genre of the artist using the artist's ID. 
    // The  getArtistGenre  method sends a request to the Spotify API to fetch the genre of the artist using the artist's ID. It then returns the genre of the artist. 
    // The  saveGenreToDatabase  method saves the genre of the artist to the database. 
    // The  saveArtistIdToDatabase  method saves the artist's ID to the database. 
    // The  SpotifyAccessToken  class is a utility class that fetches an access token from the Spotify API. 
    // The  DB_URL ,  DB_USER , and  DB_PASSWORD  constants contain the URL, username, and password of the PostgreSQL database, respectively. 
    // The  SPOTIFY_API_URL  constant contains the base URL of the Spotify API.

package services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.SpotifyAccessToken;
import java.net.URISyntaxException;


public class GetDataViaSpotifyAPI {

    private static final String SPOTIFY_API_URL = "https://api.spotify.com/v1/search";
    private static final String ACCESS_TOKEN;

    static {
        String token = "";
        try {
            token = SpotifyAccessToken.getAccessToken();
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        ACCESS_TOKEN = token;
    }
    private static final String DB_URL = "jdbc:postgresql://db.ovinvbshhlfiazazcsaw.supabase.co:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "Pk-21152SUPABASE";

    public static void main(String[] args) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String selectSql = "SELECT name FROM artists WHERE genre IS NULL";
            try (PreparedStatement selectStatement = connection.prepareStatement(selectSql);
                 ResultSet resultSet = selectStatement.executeQuery()) {

                while (resultSet.next()) {
                    String artistName = resultSet.getString("name");

                    try {
                        String genre = getGenreFromSpotify(artistName);
                        if (genre != null) {
                            saveGenreToDatabase(connection, artistName, genre);
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static String getGenreFromSpotify(String artistName) throws IOException, InterruptedException, SQLException {
        HttpClient client = HttpClient.newHttpClient();
        String query = String.format("q=artist:%s&type=artist", artistName);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SPOTIFY_API_URL + "?" + query))
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject jsonResponse = new JSONObject(response.body());
        JSONArray artists = jsonResponse.getJSONObject("artists").getJSONArray("items");

        if (artists.length() > 0) {
            JSONObject artist = artists.getJSONObject(0);
            String artistId = artist.getString("id");
            String genre = getArtistGenre(artistId);
            saveArtistIdToDatabase(artistName, artistId);
            return genre;
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
    }

    private static void saveArtistIdToDatabase(String artistName, String artistId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String sql = "UPDATE artists SET id = ? WHERE name = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, artistId);
                statement.setString(2, artistName);
                statement.executeUpdate();
            }
        }
    }

    private static String getArtistGenre(String artistId) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.spotify.com/v1/artists/" + artistId))
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject jsonResponse = new JSONObject(response.body());
        JSONArray genres = jsonResponse.getJSONArray("genres");

        if (genres.length() > 0) {
            return String.join(", ", genres.toList().stream().map(Object::toString).toArray(String[]::new));
        }
        return null;
    }
}
