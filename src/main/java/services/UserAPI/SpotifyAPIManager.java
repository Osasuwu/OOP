package services.UserAPI;

import org.json.JSONArray;
import org.json.JSONObject;
import utils.SpotifyAccessToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpotifyAPIManager {
    private static final String SPOTIFY_API_URL = "https://api.spotify.com/v1";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    private final HttpClient client;

    public SpotifyAPIManager() {
        this.client = HttpClient.newHttpClient();
    }

    public List<Map<String, Object>> getUserSavedTracks(String accessToken) {
        List<Map<String, Object>> tracks = new ArrayList<>();
        
        try {
            // Send request to Spotify API
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SPOTIFY_API_URL + "/me/tracks?limit=50"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JSONObject jsonResponse = new JSONObject(response.body());
                JSONArray items = jsonResponse.getJSONArray("items");
                
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    JSONObject track = item.getJSONObject("track");
                    
                    Map<String, Object> trackData = new HashMap<>();
                    trackData.put("track_name", track.getString("name"));
                    
                    // Get primary artist
                    if (track.has("artists") && track.getJSONArray("artists").length() > 0) {
                        trackData.put("artist_name", track.getJSONArray("artists").getJSONObject(0).getString("name"));
                    }
                    
                    trackData.put("spotify_id", track.getString("id"));
                    trackData.put("spotify_link", track.getJSONObject("external_urls").getString("spotify"));
                    
                    if (track.has("album") && track.getJSONObject("album").has("name")) {
                        trackData.put("album_name", track.getJSONObject("album").getString("name"));
                    }
                    
                    tracks.add(trackData);
                }
            } else {
                System.err.println("Error getting saved tracks: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Error in getUserSavedTracks: " + e.getMessage());
            e.printStackTrace();
        }
        
        return tracks;
    }

    public List<Map<String, Object>> getUserTopArtists(String accessToken) {
        List<Map<String, Object>> artists = new ArrayList<>();
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SPOTIFY_API_URL + "/me/top/artists?limit=50"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JSONObject jsonResponse = new JSONObject(response.body());
                JSONArray items = jsonResponse.getJSONArray("items");
                
                for (int i = 0; i < items.length(); i++) {
                    JSONObject artist = items.getJSONObject(i);
                    
                    Map<String, Object> artistData = new HashMap<>();
                    artistData.put("name", artist.getString("name"));
                    artistData.put("spotify_id", artist.getString("id"));
                    artistData.put("popularity", artist.getInt("popularity"));
                    artistData.put("spotify_link", artist.getJSONObject("external_urls").getString("spotify"));
                    
                    // Add genres if available
                    if (artist.has("genres") && artist.getJSONArray("genres").length() > 0) {
                        List<String> genres = new ArrayList<>();
                        JSONArray genresArray = artist.getJSONArray("genres");
                        
                        for (int j = 0; j < genresArray.length(); j++) {
                            genres.add(genresArray.getString(j));
                        }
                        
                        artistData.put("genres", genres);
                    }
                    
                    artists.add(artistData);
                }
            } else {
                System.err.println("Error getting top artists: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Error in getUserTopArtists: " + e.getMessage());
            e.printStackTrace();
        }
        
        return artists;
    }

    public List<Map<String, Object>> getUserRecentlyPlayed(String accessToken) {
        List<Map<String, Object>> history = new ArrayList<>();
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SPOTIFY_API_URL + "/me/player/recently-played?limit=50"))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JSONObject jsonResponse = new JSONObject(response.body());
                JSONArray items = jsonResponse.getJSONArray("items");
                
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    JSONObject track = item.getJSONObject("track");
                    
                    Map<String, Object> trackData = new HashMap<>();
                    trackData.put("track_name", track.getString("name"));
                    
                    // Get primary artist
                    if (track.has("artists") && track.getJSONArray("artists").length() > 0) {
                        trackData.put("artist_name", track.getJSONArray("artists").getJSONObject(0).getString("name"));
                    }
                    
                    trackData.put("spotify_id", track.getString("id"));
                    trackData.put("played_at", item.getString("played_at"));
                    
                    history.add(trackData);
                }
            } else {
                System.err.println("Error getting recently played tracks: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Error in getUserRecentlyPlayed: " + e.getMessage());
            e.printStackTrace();
        }
        
        return history;
    }
}
