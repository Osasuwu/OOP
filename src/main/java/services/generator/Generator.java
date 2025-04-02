package services.generator;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import models.Playlist;
import models.Song;
import services.sync.OfflineSyncManager;
import utils.Logger;

public class Generator {

    private final Logger logger;
    private final FilterManager filterManager;
    private final RecommendationEngine recommendationEngine;
    private final OfflineSyncManager offlineSyncManager;
    private final ExecutorService executorService;
    private final Map<String, List<Song>> cache;
    
    /**
     * Constructor initializing necessary services with caching and multi-threading.
     */
    public Generator() {
        this.logger = Logger.getInstance();
        this.filterManager = new FilterManager();
        this.recommendationEngine = new RecommendationEngine();
        // Fixed: Provide the needed String argument to OfflineSyncManager.
        this.offlineSyncManager = new OfflineSyncManager("music_folder");
        this.executorService = Executors.newCachedThreadPool();
        this.cache = Collections.synchronizedMap(new LinkedHashMap<String, List<Song>>(10, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, List<Song>> eldest) {
                return size() > 10;
            }
        });
    }
    
    /**
     * Generates a playlist based on a given criteria with caching and async processing.
     *
     * @param userId   The ID of the user requesting the playlist.
     * @param criteria The filtering and recommendation criteria.
     * @return A dynamically generated Playlist.
     */
    public Playlist generatePlaylist(String userId, String criteria) {
        logger.info("Starting playlist generation for User ID: " + userId + " with criteria: " + criteria);

        // Check cache before making API calls
        if (cache.containsKey(userId)) {
            logger.info("Fetching recommendations from cache for User ID: " + userId);
            return buildPlaylist(userId, criteria, cache.get(userId));
        }

        // Fetch recommendations asynchronously with a timeout
        Future<List<Song>> future = executorService.submit(() -> recommendationEngine.getRecommendations(userId));
        List<Song> recommendedSongs;
        try {
            recommendedSongs = future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            logger.warning("Recommendation fetch timed out. Using fallback songs.");
            recommendedSongs = getFallbackSongs();
        } catch (Exception e) {
            logger.error("Error fetching recommendations: " + e.getMessage());
            return createEmptyPlaylist();
        }
        
        // Cache the fetched recommendations
        cache.put(userId, recommendedSongs);
        return buildPlaylist(userId, criteria, recommendedSongs);
    }
    
    /**
     * Builds a playlist from filtered songs and logs execution time.
     */
    private Playlist buildPlaylist(String userId, String criteria, List<Song> recommendedSongs) {
        long startTime = System.currentTimeMillis();
        logger.info("Fetched " + recommendedSongs.size() + " recommended songs.");

        // Apply filters
        List<Song> filteredSongs = filterManager.applyFilters(recommendedSongs, criteria);
        logger.info("After filtering, " + filteredSongs.size() + " songs remain.");

        if (filteredSongs.isEmpty()) {
            logger.warning("No songs available after filtering.");
            return createEmptyPlaylist();
        }

        // Create playlist
        Playlist playlist = new Playlist("Generated Playlist");
        playlist.setSongs(filteredSongs);
        logger.info("Playlist generated successfully with " + filteredSongs.size() + " songs.");

        // Sync offline
        offlineSyncManager.syncPlaylist(playlist);
        
        long endTime = System.currentTimeMillis();
        logger.info("Playlist generation took " + (endTime - startTime) + " ms.");
        
        return playlist;
    }
    
    /**
     * Provides fallback songs in case of API failure.
     */
    private List<Song> getFallbackSongs() {
        return List.of(new Song("Default Song", "Unknown Artist"));
    }
    
    /**
     * Creates an empty playlist when no songs are available.
     */
    private Playlist createEmptyPlaylist() {
        Playlist emptyPlaylist = new Playlist("Generated Playlist");
        emptyPlaylist.setSongs(List.of());
        return emptyPlaylist;
    }
}