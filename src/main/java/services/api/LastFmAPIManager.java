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

public class LastFmAPIManager {
    private static final String LASTFM_API_URL = "http://ws.audioscrobbler.com/2.0/";
    private static final String LASTFM_API_KEY = "c37d56d0eec9000538301510740a3ca8";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;

    public List<String> getArtistGenres(String artistName) throws IOException, InterruptedException {
        // Implementation for getting genres from Last.fm
        return new ArrayList<>();
    }
} 