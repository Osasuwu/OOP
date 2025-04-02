package services.data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import services.network.APIClient;
import services.storage.LocalStorageManager;
import utils.Logger;
import utils.NetworkUtils;

/**
 * Fetches music-related data from APIs, local storage, or databases.
 * Implements caching for efficiency and reduces redundant network calls.
 */
public class DataFetcher {

    private final APIClient apiClient;
    private final LocalStorageManager localStorageManager;
    private final Logger logger = Logger.getInstance(); // Initialize logger here

    // [Enhancement] In-memory cache with TTL support.
    private final Map<String, CacheEntry> memoryCache = new HashMap<>();
    // Set the cache TTL to 24 hours.
    private static final long CACHE_TTL_MILLIS = TimeUnit.HOURS.toMillis(24);

    // Helper inner class for caching.
    private static class CacheEntry {
        List<String> data;
        long timestamp;
        CacheEntry(List<String> data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }

    /**
     * Constructor initializes dependencies.
     */
    public DataFetcher() {
        this.apiClient = new APIClient();
        this.localStorageManager = new LocalStorageManager("music_cache.json");
    }

    /**
     * Fetches music data, prioritizing in-memory and local cache before making API calls.
     * Enhancements include TTL-based cache invalidation, asynchronous API calls with retry logic,
     * offline mode support, and fuzzy query matching.
     *
     * @param query The search keyword (e.g., song name, artist, genre).
     * @return List of music data matching the query.
     */
    public List<String> fetchMusicData(String query) {
        logger.info("Fetching music data for query: " + query);
        
        // [Enhancement] Normalize the query for fuzzy matching.
        String normalizedQuery = query.toLowerCase().replaceAll("[^a-z0-9]", "");
        
        // [Enhancement] If offline, use only local cache.
        if (NetworkUtils.isOffline()) {
            logger.warning("Offline mode enabled. Using local cache only for query: " + normalizedQuery);
            Optional<Map<String, String>> localCacheOpt = localStorageManager.getCachedMusic(normalizedQuery);
            return localCacheOpt.map(map -> new ArrayList<>(map.values())).orElse(new ArrayList<>());
        }
        
        // [Enhancement] Check in-memory cache.
        if (memoryCache.containsKey(normalizedQuery)) {
            CacheEntry entry = memoryCache.get(normalizedQuery);
            long age = System.currentTimeMillis() - entry.timestamp;
            if (age <= CACHE_TTL_MILLIS) {
                logger.info("Returning in-memory cached data for query: " + normalizedQuery);
                return entry.data;
            } else {
                logger.info("In-memory cache expired for query: " + normalizedQuery);
                memoryCache.remove(normalizedQuery);
                // Optionally, also clear local storage cache for this query.
                localStorageManager.clearCacheEntry(normalizedQuery);
            }
        }
        
        // Check local cache from disk.
        Optional<Map<String, String>> cachedDataMap = localStorageManager.getCachedMusic(normalizedQuery);
        Optional<List<String>> cachedData = cachedDataMap.map(map -> new ArrayList<>(map.values()));
        if (cachedData.isPresent() && !cachedData.get().isEmpty()) {
            logger.info("Local cache hit for query: " + normalizedQuery);
            List<String> data = cachedData.get();
            // Update in-memory cache.
            memoryCache.put(normalizedQuery, new CacheEntry(data, System.currentTimeMillis()));
            return data;
        }
        
        // [Enhancement] API call: Use asynchronous call with retry logic.
        CompletableFuture<List<String>> future = CompletableFuture.supplyAsync(() -> {
            int retryCount = 0;
            while(retryCount < 3) {
                try {
                    return apiClient.fetchMusicData(normalizedQuery);
                } catch (IOException e) {
                    retryCount++;
                    logger.error("API call failed (attempt " + retryCount + "): " + e.getMessage());
                    try {
                        Thread.sleep(1000); // Wait 1 second before retrying.
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            return new ArrayList<String>();
        });
        
        List<String> musicData;
        try {
            musicData = future.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error during asynchronous API call: " + e.getMessage());
            return List.of();
        }
        
        // If data is retrieved, cache it in local storage and in-memory.
        if (!musicData.isEmpty()) {
            Map<String, String> musicDataMap = new HashMap<>();
            for (String data : musicData) {
                // Use the fetched song as key and the normalized query as value.
                musicDataMap.put(data, normalizedQuery);
            }
            localStorageManager.cacheMusic(normalizedQuery, musicDataMap);
            memoryCache.put(normalizedQuery, new CacheEntry(musicData, System.currentTimeMillis()));
            logger.info("Music data retrieved from API and cached for query: " + normalizedQuery);
        } else {
            logger.warning("No music data found for query: " + normalizedQuery);
        }
        return musicData;
    }

    /**
     * Fetches album details from local cache or API.
     *
     * @param albumId The album's unique identifier.
     * @return Album details as a string, or an empty Optional if not found.
     */
    public Optional<String> fetchAlbumDetails(String albumId) {
        logger.info("Fetching album details for ID: " + albumId);
        Optional<String> cachedAlbum = localStorageManager.getCachedAlbum(albumId);
        if (cachedAlbum.isPresent()) {
            logger.info("Album details found in local cache for ID: " + albumId);
            return cachedAlbum;
        }
        try {
            Optional<String> albumDetails = apiClient.fetchAlbumDetails(albumId);
            albumDetails.ifPresent(details -> localStorageManager.cacheAlbum(albumId, details));
            return albumDetails;
        } catch (IOException e) {
            logger.error("Error fetching album details: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches artist information, prioritizing cache before making API requests.
     *
     * @param artistName The artist's name.
     * @return Artist details as a string, or an empty Optional if not found.
     */
    public Optional<String> fetchArtistDetails(String artistName) {
        logger.info("Fetching artist details for: " + artistName);
        Optional<String> cachedArtist = localStorageManager.getCachedArtist(artistName);
        if (cachedArtist.isPresent()) {
            logger.info("Artist details found in local cache for: " + artistName);
            return cachedArtist;
        }
        try {
            Optional<String> artistDetails = apiClient.fetchArtistDetails(artistName);
            artistDetails.ifPresent(details -> localStorageManager.cacheArtist(artistName, details));
            return artistDetails;
        } catch (IOException e) {
            logger.error("Error fetching artist details: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Fetches songs as a list of maps with song details.
     * This method calls fetchMusicData("") (using an empty query) and converts
     * each returned String (assumed to be a song title) into a Map with default values.
     *
     * @return A list of songs represented as maps.
     */
    public List<Map<String, Object>> fetchSongs() {
        List<String> songTitles = fetchMusicData("");
        List<Map<String, Object>> songs = new ArrayList<>();
        for (String title : songTitles) {
            Map<String, Object> song = new HashMap<>();
            song.put("title", title);
            // [Enhancement] Enhanced metadata: attempting to add more detailed info:
            song.put("genre", "unknown");  // Could use: map.getOrDefault("genre", "unknown")
            song.put("artist", "unknown");
            song.put("popularity", generatePopularityScore(title)); // Dummy function for demonstration.
            song.put("duration", 0); // Replace with actual duration if available.
            song.put("year", extractYearFromTitle(title)); // Dummy function for demonstration.
            songs.add(song);
        }
        return songs;
    }

    /**
     * Clears all cached music data.
     */
    public void clearCache() {
        localStorageManager.clearCache();
        logger.info("Music data cache cleared.");
    }
    
    // --- Enhancement Helper Methods ---
    
    /**
     * Dummy function to generate a popularity score based on the song title.
     * In practice, this could use more complex logic or external data.
     */
    private int generatePopularityScore(String title) {
        return title.length() % 100; // Example calculation.
    }
    
    /**
     * Dummy function to extract a year from the song title.
     * In a real application, this would extract from metadata.
     */
    private int extractYearFromTitle(String title) {
        return 2000 + (title.length() % 21); // Example: returns a year between 2000 and 2020.
    }
}