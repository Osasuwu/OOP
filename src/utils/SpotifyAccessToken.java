import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.Scanner;

package utils;


public class SpotifyAccessToken {

    private static final String CLIENT_ID = "a058f1bb72fb4456a1930dcdd3da5391";
    private static final String CLIENT_SECRET = "2155520c26ba404ab695f42c27e896e1";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";

    public static String getAccessToken() throws IOException {
        String auth = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        URL url = new URL(TOKEN_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Basic " + encodedAuth);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setDoOutput(true);

        String body = "grant_type=client_credentials";
        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes());
            os.flush();
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (Scanner scanner = new Scanner(connection.getInputStream())) {
                String responseBody = scanner.useDelimiter("\\A").next();
                // Extract the access token from the response body
                // Assuming the response is in JSON format
                String accessToken = responseBody.split("\"access_token\":\"")[1].split("\"")[0];
                return accessToken;
            }
        } else {
            throw new IOException("Error getting access token, response code: " + responseCode);
        }
    }
}