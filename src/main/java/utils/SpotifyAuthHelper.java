package utils;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

import org.json.JSONObject;

/**
 * Helper class for Spotify OAuth authorization
 */
public class SpotifyAuthHelper {
    private static final String CLIENT_ID = "a058f1bb72fb4456a1930dcdd3da5391";
    private static final String CLIENT_SECRET = "2155520c26ba404ab695f42c27e896e1";
    private static final String REDIRECT_URI = "http://localhost:8888/callback";
    private static final String AUTH_URL = "https://accounts.spotify.com/authorize";
    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    
    private static String accessToken;
    private static String refreshToken;
    private static long expiresAt;
    
    /**
     * Initiate the OAuth authorization flow
     * @param scopes Space-separated list of scopes (permissions)
     * @return The access token if successful, null otherwise
     */
    public static String authorize(String scopes) {
        try {
            // Check if we already have a valid token
            if (accessToken != null && System.currentTimeMillis() < expiresAt) {
                return accessToken;
            }
            
            // Try to refresh the token if we have a refresh token
            if (refreshToken != null) {
                try {
                    refreshAccessToken();
                    return accessToken;
                } catch (Exception e) {
                    System.err.println("Failed to refresh token: " + e.getMessage());
                    // Continue with new authorization
                }
            }
            
            // Create a server to listen for the callback
            CompletableFuture<String> codeReceiver = new CompletableFuture<>();
            startLocalServer(codeReceiver);
            
            // Build the authorization URL
            String authUrl = AUTH_URL +
                    "?client_id=" + CLIENT_ID +
                    "&response_type=code" +
                    "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8) +
                    "&scope=" + URLEncoder.encode(scopes, StandardCharsets.UTF_8);
            
            // Open the browser for the user to authenticate
            System.out.println("Opening browser for Spotify authorization...");
            Desktop.getDesktop().browse(new URI(authUrl));
            
            // Wait for the code to be received
            String code = codeReceiver.get();
            
            // Exchange the code for an access token
            return exchangeCodeForToken(code);
            
        } catch (Exception e) {
            System.err.println("Authorization failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    private static void startLocalServer(CompletableFuture<String> codeReceiver) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(8888)) {
                System.out.println("Waiting for callback on port 8888...");
                Socket clientSocket = serverSocket.accept();
                
                // Read the request
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String request = in.readLine();
                
                // Extract the code from the request
                String code = null;
                if (request != null && request.startsWith("GET /callback?code=")) {
                    code = request.substring(17, request.lastIndexOf(" HTTP"));
                }
                
                // Send response to browser
                String response = "HTTP/1.1 200 OK\r\n\r\n<html><body><h1>Authorization Successful</h1><p>You can close this window now.</p></body></html>";
                clientSocket.getOutputStream().write(response.getBytes());
                
                // Complete the future with the code
                if (code != null) {
                    codeReceiver.complete(code);
                } else {
                    codeReceiver.completeExceptionally(new RuntimeException("No code received"));
                }
                
            } catch (IOException e) {
                codeReceiver.completeExceptionally(e);
            }
        }).start();
    }
    
    private static String exchangeCodeForToken(String code) throws IOException, InterruptedException {
        // Prepare the request
        String auth = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        
        String requestBody = "grant_type=authorization_code" +
                "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8) +
                "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        
        // Send the request
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        
        // Process the response
        if (response.statusCode() == 200) {
            JSONObject json = new JSONObject(response.body());
            accessToken = json.getString("access_token");
            refreshToken = json.getString("refresh_token");
            int expiresIn = json.getInt("expires_in");
            expiresAt = System.currentTimeMillis() + (expiresIn * 1000);
            
            return accessToken;
        } else {
            throw new RuntimeException("Failed to exchange code for token: " + response.body());
        }
    }
    
    private static void refreshAccessToken() throws IOException, InterruptedException {
        // Prepare the request
        String auth = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
        
        String requestBody = "grant_type=refresh_token" +
                "&refresh_token=" + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        
        // Send the request
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        
        // Process the response
        if (response.statusCode() == 200) {
            JSONObject json = new JSONObject(response.body());
            accessToken = json.getString("access_token");
            int expiresIn = json.getInt("expires_in");
            expiresAt = System.currentTimeMillis() + (expiresIn * 1000);
            
            // The refresh token might be included or not
            if (json.has("refresh_token")) {
                refreshToken = json.getString("refresh_token");
            }
        } else {
            throw new RuntimeException("Failed to refresh token: " + response.body());
        }
    }
    
    /**
     * Get the current Spotify user ID
     * @return The user ID if successful, null otherwise
     */
    public static String getCurrentUserId() throws IOException, InterruptedException, URISyntaxException {
        // Ensure we have a token
        if (accessToken == null) {
            String scopes = "user-read-private playlist-modify-public playlist-modify-private";
            accessToken = authorize(scopes);
            if (accessToken == null) {
                return null;
            }
        }
        
        // Prepare the request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.spotify.com/v1/me"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
        
        // Send the request
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        
        // Process the response
        if (response.statusCode() == 200) {
            JSONObject json = new JSONObject(response.body());
            return json.getString("id");
        } else {
            System.err.println("Failed to get user ID: " + response.body());
            return null;
        }
    }
}
