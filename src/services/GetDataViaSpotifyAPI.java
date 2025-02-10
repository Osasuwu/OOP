
//Set genre to every song in the database by using Spotify API
//can be used without user having spotify account (I hope so)

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
import java.utils.CreateSpotifyAccessToken;

package services;

public class GetDataViaSpotifyAPI {

    private static final String SPOTIFY_API_URL = "https://api.spotify.com/v1/search";
    private static final String ACCESS_TOKEN = CreateSpotifyAccessToken.getAccessToken();
    private static final String DB_URL = "jdbc:mysql://localhost:5432/MusicPlaylistGenerator";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "somepassword";

    public static void main(String[] args) {
        try (Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            String selectSql = "SELECT artist_name FROM artists WHERE genre IS NULL";
            PreparedStatement selectStatement = connection.prepareStatement(selectSql);
            ResultSet resultSet = selectStatement.executeQuery();

            while (resultSet.next()) {
                String artistName = resultSet.getString("artist_name");

                try {
                    String genre = getGenreFromSpotify(artistName);
                    if (genre != null) {
                        saveGenreToDatabase(artistName, genre);
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }

            resultSet.close();
            selectStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static String getGenreFromSpotify(String artistName) throws IOException, InterruptedException {
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

    private static void saveGenreToDatabase(String artistName, String genre) throws SQLException {
        Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        String sql = "UPDATE artists SET genre = ? WHERE artist_name = ?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, genre);
        statement.setString(2, artistName);
        statement.executeUpdate();
        statement.close();
        connection.close();
    }

    private static void saveArtistIdToDatabase(String artistName, String artistId) throws SQLException {
        Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        String sql = "UPDATE artists SET artist_id = ? WHERE artist_name = ?";
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setString(1, artistId);
        statement.setString(2, artistName);
        statement.executeUpdate();
        statement.close();
        connection.close();
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
            return genres.getString(0);
        }
        return null;
    }
}
