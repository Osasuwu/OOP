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
    private static final String MUSICBRAINZ_API_URL = "https://musicbrainz.org/ws/2/artist/";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000;

    public List<String> getArtistGenres(String artistName) throws IOException, InterruptedException {
        // Implementation for getting genres from MusicBrainz
        return new ArrayList<>();
    }
} 