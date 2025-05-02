package services.AppAPI;

import utils.SpotifyAccessToken;
import utils.CacheManager;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class AppSpotifyAPIManager {
    private static final String SPOTIFY_API_URL = "https://api.spotify.com/v1";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    
    private String accessToken;
    private final HttpClient client;

    
    public AppSpotifyAPIManager() {
        try {
            this.accessToken = SpotifyAccessToken.getAccessToken();
            if (this.accessToken == null || this.accessToken.isEmpty()) {
                System.err.println("Warning: Received empty Spotify access token");
            } else {
                System.out.println("Successfully obtained Spotify access token");
            }
        } catch (IOException | URISyntaxException e) {
            System.err.println("Failed to get Spotify access token: " + e.getMessage());
            e.printStackTrace();
        }
        this.client = HttpClient.newHttpClient();
    }
    
    /**
     * Get track information by Spotify track ID
     * @param trackId Spotify track ID
     * @return Map containing track data
     */
    public Map<String, Object> getTrackInfo(String trackId) throws Exception {
        // First check cache
        Map<String, Object> cachedData = CacheManager.getCachedSpotifyTrackById(trackId);
        if (cachedData != null && !cachedData.isEmpty()) {
            return cachedData;
        }
        
        // If not in cache, fetch from API
        Map<String, Object> trackData = new HashMap<>();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Make sure we have a valid token
                ensureValidToken();
                
                String endpoint = SPOTIFY_API_URL + "/tracks/" + trackId;
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JSONObject jsonResponse = new JSONObject(response.body());
                    
                    // Extract track name
                    trackData.put("title", jsonResponse.getString("name"));
                    
                    // Extract album
                    if (jsonResponse.has("album") && !jsonResponse.isNull("album")) {
                        JSONObject album = jsonResponse.getJSONObject("album");
                        trackData.put("album", album.getString("name"));
                        trackData.put("release_date", album.getString("release_date"));
                        if (album.has("images") && album.getJSONArray("images").length() > 0) {
                            trackData.put("image_url", album.getJSONArray("images").getJSONObject(0).getString("url"));
                        }
                    }

                    // Extract artists
                    if (jsonResponse.has("artists") && jsonResponse.getJSONArray("artists").length() > 0) {
                        JSONArray artists = jsonResponse.getJSONArray("artists");
                        JSONObject artist = artists.getJSONObject(0);
                        trackData.put("artist", artist.getString("name"));
                    }
                    
                    if (jsonResponse.has("duration_ms")) {
                        trackData.put("duration_ms", jsonResponse.getInt("duration_ms"));
                    }
                    
                    if (jsonResponse.has("popularity")) {
                        trackData.put("popularity", jsonResponse.getInt("popularity"));
                    }

                    if (jsonResponse.has("explicit")) {
                        trackData.put("explicit", jsonResponse.getBoolean("explicit"));
                    }

                    if (jsonResponse.has("external_urls") && jsonResponse.getJSONObject("external_urls").has("spotify")) {
                        trackData.put("spotify_link", jsonResponse.getJSONObject("external_urls").getString("spotify"));
                    }

                    if (jsonResponse.has("preview_url") && !jsonResponse.isNull("preview_url")) {
                        trackData.put("preview_url", jsonResponse.getString("preview_url"));
                    }
                    
                    // Save to cache
                    CacheManager.cacheSpotifyTrack(trackId, trackData);
                } else if (response.statusCode() == 401) {
                    // Token expired, try to refresh and retry
                    refreshAccessToken();
                    continue;
                } else if (response.statusCode() == 429) {
                    System.err.println("Rate limit exceeded. Waiting before retry...");
                    Thread.sleep(2000 * attempt); // Exponential backoff
                    continue;
                } else {
                    System.err.println("Error getting track info: " + response.statusCode() + " - " + response.body());
                    if (attempt == MAX_RETRIES) {
                        throw new IOException("Failed to get track info after " + MAX_RETRIES + " attempts");
                    }
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                    continue;
                }
                break;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
            }
        }
        
        return trackData;
    }
    
    /**
     * Search for a track by title and artist
     * @param title Track title
     * @param artist Artist name
     * @return Map containing track data
     */
    public Map<String, Object> searchTrack(String title, String artist) throws Exception {
        // Check cache first
        Map<String, Object> cachedData = CacheManager.getCachedSpotifyTrackByTitleAndArtist(title, artist);
        if (cachedData != null && !cachedData.isEmpty()) {
            return cachedData;
        }
        
        Map<String, Object> trackData = new HashMap<>();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Make sure we have a valid token
                ensureValidToken();
                
                String query = URLEncoder.encode("track:" + title + " artist:" + artist, StandardCharsets.UTF_8);
                String endpoint = SPOTIFY_API_URL + "/search?q=" + query + "&type=track&limit=1";
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JSONObject jsonResponse = new JSONObject(response.body());
                    
                    if (jsonResponse.has("tracks") && 
                        jsonResponse.getJSONObject("tracks").has("items") &&
                        jsonResponse.getJSONObject("tracks").getJSONArray("items").length() > 0) {
                        
                        JSONObject track = jsonResponse.getJSONObject("tracks")
                                                       .getJSONArray("items")
                                                       .getJSONObject(0);
                        
                        // Extract track ID and get full details
                        String trackId = track.getString("id");
                        Map<String, Object> fullTrackData = getTrackInfo(trackId);
                        
                        // Cache it by title/artist as well for future lookups
                        if (!fullTrackData.isEmpty()) {
                            // Make sure it has title and artist for future cache lookups
                            if (!fullTrackData.containsKey("title")) fullTrackData.put("title", title);
                            if (!fullTrackData.containsKey("artist")) fullTrackData.put("artist", artist);
                            
                            // We don't need to call cacheSpotifyTrack here because getTrackInfo already does it
                        }
                        
                        return fullTrackData;
                    }
                } else if (response.statusCode() == 401) {
                    // Token expired, try to refresh and retry
                    refreshAccessToken();
                    continue;
                } else if (response.statusCode() == 429) {
                    System.err.println("Rate limit exceeded. Waiting before retry...");
                    Thread.sleep(2000 * attempt); // Exponential backoff
                    continue;
                } else {
                    System.err.println("Error searching track: " + response.statusCode() + " - " + response.body());
                }
                break;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
            }
        }
        
        return trackData;
    }
    
    /**
     * Get artist information by Spotify artist ID
     * @param artistId Spotify artist ID
     * @return Map containing artist data
     */
    public Map<String, Object> getArtistInfo(String artistId) throws Exception {
        // First check cache
        Map<String, Object> cachedData = CacheManager.getCachedSpotifyArtistById(artistId);
        if (cachedData != null && !cachedData.isEmpty()) {
            return cachedData;
        }
        
        Map<String, Object> artistData = new HashMap<>();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Make sure we have a valid token
                ensureValidToken();
                
                String endpoint = SPOTIFY_API_URL + "/artists/" + artistId;
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JSONObject jsonResponse = new JSONObject(response.body());
                    
                    // Extract artist name
                    artistData.put("name", jsonResponse.getString("name"));
                    artistData.put("spotify_id", jsonResponse.getString("id"));
                    artistData.put("spotify_link", jsonResponse.getJSONObject("external_urls").getString("spotify"));
                    
                    // Extract popularity if available
                    if (jsonResponse.has("popularity")) {
                        artistData.put("popularity", jsonResponse.getInt("popularity"));
                    }
                    
                    // Extract image if available
                    if (jsonResponse.has("images") && jsonResponse.getJSONArray("images").length() > 0) {
                        artistData.put("image_url", jsonResponse.getJSONArray("images").getJSONObject(0).getString("url"));
                    }
                    
                    // Extract genres
                    if (jsonResponse.has("genres") && jsonResponse.getJSONArray("genres").length() > 0) {
                        JSONArray genresArray = jsonResponse.getJSONArray("genres");
                        List<String> genres = new ArrayList<>();
                        for (int i = 0; i < genresArray.length(); i++) {
                            genres.add(genresArray.getString(i));
                        }
                        artistData.put("genres", genres);
                    }
                    
                    // Save to cache
                    CacheManager.cacheSpotifyArtist(artistId, artistData);
                } else if (response.statusCode() == 401) {
                    // Token expired, try to refresh and retry
                    refreshAccessToken();
                    continue;
                } else if (response.statusCode() == 429) {
                    System.err.println("Rate limit exceeded. Waiting before retry...");
                    Thread.sleep(2000 * attempt); // Exponential backoff
                    continue;
                } else {
                    System.err.println("Error getting artist info: " + response.statusCode() + " - " + response.body());
                }
                break;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
            }
        }
        
        return artistData;
    }
    
    /**
     * Get artist information by artist name
     * @param artistName Name of the artist
     * @return Map containing artist data
     */
    public Map<String, Object> getArtistInfoByName(String artistName) throws Exception {
        // Check cache first
        Map<String, Object> cachedData = CacheManager.getCachedSpotifyArtistByName(artistName);
        if (cachedData != null && !cachedData.isEmpty()) {
            return cachedData;
        }
        
        // Find the artist ID first
        String artistId = getArtistSpotifyId(artistName);
        if (artistId == null) {
            System.err.println("Artist not found on Spotify: " + artistName);
            return new HashMap<>(); // Return empty map if artist not found
        }
        
        // Get full artist info using the ID
        Map<String, Object> artistInfo = getArtistInfo(artistId);
        
        // Return the data
        return artistInfo;
    }
    
    /**
     * Get track information by track name and artist
     * This is an alternative to searchTrack with clearer naming
     * @param trackName Name of the track
     * @param artistName Name of the artist
     * @return Map containing track data
     */
    public Map<String, Object> getTrackInfoByName(String trackName, String artistName) throws Exception {
        return searchTrack(trackName, artistName);
    }
    
    /**
     * Updates the access token
     */
    public void refreshAccessToken() {
        try {
            System.out.println("Refreshing Spotify access token...");
            String newToken = SpotifyAccessToken.getAccessToken();
            if (newToken != null && !newToken.isEmpty()) {
                this.accessToken = newToken;
                System.out.println("Successfully refreshed Spotify access token");
            } else {
                System.err.println("Failed to refresh Spotify access token: received empty token");
            }
        } catch (IOException | URISyntaxException e) {
            System.err.println("Failed to refresh Spotify access token: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get songs by an artist
     * @param artistName Name of the artist
     * @param limit Number of songs to retrieve
     * @return List of song data maps
     */
    public List<Map<String, Object>> getArtistSongs(String artistName, int limit) throws Exception {
        List<Map<String, Object>> songs = new ArrayList<>();
        
        // First, find the artist ID
        String artistId = getArtistSpotifyId(artistName);
        if (artistId == null) {
            System.err.println("Artist not found on Spotify: " + artistName);
            return songs;
        }
        
        // Then get their top tracks
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Make sure we have a valid token
                ensureValidToken();
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SPOTIFY_API_URL + "/artists/" + artistId + "/top-tracks?market=US"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JSONObject jsonResponse = new JSONObject(response.body());
                    
                    if (jsonResponse.has("tracks")) {
                        JSONArray tracks = jsonResponse.getJSONArray("tracks");
                        int count = Math.min(tracks.length(), limit);
                        
                        for (int i = 0; i < count; i++) {
                            JSONObject track = tracks.getJSONObject(i);
                            Map<String, Object> songData = new HashMap<>();
                            
                            songData.put("title", track.getString("name"));
                            songData.put("artist", artistName);
                            songData.put("spotify_id", track.getString("id"));
                            songData.put("popularity", track.getInt("popularity"));
                            songData.put("duration_ms", track.getInt("duration_ms"));
                            songData.put("spotify_uri", track.getString("uri"));
                            songData.put("explicit", track.getBoolean("explicit"));
                            
                            JSONObject album = track.getJSONObject("album");
                            songData.put("album_name", album.getString("name"));
                            songData.put("release_date", album.getString("release_date"));
                            
                            if (album.has("images") && album.getJSONArray("images").length() > 0) {
                                songData.put("image_url", album.getJSONArray("images").getJSONObject(0).getString("url"));
                            }
                            
                            // Cache track data
                            CacheManager.cacheSpotifyTrack(track.getString("id"), songData);
                            
                            songs.add(songData);
                        }
                    }
                } else if (response.statusCode() == 401) {
                    // Token expired, try to refresh and retry
                    refreshAccessToken();
                    continue;
                } else if (response.statusCode() == 429) {
                    System.err.println("Rate limit exceeded. Waiting before retry...");
                    Thread.sleep(2000 * attempt); // Exponential backoff
                    continue;
                } else {
                    System.err.println("Error getting artist songs: " + response.statusCode() + " - " + response.body());
                }
                break;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
                // Refresh token if expired
                refreshAccessToken();
            }
        }
        
        return songs;
    }
    
    /**
     * Get artist ID from Spotify
     * @param artistName Name of the artist to search for
     * @return Spotify artist ID or null if not found
     */
    private String getArtistSpotifyId(String artistName) throws Exception {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                // Make sure we have a valid token
                ensureValidToken();
                
                String query = String.format("q=artist:%s&type=artist&limit=1", 
                    URLEncoder.encode(artistName, StandardCharsets.UTF_8));
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SPOTIFY_API_URL + "/search?" + query))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JSONObject jsonResponse = new JSONObject(response.body());
                    
                    if (jsonResponse.has("artists") && !jsonResponse.isNull("artists")) {
                        JSONObject artists = jsonResponse.getJSONObject("artists");
                        if (artists.has("items") && artists.getJSONArray("items").length() > 0) {
                            return artists.getJSONArray("items").getJSONObject(0).getString("id");
                        }
                    }
                } else if (response.statusCode() == 401) {
                    // Token expired, try to refresh and retry
                    refreshAccessToken();
                    continue;
                } else if (response.statusCode() == 429) {
                    System.err.println("Rate limit exceeded. Waiting before retry...");
                    Thread.sleep(2000 * attempt); // Exponential backoff
                    continue;
                } else {
                    System.err.println("Error searching for artist: " + response.statusCode() + " - " + response.body());
                }
                return null;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
                // Refresh token if expired
                refreshAccessToken();
            }
        }
        return null;
    }
    
    /**
     * Ensures we have a valid access token
     */
    private void ensureValidToken() {
        if (accessToken == null || accessToken.isEmpty()) {
            refreshAccessToken();
        }
    }
}