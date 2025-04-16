package utils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the caching of API responses to reduce duplicate calls
 * and speed up data enrichment.
 */
public class CacheManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheManager.class);
    private static final String CACHE_DIRECTORY = "cache";
    private static final String CACHE_FILE = "music_cache.json";
    private static final String SPOTIFY_ARTIST_CACHE = "spotify_artists";
    private static final String SPOTIFY_SONG_CACHE = "spotify_songs";
    private static final String LASTFM_CACHE = "lastfm_data";
    private static final String MUSICBRAINZ_CACHE = "musicbrainz_data";
    
    private static JSONObject cacheData;
    
    static {
        // Initialize cache on class load
        loadCache();
    }
    
    /**
     * Loads the cache from disk
     */
    private static synchronized void loadCache() {
        try {
            File cacheFile = new File(CACHE_DIRECTORY, CACHE_FILE);
            if (!cacheFile.exists()) {
                // Create cache structure if it doesn't exist
                cacheData = new JSONObject();
                cacheData.put(SPOTIFY_ARTIST_CACHE, new JSONObject());
                cacheData.put(SPOTIFY_SONG_CACHE, new JSONObject());
                cacheData.put(LASTFM_CACHE, new JSONObject());
                cacheData.put(MUSICBRAINZ_CACHE, new JSONObject());
                
                // Ensure directory exists
                new File(CACHE_DIRECTORY).mkdirs();
                saveCache();
            } else {
                String content = new String(Files.readAllBytes(cacheFile.toPath()));
                cacheData = new JSONObject(content);
                
                // Ensure all required sections exist
                if (!cacheData.has(SPOTIFY_ARTIST_CACHE)) cacheData.put(SPOTIFY_ARTIST_CACHE, new JSONObject());
                if (!cacheData.has(SPOTIFY_SONG_CACHE)) cacheData.put(SPOTIFY_SONG_CACHE, new JSONObject());
                if (!cacheData.has(LASTFM_CACHE)) cacheData.put(LASTFM_CACHE, new JSONObject());
                if (!cacheData.has(MUSICBRAINZ_CACHE)) cacheData.put(MUSICBRAINZ_CACHE, new JSONObject());
            }
            LOGGER.info("Cache loaded with {} spotify artists, {} spotify songs, {} lastfm entries, {} musicbrainz entries",
                cacheData.getJSONObject(SPOTIFY_ARTIST_CACHE).length(),
                cacheData.getJSONObject(SPOTIFY_SONG_CACHE).length(),
                cacheData.getJSONObject(LASTFM_CACHE).length(),
                cacheData.getJSONObject(MUSICBRAINZ_CACHE).length());
        } catch (Exception e) {
            LOGGER.error("Failed to load cache: {}", e.getMessage(), e);
            // Create a new cache if loading fails
            cacheData = new JSONObject();
            cacheData.put(SPOTIFY_ARTIST_CACHE, new JSONObject());
            cacheData.put(SPOTIFY_SONG_CACHE, new JSONObject());
            cacheData.put(LASTFM_CACHE, new JSONObject());
            cacheData.put(MUSICBRAINZ_CACHE, new JSONObject());
        }
    }
    
    /**
     * Saves the cache to disk
     */
    private static synchronized void saveCache() {
        try {
            File cacheFile = new File(CACHE_DIRECTORY, CACHE_FILE);
            Files.write(cacheFile.toPath(), cacheData.toString(2).getBytes());
            LOGGER.debug("Cache saved successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to save cache: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gets cached Spotify artist data by ID or null if not in cache
     * @param artistId The Spotify artist ID
     * @return Map containing the artist data or null
     */
    public static synchronized Map<String, Object> getCachedSpotifyArtistById(String artistId) {
        try {
            JSONObject artistCache = cacheData.getJSONObject(SPOTIFY_ARTIST_CACHE);
            if (artistCache.has(artistId)) {
                LOGGER.debug("Cache hit for artist ID: {}", artistId);
                return jsonToMap(artistCache.getJSONObject(artistId));
            }
        } catch (Exception e) {
            LOGGER.warn("Error getting artist from cache: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Gets cached Spotify artist data by name or null if not in cache
     * @param artistName The artist name
     * @return Map containing the artist data or null
     */
    public static synchronized Map<String, Object> getCachedSpotifyArtistByName(String artistName) {
        try {
            JSONObject artistCache = cacheData.getJSONObject(SPOTIFY_ARTIST_CACHE);
            
            // Normalize the artist name to improve matching
            String normalizedName = normalizeString(artistName);
            
            // First try exact normalized name match
            for (String key : artistCache.keySet()) {
                JSONObject artist = artistCache.getJSONObject(key);
                if (artist.has("name")) {
                    String cachedName = normalizeString(artist.getString("name"));
                    if (cachedName.equals(normalizedName)) {
                        LOGGER.debug("Cache hit for artist name: {}", artistName);
                        return jsonToMap(artist);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error getting artist by name from cache: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Gets cached Spotify track data by ID or null if not in cache
     * @param trackId The Spotify track ID
     * @return Map containing the track data or null
     */
    public static synchronized Map<String, Object> getCachedSpotifyTrackById(String trackId) {
        try {
            JSONObject songCache = cacheData.getJSONObject(SPOTIFY_SONG_CACHE);
            if (songCache.has(trackId)) {
                LOGGER.debug("Cache hit for track ID: {}", trackId);
                return jsonToMap(songCache.getJSONObject(trackId));
            }
        } catch (Exception e) {
            LOGGER.warn("Error getting track from cache: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Gets cached Spotify track data by title and artist or null if not in cache
     * @param title The track title
     * @param artist The artist name
     * @return Map containing the track data or null
     */
    public static synchronized Map<String, Object> getCachedSpotifyTrackByTitleAndArtist(String title, String artist) {
        try {
            JSONObject songCache = cacheData.getJSONObject(SPOTIFY_SONG_CACHE);
            
            // Normalize strings to improve matching
            String normalizedTitle = normalizeString(title);
            String normalizedArtist = normalizeString(artist);
            
            for (String key : songCache.keySet()) {
                JSONObject track = songCache.getJSONObject(key);
                if (track.has("title") && track.has("artist")) {
                    String cachedTitle = normalizeString(track.getString("title"));
                    String cachedArtist = normalizeString(track.getString("artist"));
                    
                    if (cachedTitle.equals(normalizedTitle) && cachedArtist.equals(normalizedArtist)) {
                        LOGGER.debug("Cache hit for track: {} by {}", title, artist);
                        return jsonToMap(track);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error getting track by title/artist from cache: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Caches Spotify artist data
     * @param artistId The Spotify artist ID
     * @param artistData The artist data to cache
     */
    public static synchronized void cacheSpotifyArtist(String artistId, Map<String, Object> artistData) {
        try {
            if (artistId != null && artistData != null && !artistData.isEmpty()) {
                JSONObject artistCache = cacheData.getJSONObject(SPOTIFY_ARTIST_CACHE);
                artistCache.put(artistId, new JSONObject(artistData));
                saveCache();
                LOGGER.debug("Cached artist data for ID: {}", artistId);
            }
        } catch (Exception e) {
            LOGGER.warn("Error caching artist data: {}", e.getMessage());
        }
    }
    
    /**
     * Caches Spotify track data
     * @param trackId The Spotify track ID
     * @param trackData The track data to cache
     */
    public static synchronized void cacheSpotifyTrack(String trackId, Map<String, Object> trackData) {
        try {
            if (trackId != null && trackData != null && !trackData.isEmpty()) {
                JSONObject songCache = cacheData.getJSONObject(SPOTIFY_SONG_CACHE);
                songCache.put(trackId, new JSONObject(trackData));
                saveCache();
                LOGGER.debug("Cached track data for ID: {}", trackId);
            }
        } catch (Exception e) {
            LOGGER.warn("Error caching track data: {}", e.getMessage());
        }
    }
    
    /**
     * Converts a JSONObject to a Map
     * @param json The JSONObject to convert
     * @return The resulting Map
     */
    private static Map<String, Object> jsonToMap(JSONObject json) {
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
    
    /**
     * Converts a JSONArray to a List
     * @param array The JSONArray to convert
     * @return The resulting List
     */
    private static List<Object> jsonArrayToList(JSONArray array) {
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
    
    /**
     * Normalizes a string for better matching
     * @param input The input string
     * @return Normalized string
     */
    private static String normalizeString(String input) {
        if (input == null) return "";
        return input.toLowerCase().trim()
            .replaceAll("[^a-z0-9]", ""); // Remove all non-alphanumeric chars
    }
    
    /**
     * Gets cached LastFM data by artist name
     * @param artistName The artist name
     * @return Map containing the data or null
     */
    public static synchronized Map<String, Object> getCachedLastFmData(String artistName) {
        try {
            JSONObject cache = cacheData.getJSONObject(LASTFM_CACHE);
            String normalizedName = normalizeString(artistName);
            
            if (cache.has(normalizedName)) {
                LOGGER.debug("Cache hit for LastFM data: {}", artistName);
                return jsonToMap(cache.getJSONObject(normalizedName));
            }
        } catch (Exception e) {
            LOGGER.warn("Error getting LastFM data from cache: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Caches LastFM data
     * @param artistName The artist name
     * @param data The data to cache
     */
    public static synchronized void cacheLastFmData(String artistName, Map<String, Object> data) {
        try {
            if (artistName != null && data != null && !data.isEmpty()) {
                JSONObject cache = cacheData.getJSONObject(LASTFM_CACHE);
                String normalizedName = normalizeString(artistName);
                cache.put(normalizedName, new JSONObject(data));
                saveCache();
                LOGGER.debug("Cached LastFM data for: {}", artistName);
            }
        } catch (Exception e) {
            LOGGER.warn("Error caching LastFM data: {}", e.getMessage());
        }
    }
    
    /**
     * Gets cached MusicBrainz data by artist name
     * @param artistName The artist name
     * @return Map containing the data or null
     */
    public static synchronized Map<String, Object> getCachedMusicBrainzData(String artistName) {
        try {
            JSONObject cache = cacheData.getJSONObject(MUSICBRAINZ_CACHE);
            String normalizedName = normalizeString(artistName);
            
            if (cache.has(normalizedName)) {
                LOGGER.debug("Cache hit for MusicBrainz data: {}", artistName);
                return jsonToMap(cache.getJSONObject(normalizedName));
            }
        } catch (Exception e) {
            LOGGER.warn("Error getting MusicBrainz data from cache: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Caches MusicBrainz data
     * @param artistName The artist name
     * @param data The data to cache
     */
    public static synchronized void cacheMusicBrainzData(String artistName, Map<String, Object> data) {
        try {
            if (artistName != null && data != null && !data.isEmpty()) {
                JSONObject cache = cacheData.getJSONObject(MUSICBRAINZ_CACHE);
                String normalizedName = normalizeString(artistName);
                cache.put(normalizedName, new JSONObject(data));
                saveCache();
                LOGGER.debug("Cached MusicBrainz data for: {}", artistName);
            }
        } catch (Exception e) {
            LOGGER.warn("Error caching MusicBrainz data: {}", e.getMessage());
        }
    }
}