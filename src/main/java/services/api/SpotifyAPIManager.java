package services.api;

import utils.SpotifyAccessToken;

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

public class SpotifyAPIManager {
    private static final String SPOTIFY_API_URL = "https://api.spotify.com/v1";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    
    private String accessToken;
    private final HttpClient client;
    
    public SpotifyAPIManager() {
        try {
            this.accessToken = SpotifyAccessToken.getAccessToken();
        } catch (IOException | URISyntaxException e) {
            System.err.println("Failed to get Spotify access token: " + e.getMessage());
        }
        this.client = HttpClient.newHttpClient();
    }
    
    /**
     * Get track information by Spotify track ID
     * @param trackId Spotify track ID
     * @return Map containing track data
     */
    public Map<String, Object> getTrackInfo(String trackId) throws Exception {
        Map<String, Object> trackData = new HashMap<>();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
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
                    }
                    
                    // Extract duration
                    if (jsonResponse.has("duration_ms")) {
                        trackData.put("duration_ms", jsonResponse.getInt("duration_ms"));
                    }
                    
                    // Extract artists
                    if (jsonResponse.has("artists") && jsonResponse.getJSONArray("artists").length() > 0) {
                        JSONArray artists = jsonResponse.getJSONArray("artists");
                        JSONObject artist = artists.getJSONObject(0);
                        trackData.put("artist", artist.getString("name"));
                        
                        // Get artist details including genres
                        String artistId = artist.getString("id");
                        Map<String, Object> artistInfo = getArtistInfo(artistId);
                        if (artistInfo.containsKey("genres")) {
                            trackData.put("genres", artistInfo.get("genres"));
                        }
                    }
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
        Map<String, Object> trackData = new HashMap<>();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
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
                        return getTrackInfo(trackId);
                    }
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
        Map<String, Object> artistData = new HashMap<>();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
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
                    
                    // Extract genres
                    if (jsonResponse.has("genres") && jsonResponse.getJSONArray("genres").length() > 0) {
                        JSONArray genresArray = jsonResponse.getJSONArray("genres");
                        List<String> genres = new ArrayList<>();
                        for (int i = 0; i < genresArray.length(); i++) {
                            genres.add(genresArray.getString(i));
                        }
                        artistData.put("genres", genres);
                    }
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
     * Updates the access token
     */
    public void refreshAccessToken() {
        try {
            this.accessToken = SpotifyAccessToken.getAccessToken();
        } catch (IOException | URISyntaxException e) {
            System.err.println("Failed to refresh Spotify access token: " + e.getMessage());
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
        String artistId = getArtistId(artistName);
        if (artistId == null) {
            System.out.println("Artist not found on Spotify: " + artistName);
            return songs;
        }
        
        // Then get their top tracks
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SPOTIFY_API_URL + "/artists/" + artistId + "/top-tracks?market=US"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
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
                        
                        songs.add(songData);
                    }
                }
                break;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
                // Refresh token if expired
                try {
                    this.accessToken = SpotifyAccessToken.getAccessToken();
                } catch (Exception ex) {
                    System.err.println("Failed to refresh token: " + ex.getMessage());
                }
            }
        }
        
        return songs;
    }
    
    /**
     * Get artist ID from Spotify
     */
    private String getArtistId(String artistName) throws Exception {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String query = String.format("q=artist:%s&type=artist&limit=1", 
                    URLEncoder.encode(artistName, StandardCharsets.UTF_8));
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SPOTIFY_API_URL + "/search?" + query))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject jsonResponse = new JSONObject(response.body());
                
                if (jsonResponse.has("artists") && !jsonResponse.isNull("artists")) {
                    JSONObject artists = jsonResponse.getJSONObject("artists");
                    if (artists.has("items") && artists.getJSONArray("items").length() > 0) {
                        return artists.getJSONArray("items").getJSONObject(0).getString("id");
                    }
                }
                return null;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
                // Refresh token if expired
                this.accessToken = SpotifyAccessToken.getAccessToken();
            }
        }
        return null;
    }
    
    /**
     * Create a playlist on Spotify
     * @param playlistName Name of the playlist
     * @param songs List of song data
     * @return True if successful
     */
    public boolean createPlaylist(String playlistName, List<Map<String, Object>> songs) throws Exception {
        // Need a Spotify user ID to create playlists - would need to implement OAuth flow
        // For this example we'll just print the songs that would be in the playlist
        System.out.println("Would create Spotify playlist: " + playlistName);
        System.out.println("Containing these songs:");
        
        for (Map<String, Object> song : songs) {
            System.out.println("- \"" + song.get("title") + "\" by " + song.get("artist"));
        }
        
        return true;
    }
    
    /**
     * Get recommendations based on seed tracks, artists, and genres
     * @param seedTracks List of track IDs to use as seeds (max 5)
     * @param seedArtists List of artist IDs to use as seeds (max 5)
     * @param seedGenres List of genres to use as seeds (max 5)
     * @param limit Number of recommendations to get
     * @return List of recommended song data
     */
    public List<Map<String, Object>> getRecommendations(List<String> seedTracks, 
                                                       List<String> seedArtists,
                                                       List<String> seedGenres,
                                                       int limit) throws Exception {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        // Total seeds can't exceed 5
        int seedCount = Math.min(5, seedTracks.size() + seedArtists.size() + seedGenres.size());
        if (seedCount == 0) {
            return recommendations;
        }
        
        // Adjust seed lists to fit within limit of 5 total
        if (seedCount > 5) {
            int tracksToUse = Math.min(seedTracks.size(), 2);
            int artistsToUse = Math.min(seedArtists.size(), 2);
            int genresToUse = Math.min(seedGenres.size(), 5 - tracksToUse - artistsToUse);
            
            seedTracks = seedTracks.subList(0, tracksToUse);
            seedArtists = seedArtists.subList(0, artistsToUse);
            seedGenres = seedGenres.subList(0, genresToUse);
        }
        
        StringBuilder queryBuilder = new StringBuilder();
        
        if (!seedTracks.isEmpty()) {
            queryBuilder.append("seed_tracks=")
                       .append(String.join(",", seedTracks))
                       .append("&");
        }
        
        if (!seedArtists.isEmpty()) {
            queryBuilder.append("seed_artists=")
                       .append(String.join(",", seedArtists))
                       .append("&");
        }
        
        if (!seedGenres.isEmpty()) {
            queryBuilder.append("seed_genres=")
                       .append(URLEncoder.encode(String.join(",", seedGenres), StandardCharsets.UTF_8))
                       .append("&");
        }
        
        queryBuilder.append("limit=").append(limit);
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SPOTIFY_API_URL + "/recommendations?" + queryBuilder.toString()))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject jsonResponse = new JSONObject(response.body());
                
                if (jsonResponse.has("tracks")) {
                    JSONArray tracks = jsonResponse.getJSONArray("tracks");
                    
                    for (int i = 0; i < tracks.length(); i++) {
                        JSONObject track = tracks.getJSONObject(i);
                        Map<String, Object> songData = new HashMap<>();
                        
                        songData.put("title", track.getString("name"));
                        JSONArray artists = track.getJSONArray("artists");
                        if (artists.length() > 0) {
                            songData.put("artist", artists.getJSONObject(0).getString("name"));
                        } else {
                            songData.put("artist", "Unknown");
                        }
                        
                        songData.put("spotify_id", track.getString("id"));
                        songData.put("popularity", track.getInt("popularity"));
                        songData.put("duration_ms", track.getInt("duration_ms"));
                        songData.put("spotify_uri", track.getString("uri"));
                        
                        JSONObject album = track.getJSONObject("album");
                        songData.put("album_name", album.getString("name"));
                        songData.put("release_date", album.getString("release_date"));
                        
                        if (album.has("images") && album.getJSONArray("images").length() > 0) {
                            songData.put("image_url", album.getJSONArray("images").getJSONObject(0).getString("url"));
                        }
                        
                        recommendations.add(songData);
                    }
                }
                break;
            } catch (Exception e) {
                if (attempt == MAX_RETRIES) throw e;
                Thread.sleep(RETRY_DELAY_MS * attempt);
                // Refresh token if expired
                this.accessToken = SpotifyAccessToken.getAccessToken();
            }
        }
        
        return recommendations;
    }
}