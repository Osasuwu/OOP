package services.api;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class LastFmAPIManager {
    private static final String LASTFM_API_URL = "http://ws.audioscrobbler.com/2.0/";
    private static final String LASTFM_API_KEY = "c37d56d0eec9000538301510740a3ca8";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    
    /**
     * Get artist information from Last.fm
     * @param artistName Name of the artist to look up
     * @return Map containing artist data
     */
    public Map<String, Object> getArtistInfo(String artistName) throws Exception {
        Map<String, Object> artistData = new HashMap<>();
        List<String> genres = new ArrayList<>();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                String query = String.format("method=artist.getinfo&artist=%s&api_key=%s&format=json",
                    URLEncoder.encode(artistName, StandardCharsets.UTF_8),
                    LASTFM_API_KEY);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LASTFM_API_URL + "?" + query))
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject jsonResponse = new JSONObject(response.body());
                
                if (jsonResponse.has("artist") && !jsonResponse.isNull("artist")) {
                    JSONObject artist = jsonResponse.getJSONObject("artist");
                    
                    artistData.put("name", artist.optString("name", artistName));
                    
                    // Extract bio if available
                    if (artist.has("bio") && !artist.isNull("bio")) {
                        JSONObject bio = artist.getJSONObject("bio");
                        artistData.put("bio", bio.optString("summary", ""));
                    }
                    
                    // Extract tags/genres
                    if (artist.has("tags") && !artist.isNull("tags")) {
                        JSONObject tags = artist.getJSONObject("tags");
                        if (tags.has("tag")) {
                            JSONArray tagArray = tags.getJSONArray("tag");
                            for (int i = 0; i < tagArray.length(); i++) {
                                genres.add(tagArray.getJSONObject(i).getString("name"));
                            }
                        }
                    }
                    artistData.put("genres", genres);
                    
                    // Extract similar artists
                    List<Map<String, Object>> similarArtists = new ArrayList<>();
                    if (artist.has("similar") && !artist.isNull("similar")) {
                        JSONObject similar = artist.getJSONObject("similar");
                        if (similar.has("artist")) {
                            JSONArray similarArray = similar.getJSONArray("artist");
                            for (int i = 0; i < similarArray.length(); i++) {
                                JSONObject simArtist = similarArray.getJSONObject(i);
                                Map<String, Object> simData = new HashMap<>();
                                simData.put("name", simArtist.getString("name"));
                                similarArtists.add(simData);
                            }
                        }
                    }
                    artistData.put("similar", similarArtists);
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
     * Get genres associated with an artist
     * @param artistName Name of the artist
     * @return List of genres
     */
    @SuppressWarnings("unchecked")
    public List<String> getArtistGenres(String artistName) throws Exception {
        Map<String, Object> artistInfo = getArtistInfo(artistName);
        if (artistInfo.containsKey("genres")) {
            return (List<String>) artistInfo.get("genres");
        }
        return new ArrayList<>();
    }
    
    /**
     * Get artists similar to the specified artist
     * @param artistName Name of the artist
     * @param limit Maximum number of similar artists to return
     * @return List of similar artist data
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSimilarArtists(String artistName, int limit) throws Exception {
        Map<String, Object> artistInfo = getArtistInfo(artistName);
        
        if (artistInfo.containsKey("similar")) {
            List<Map<String, Object>> similarArtists = (List<Map<String, Object>>) artistInfo.get("similar");
            if (similarArtists.size() > limit) {
                return similarArtists.subList(0, limit);
            }
            return similarArtists;
        }
        
        return new ArrayList<>();
    }
}