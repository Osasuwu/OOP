package services.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MusicBrainzAPIManager {
    private static final String MUSICBRAINZ_API_URL = "https://musicbrainz.org/ws/2";
    private static final String ARTIST_ENDPOINT = "/artist";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;
    
    /**
     * Get artist data from MusicBrainz
     * @param artistName Name of the artist to look up
     * @return Map containing artist data
     */
    public Map<String, Object> getArtistInfo(String artistName) throws Exception {
        Map<String, Object> artistData = new HashMap<>();
        List<String> genres = new ArrayList<>();
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                String query = String.format("query=artist:%s&fmt=json", 
                    URLEncoder.encode(artistName, StandardCharsets.UTF_8));
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MUSICBRAINZ_API_URL + ARTIST_ENDPOINT + "?" + query))
                    .header("User-Agent", "PlaylistGeneratorApp/1.0 (petrkudr2@gmail.com"))
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject jsonResponse = new JSONObject(response.body());
                
                if (jsonResponse.has("artists") && jsonResponse.getJSONArray("artists").length() > 0) {
                    JSONObject artist = jsonResponse.getJSONArray("artists").getJSONObject(0);
                    
                    artistData.put("id", artist.optString("id", ""));
                    artistData.put("name", artist.optString("name", artistName));
                    
                    // Extract tags/genres
                    if (artist.has("tags")) {
                        JSONArray tags = artist.getJSONArray("tags");
                        for (int i = 0; i < tags.length(); i++) {
                            JSONObject tag = tags.getJSONObject(i);
                            genres.add(tag.getString("name"));
                        }
                    }
                    artistData.put("genres", genres);
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
    public List<String> getArtistGenres(String artistName) throws Exception {
        Map<String, Object> artistInfo = getArtistInfo(artistName);
        if (artistInfo.containsKey("genres")) {
            return (List<String>) artistInfo.get("genres");
        }
        return new ArrayList<>();
    }
}