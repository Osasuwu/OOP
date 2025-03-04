package services.offline;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.*;
import org.json.JSONObject;
import org.json.JSONArray;

public class OfflineDataManager {
    private static final String CACHE_DIR = "cache";
    private static final String CACHE_FILE = "music_cache.json";
    private static final int CACHE_SIZE_LIMIT = 1000;

    private JSONObject cache;
    private String userId;

    public OfflineDataManager(String userId) {
        this.userId = userId;
        loadCache();
    }

    private void loadCache() {
        File cacheFile = new File(CACHE_DIR, CACHE_FILE);
        if (cacheFile.exists()) {
            try {
                String content = new String(Files.readAllBytes(cacheFile.toPath()));
                cache = new JSONObject(content);
            } catch (Exception e) {
                cache = new JSONObject();
            }
        } else {
            cache = new JSONObject();
            initializeCacheStructure();
        }
    }

    private void initializeCacheStructure() {
        cache.put("users", new JSONObject());
        cache.put("artists", new JSONObject());
        cache.put("songs", new JSONObject());
        cache.put("genres", new JSONObject());
        cache.put("playlists", new JSONObject());
        saveCache();
    }

    public void saveCache() {
        try {
            File cacheDir = new File(CACHE_DIR);
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            try (FileWriter writer = new FileWriter(new File(CACHE_DIR, CACHE_FILE))) {
                writer.write(cache.toString(2));
            }
        } catch (Exception e) {
            System.err.println("Error saving cache: " + e.getMessage());
        }
    }

    public Map<String, Object> getUserPreferences() {
        JSONObject users = cache.getJSONObject("users");
        if (users.has(userId)) {
            return jsonToMap(users.getJSONObject(userId));
        }
        return new HashMap<>();
    }

    public void updateUserPreferences(Map<String, Object> preferences) {
        JSONObject users = cache.getJSONObject("users");
        if (!users.has(userId)) {
            users.put(userId, new JSONObject());
        }
        JSONObject userData = users.getJSONObject(userId);
        mapToJson(preferences, userData);
        saveCache();
    }

    public List<Map<String, Object>> getRecommendedSongs(int limit) {
        List<Map<String, Object>> songs = new ArrayList<>();
        JSONObject songsData = cache.getJSONObject("songs");
        JSONObject userPrefs = cache.getJSONObject("users").getJSONObject(userId);

        // Get user preferences
        Set<String> favoriteGenres = new HashSet<>();
        Set<String> favoriteArtists = new HashSet<>();
        if (userPrefs.has("favorite_genres")) {
            JSONArray genres = userPrefs.getJSONArray("favorite_genres");
            for (int i = 0; i < genres.length(); i++) {
                favoriteGenres.add(genres.getString(i));
            }
        }
        if (userPrefs.has("favorite_artists")) {
            JSONArray artists = userPrefs.getJSONArray("favorite_artists");
            for (int i = 0; i < artists.length(); i++) {
                favoriteArtists.add(artists.getString(i));
            }
        }

        // Score and sort songs
        List<Map.Entry<String, Double>> scoredSongs = new ArrayList<>();
        for (String songId : songsData.keySet()) {
            JSONObject song = songsData.getJSONObject(songId);
            double score = calculateSongScore(song, favoriteGenres, favoriteArtists);
            scoredSongs.add(new AbstractMap.SimpleEntry<>(songId, score));
        }

        // Sort by score and get top songs
        scoredSongs.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        for (int i = 0; i < Math.min(limit, scoredSongs.size()); i++) {
            String songId = scoredSongs.get(i).getKey();
            songs.add(jsonToMap(songsData.getJSONObject(songId)));
        }

        return songs;
    }

    private double calculateSongScore(JSONObject song, Set<String> favoriteGenres, Set<String> favoriteArtists) {
        double score = 0.0;
        
        // Base score from popularity
        if (song.has("popularity")) {
            score += song.getDouble("popularity") * 0.1;
        }

        // Genre match score
        if (song.has("genres")) {
            JSONArray genres = song.getJSONArray("genres");
            for (int i = 0; i < genres.length(); i++) {
                if (favoriteGenres.contains(genres.getString(i))) {
                    score += 2.0;
                }
            }
        }

        // Artist match score
        if (song.has("artist_name") && favoriteArtists.contains(song.getString("artist_name"))) {
            score += 2.0;
        }

        // Audio feature scores
        if (song.has("danceability")) score += song.getDouble("danceability") * 0.5;
        if (song.has("energy")) score += song.getDouble("energy") * 0.5;
        if (song.has("valence")) score += song.getDouble("valence") * 0.5;

        return score;
    }

    public List<Map<String, Object>> getSimilarArtists(String artistName, int limit) {
        List<Map<String, Object>> artists = new ArrayList<>();
        JSONObject artistsData = cache.getJSONObject("artists");
        JSONObject genresData = cache.getJSONObject("genres");

        // Get target artist's genres
        Set<String> targetGenres = new HashSet<>();
        if (artistsData.has(artistName) && artistsData.getJSONObject(artistName).has("genres")) {
            JSONArray genres = artistsData.getJSONObject(artistName).getJSONArray("genres");
            for (int i = 0; i < genres.length(); i++) {
                targetGenres.add(genres.getString(i));
            }
        }

        // Score and sort artists
        List<Map.Entry<String, Double>> scoredArtists = new ArrayList<>();
        for (String artist : artistsData.keySet()) {
            if (!artist.equals(artistName)) {
                JSONObject artistData = artistsData.getJSONObject(artist);
                double score = calculateArtistSimilarity(artistData, targetGenres);
                scoredArtists.add(new AbstractMap.SimpleEntry<>(artist, score));
            }
        }

        // Sort by score and get top artists
        scoredArtists.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        for (int i = 0; i < Math.min(limit, scoredArtists.size()); i++) {
            String artist = scoredArtists.get(i).getKey();
            artists.add(jsonToMap(artistsData.getJSONObject(artist)));
        }

        return artists;
    }

    private double calculateArtistSimilarity(JSONObject artist, Set<String> targetGenres) {
        double score = 0.0;

        // Genre similarity score
        if (artist.has("genres")) {
            JSONArray genres = artist.getJSONArray("genres");
            for (int i = 0; i < genres.length(); i++) {
                if (targetGenres.contains(genres.getString(i))) {
                    score += 1.0;
                }
            }
        }

        // Popularity score
        if (artist.has("popularity")) {
            score += artist.getDouble("popularity") * 0.1;
        }

        return score;
    }

    private Map<String, Object> jsonToMap(JSONObject json) {
        Map<String, Object> map = new HashMap<>();
        for (String key : json.keySet()) {
            Object value = json.get(key);
            if (value instanceof JSONObject) {
                map.put(key, jsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                map.put(key, jsonArrayToList((JSONArray) value));
            } else {
                map.put(key, value);
            }
        }
        return map;
    }

    private List<Object> jsonArrayToList(JSONArray array) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONObject) {
                list.add(jsonToMap((JSONObject) value));
            } else if (value instanceof JSONArray) {
                list.add(jsonArrayToList((JSONArray) value));
            } else {
                list.add(value);
            }
        }
        return list;
    }

    private void mapToJson(Map<String, Object> map, JSONObject json) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                JSONObject nestedJson = new JSONObject();
                mapToJson((Map<String, Object>) value, nestedJson);
                json.put(key, nestedJson);
            } else if (value instanceof List) {
                json.put(key, new JSONArray((List<?>) value));
            } else {
                json.put(key, value);
            }
        }
    }
} 