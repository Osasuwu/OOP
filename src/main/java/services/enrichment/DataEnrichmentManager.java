package services.enrichment;

import models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.AppAPI.*;
import utils.CacheManager;

import java.util.concurrent.*;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the enrichment of music data from various external services
 */
public class DataEnrichmentManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataEnrichmentManager.class);
    
    private final boolean isOnline;
    private final AppSpotifyAPIManager spotifyManager;
    private final MusicBrainzAPIManager musicBrainzManager;
    private final LastFmAPIManager lastFmManager;
    private final ExecutorService executorService;
    
    // Constants for progress logging
    private static final int LOG_PROGRESS_THRESHOLD = 100;
    
    // Progress tracking variables
    private AtomicInteger artistsProcessed = new AtomicInteger(0);
    private AtomicInteger songsProcessed = new AtomicInteger(0);
    
    // Rate limiting constants
    private static final int SPOTIFY_REQUESTS_PER_SECOND = 2; // Maximum 2 requests per second to avoid 429 errors
    private static final RateLimiter spotifyRateLimiter = new RateLimiter(SPOTIFY_REQUESTS_PER_SECOND);
    
    /**
     * Creates a new DataEnrichmentManager
     * 
     * @param isOnline Whether the app is running in online mode
     * @param spotifyManager The Spotify API manager
     * @param musicBrainzManager The MusicBrainz API manager
     * @param lastFmManager The Last.fm API manager
     */
    public DataEnrichmentManager(boolean isOnline, 
                               AppSpotifyAPIManager spotifyManager, 
                               MusicBrainzAPIManager musicBrainzManager,
                               LastFmAPIManager lastFmManager) {
        this.isOnline = isOnline;
        this.spotifyManager = spotifyManager;
        this.musicBrainzManager = musicBrainzManager;
        this.lastFmManager = lastFmManager;
        this.executorService = Executors.newFixedThreadPool(3);
    }
    
    /**
     * Rate limiter class to prevent API rate limit errors
     */
    private static class RateLimiter {
        private final long intervalMs;
        private long lastRequestTime = 0;
        private final Object lock = new Object();
        
        public RateLimiter(int requestsPerSecond) {
            this.intervalMs = 1000 / requestsPerSecond;
        }
        
        /**
         * Wait until allowed to make another request based on rate limits
         */
        public void acquire() {
            synchronized (lock) {
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - lastRequestTime;
                
                if (elapsedTime < intervalMs) {
                    try {
                        Thread.sleep(intervalMs - elapsedTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                lastRequestTime = System.currentTimeMillis();
            }
        }
    }
    
    /**
     * Enriches user music data with additional information from external services
     * 
     * @param userData The user music data to enrich
     * @return The enriched user music data
     */
    public UserMusicData enrichUserData(UserMusicData userData) {
        if (!isOnline) {
            LOGGER.warn("Cannot enrich data in offline mode");
            return userData;
        }
        
        // Reset counters for this enrichment operation
        artistsProcessed.set(0);
        songsProcessed.set(0);
        
        LOGGER.info("Starting data enrichment for {} songs and {} artists", 
                  userData.getSongs().size(), userData.getArtists().size());
        
        try {
            // Enrich artists with parallel processing
            enrichArtists(userData.getArtists());
            
            // Enrich songs with parallel processing
            enrichSongs(userData.getSongs());
            
            // Count enriched items
            long artistsWithGenres = userData.getArtists().stream()
                .filter(a -> a.getGenres() != null && !a.getGenres().isEmpty())
                .count();
                
            long songsWithAdditionalInfo = userData.getSongs().stream()
                .filter(s -> s.getReleaseDate() != null || s.getPopularity() > 0 || s.getImageUrl() != null || s.getPreviewUrl() != null || s.getSpotifyLink() != null || s.getAlbumName() != null)
                .count();
                
            LOGGER.info("Enrichment completed. {} artists with genres, {} songs with additional info",
                      artistsWithGenres, songsWithAdditionalInfo);
            
            return userData;
        } catch (Exception e) {
            LOGGER.error("Error during data enrichment: {}", e.getMessage(), e);
            return userData; // Return original data on error
        }
    }
    
    private void enrichArtists(List<Artist> artists) {
        List<Future<Void>> futures = new ArrayList<>();
        int totalArtists = artists.size();
        
        for (Artist artist : artists) {
            Future<Void> future = executorService.submit(() -> {
                try {
                    // Determine enrichment method based on available data
                    if (artist.getSpotifyId() != null && !artist.getSpotifyId().isEmpty()) {
                        enrichArtistById(artist);
                    } else {
                        enrichArtistByName(artist);
                    }
                    
                    // Log progress at specific intervals
                    int current = artistsProcessed.incrementAndGet();
                    if (current % LOG_PROGRESS_THRESHOLD == 0 || current == totalArtists) {
                        LOGGER.info("Enriching artists: {}/{} processed", current, totalArtists);
                    }
                    
                } catch (Exception e) {
                    LOGGER.warn("Failed to enrich artist {}: {}", artist.getName(), e.getMessage());
                }
                return null;
            });
            
            futures.add(future);
        }
        
        // Wait for all futures to complete
        for (Future<Void> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS); // Increased timeout to 30 seconds
            } catch (TimeoutException e) {
                LOGGER.warn("Artist enrichment timed out after 30 seconds");
            } catch (Exception e) {
                LOGGER.warn("Artist enrichment failed: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Enriches artist data using Spotify ID
     * 
     * @param artist The artist to enrich
     */
    private void enrichArtistById(Artist artist) {
        try {
            // First check cache
            Map<String, Object> cachedData = CacheManager.getCachedSpotifyArtistById(artist.getSpotifyId());
            if (cachedData != null && !cachedData.isEmpty()) {
                LOGGER.info("Using cached data for artist: {} (ID: {})", artist.getName(), artist.getSpotifyId());
                updateArtistFromSpotify(artist, cachedData);
                return;
            }
            
            // Respect rate limits if we need to make an API call
            spotifyRateLimiter.acquire();
            
            // Get artist info from Spotify using ID
            Map<String, Object> spotifyInfo = spotifyManager.getArtistInfo(artist.getSpotifyId());
            if (spotifyInfo != null && !spotifyInfo.isEmpty()) {
                updateArtistFromSpotify(artist, spotifyInfo);
                LOGGER.debug("Enriched artist {} using Spotify ID", artist.getName());
                
                // The spotifyManager now handles caching internally
            }
            
            // If still missing data, try other sources using name
            if ((artist.getImageUrl() == null || artist.getGenres() == null || artist.getGenres().isEmpty()) && 
                spotifyInfo != null && !spotifyInfo.isEmpty()) {
                
                // Try Last.fm
                Map<String, Object> cachedLastFm = CacheManager.getCachedLastFmData(artist.getName());
                if (cachedLastFm != null && !cachedLastFm.isEmpty()) {
                    updateArtistFromLastFm(artist, cachedLastFm);
                } else {
                    try {
                        Map<String, Object> lastFmInfo = lastFmManager.getArtistInfo(artist.getName());
                        if (lastFmInfo != null && !lastFmInfo.isEmpty()) {
                            updateArtistFromLastFm(artist, lastFmInfo);
                            CacheManager.cacheLastFmData(artist.getName(), lastFmInfo);
                            LOGGER.debug("Supplemented artist {} using Last.fm", artist.getName());
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to get Last.fm data for {}: {}", artist.getName(), e.getMessage());
                    }
                }
                
                // MusicBrainz if still needed
                if (artist.getImageUrl() == null || artist.getGenres() == null || artist.getGenres().isEmpty()) {
                    Map<String, Object> cachedMB = CacheManager.getCachedMusicBrainzData(artist.getName());
                    if (cachedMB != null && !cachedMB.isEmpty()) {
                        updateArtistFromMusicBrainz(artist, cachedMB);
                    } else {
                        try {
                            Map<String, Object> mbInfo = musicBrainzManager.getArtistInfo(artist.getName());
                            if (mbInfo != null && !mbInfo.isEmpty()) {
                                updateArtistFromMusicBrainz(artist, mbInfo);
                                CacheManager.cacheMusicBrainzData(artist.getName(), mbInfo);
                                LOGGER.debug("Supplemented artist {} using MusicBrainz", artist.getName());
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Failed to get MusicBrainz data for {}: {}", artist.getName(), e.getMessage());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            LOGGER.warn("Failed to enrich artist {} by ID: {}", artist.getName(), e.getMessage());
        }
    }
    
    /**
     * Enriches artist data using artist name
     * 
     * @param artist The artist to enrich
     */
    private void enrichArtistByName(Artist artist) {
        try {
            // First check cache by name
            Map<String, Object> cachedData = CacheManager.getCachedSpotifyArtistByName(artist.getName());
            if (cachedData != null && !cachedData.isEmpty()) {
                LOGGER.info("Using cached data for artist by name: {}", artist.getName());
                updateArtistFromSpotify(artist, cachedData);
                return;
            }
            
            // Respect rate limits
            spotifyRateLimiter.acquire();
            
            // Try to get artist info from Spotify
            Map<String, Object> spotifyInfo = spotifyManager.getArtistInfoByName(artist.getName());
            if (spotifyInfo != null && !spotifyInfo.isEmpty()) {
                updateArtistFromSpotify(artist, spotifyInfo);
                LOGGER.debug("Enriched artist {} using Spotify name search", artist.getName());
                
                // The spotifyManager now handles caching internally
            }

            // If no genres or still missing data, try MusicBrainz
            if (artist.getImageUrl() == null || artist.getGenres() == null || artist.getGenres().isEmpty()) {
                Map<String, Object> cachedMB = CacheManager.getCachedMusicBrainzData(artist.getName());
                if (cachedMB != null && !cachedMB.isEmpty()) {
                    updateArtistFromMusicBrainz(artist, cachedMB);
                } else {
                    Map<String, Object> mbInfo = musicBrainzManager.getArtistInfo(artist.getName());
                    if (mbInfo != null && !mbInfo.isEmpty()) {
                        updateArtistFromMusicBrainz(artist, mbInfo);
                        CacheManager.cacheMusicBrainzData(artist.getName(), mbInfo);
                        LOGGER.debug("Enriched artist {} using MusicBrainz", artist.getName());
                    }
                }
            }

            // If still no genres yet, try Last.fm
            if (artist.getGenres() == null || artist.getGenres().isEmpty()) {
                Map<String, Object> cachedLastFm = CacheManager.getCachedLastFmData(artist.getName());
                if (cachedLastFm != null && !cachedLastFm.isEmpty()) {
                    updateArtistFromLastFm(artist, cachedLastFm);
                } else {
                    Map<String, Object> lastFmInfo = lastFmManager.getArtistInfo(artist.getName());
                    if (lastFmInfo != null && !lastFmInfo.isEmpty()) {
                        updateArtistFromLastFm(artist, lastFmInfo);
                        CacheManager.cacheLastFmData(artist.getName(), lastFmInfo);
                        LOGGER.debug("Enriched artist {} using Last.fm", artist.getName());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to enrich artist {} by name: {}", artist.getName(), e.getMessage());
        }
    }
    
    private void enrichSongs(List<Song> songs) {
        List<Future<Void>> futures = new ArrayList<>();
        int totalSongs = songs.size();
        
        for (Song song : songs) {
            Future<Void> future = executorService.submit(() -> {
                try {
                    // Determine enrichment method based on available data
                    if (song.getSpotifyId() != null && !song.getSpotifyId().isEmpty()) {
                        enrichSongById(song);
                    } else {
                        enrichSongByNameAndArtist(song);
                    }
                    
                    // Log progress at specific intervals
                    int current = songsProcessed.incrementAndGet();
                    if (current % LOG_PROGRESS_THRESHOLD == 0 || current == totalSongs) {
                        LOGGER.info("Enriching songs: {}/{} processed", current, totalSongs);
                    }
                    
                } catch (Exception e) {
                    LOGGER.warn("Failed to enrich song {} by {}: {}", 
                             song.getTitle(), song.getArtist().getName(), e.getMessage());
                }
                return null;
            });
            
            futures.add(future);
        }
        
        // Wait for all futures to complete
        for (Future<Void> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS); // Increased timeout to 30 seconds
            } catch (Exception e) {
                LOGGER.warn("Song enrichment task interrupted: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Enriches song data using Spotify track ID
     * 
     * @param song The song to enrich
     */
    private void enrichSongById(Song song) {
        try {
            // Check cache first
            Map<String, Object> cachedData = CacheManager.getCachedSpotifyTrackById(song.getSpotifyId());
            if (cachedData != null && !cachedData.isEmpty()) {
                LOGGER.info("Using cached data for song: {} by {} (ID: {})", 
                         song.getTitle(), song.getArtist().getName(), song.getSpotifyId());
                updateSongFromSpotify(song, cachedData);
                return;
            }
            
            // Respect rate limits if we need to make an API call
            spotifyRateLimiter.acquire();
            
            // Try to get song info from Spotify using ID
            Map<String, Object> spotifyInfo = spotifyManager.getTrackInfo(song.getSpotifyId());
            if (spotifyInfo != null && !spotifyInfo.isEmpty()) {
                updateSongFromSpotify(song, spotifyInfo);
                LOGGER.debug("Enriched song {} by {} using Spotify ID", 
                          song.getTitle(), song.getArtist().getName());
                
                // The spotifyManager now handles caching internally
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to enrich song {} by ID: {}", song.getTitle(), e.getMessage());
        }
    }
    
    /**
     * Enriches song data using song title and artist name
     * 
     * @param song The song to enrich
     */
    private void enrichSongByNameAndArtist(Song song) {
        try {
            // Check cache first
            Map<String, Object> cachedData = CacheManager.getCachedSpotifyTrackByTitleAndArtist(
                song.getTitle(), song.getArtist().getName());
            if (cachedData != null && !cachedData.isEmpty()) {
                LOGGER.info("Using cached data for song by name/artist: {} by {}", 
                         song.getTitle(), song.getArtist().getName());
                updateSongFromSpotify(song, cachedData);
                return;
            }
            
            // Respect rate limits if we need to make an API call
            spotifyRateLimiter.acquire();
            
            // URL encode the title and artist name
            String encodedTitle = encodeForUrl(song.getTitle());
            String encodedArtistName = encodeForUrl(song.getArtist().getName());
            
            // Try to get song info from Spotify using search
            Map<String, Object> spotifyInfo = spotifyManager.getTrackInfoByName(encodedTitle, encodedArtistName);
            if (spotifyInfo != null && !spotifyInfo.isEmpty()) {
                updateSongFromSpotify(song, spotifyInfo);
                LOGGER.debug("Enriched song {} by {} using Spotify search", 
                          song.getTitle(), song.getArtist().getName());
                
                // The spotifyManager now handles caching internally
            }            
        } catch (Exception e) {
            LOGGER.warn("Failed to enrich song {} by name and artist: {}", 
                     song.getTitle(), e.getMessage());
        }
    }
    
    /**
     * Enriches a specific artist by name or ID
     * 
     * @param artist The artist to enrich
     * @return The enriched artist
     */
    public Artist enrichSingleArtist(Artist artist) {
        if (!isOnline) {
            LOGGER.warn("Cannot enrich artist in offline mode");
            return artist;
        }
        
        try {
            if (artist.getSpotifyId() != null && !artist.getSpotifyId().isEmpty()) {
                enrichArtistById(artist);
            } else {
                enrichArtistByName(artist);
            }
            
            return artist;
        } catch (Exception e) {
            LOGGER.error("Error enriching artist {}: {}", artist.getName(), e.getMessage());
            return artist;
        }
    }
    
    /**
     * Enriches a specific song by ID or name/artist
     * 
     * @param song The song to enrich
     * @return The enriched song
     */
    public Song enrichSingleSong(Song song) {
        if (!isOnline) {
            LOGGER.warn("Cannot enrich song in offline mode");
            return song;
        }
        
        try {
            if (song.getSpotifyId() != null && !song.getSpotifyId().isEmpty()) {
                enrichSongById(song);
            } else {
                enrichSongByNameAndArtist(song);
            }
            
            return song;
        } catch (Exception e) {
            LOGGER.error("Error enriching song {}: {}", song.getTitle(), e.getMessage());
            return song;
        }
    }
    
    private void updateArtistFromSpotify(Artist artist, Map<String, Object> spotifyInfo) {
        if (spotifyInfo.containsKey("spotify_id")) {
            artist.setSpotifyId((String) spotifyInfo.get("spotify_id"));
        }
        
        if (spotifyInfo.containsKey("popularity")) {
            artist.setPopularity(((Number) spotifyInfo.get("popularity")).intValue());
        }
        
        if (spotifyInfo.containsKey("genres")) {
            @SuppressWarnings("unchecked")
            List<String> genres = (List<String>) spotifyInfo.get("genres");
            artist.setGenres(genres);
        }
        
        if (spotifyInfo.containsKey("image_url")) {
            artist.setImageUrl((String) spotifyInfo.get("image_url"));
        }
        
        if (spotifyInfo.containsKey("spotify_link")) {
            artist.setSpotifyLink((String) spotifyInfo.get("spotify_link"));
        }
    }
    
    private void updateArtistFromLastFm(Artist artist, Map<String, Object> lastFmInfo) {
        if (lastFmInfo.containsKey("genres") && (artist.getGenres() == null || artist.getGenres().isEmpty())) {
            @SuppressWarnings("unchecked")
            List<String> genres = (List<String>) lastFmInfo.get("genres");
            artist.setGenres(genres);
        }
        
        if (lastFmInfo.containsKey("image_url") && (artist.getImageUrl() == null || artist.getImageUrl().isEmpty())) {
            artist.setImageUrl((String) lastFmInfo.get("image_url"));
        }
    }
    
    private void updateArtistFromMusicBrainz(Artist artist, Map<String, Object> mbInfo) {
        if (mbInfo.containsKey("image_url") && (artist.getImageUrl() == null || artist.getImageUrl().isEmpty())) {
            artist.setImageUrl((String) mbInfo.get("image_url"));
        }
        
        if (mbInfo.containsKey("genres") && (artist.getGenres() == null || artist.getGenres().isEmpty())) {
            @SuppressWarnings("unchecked")
            List<String> genres = (List<String>) mbInfo.get("genres");
            artist.setGenres(genres);
        }
    }
    
    private void updateSongFromSpotify(Song song, Map<String, Object> spotifyInfo) {
        if (spotifyInfo.containsKey("spotify_id")) {
            song.setSpotifyId((String) spotifyInfo.get("spotify_id"));
        }
        
        if (spotifyInfo.containsKey("popularity")) {
            song.setPopularity(((Number) spotifyInfo.get("popularity")).intValue());
        }
        
        if (spotifyInfo.containsKey("album") && (song.getAlbumName() == null || song.getAlbumName().isEmpty())) {
            song.setAlbumName((String) spotifyInfo.get("album"));
        }
        
        if (spotifyInfo.containsKey("release_date")) {
            String dateStr = (String) spotifyInfo.get("release_date");
            try {
                java.sql.Date date = java.sql.Date.valueOf(dateStr);
                song.setReleaseDate(date);
            } catch (Exception e) {
                LOGGER.warn("Failed to parse release date: {}", dateStr);
            }
        }
        
        if (spotifyInfo.containsKey("duration_ms")) {
            song.setDurationMs(((Number) spotifyInfo.get("duration_ms")).intValue());
        }
        
        if (spotifyInfo.containsKey("image_url")) {
            song.setImageUrl((String) spotifyInfo.get("image_url"));
        }
        
        if (spotifyInfo.containsKey("preview_url")) {
            song.setPreviewUrl((String) spotifyInfo.get("preview_url"));
        }
        
        if (spotifyInfo.containsKey("spotify_link")) {
            song.setSpotifyLink((String) spotifyInfo.get("spotify_link"));
        }
    }
    
    /**
     * URL encodes a string for safe API requests
     * 
     * @param input The string to encode
     * @return The URL encoded string
     */
    private String encodeForUrl(String input) {
        if (input == null) {
            return "";
        }
        try {
            return URLEncoder.encode(input, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            LOGGER.warn("Failed to URL encode string: {}", input);
            return input; // Return unencoded as fallback
        }
    }
    
    /**
     * Shuts down the manager and releases resources
     */
    public void shutdown() {
        try {
            // Add timeouts to ensure we can still shutdown if tasks are stuck
            executorService.shutdown();
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warn("Forcing shutdown of enrichment executor after timeout");
                executorService.shutdownNow();
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.error("Enrichment executor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
