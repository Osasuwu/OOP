package services.enrichment;

import models.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.AppAPI.*;
import services.database.MusicDatabaseManager;

import java.util.concurrent.*;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Manages the enrichment of music data from various external services
 */
public class DataEnrichmentManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataEnrichmentManager.class);
    
    private final boolean isOnline;
    private final AppSpotifyAPIManager spotifyManager;
    private final MusicBrainzAPIManager musicBrainzManager;
    private final LastFmAPIManager lastFmManager;
    private final MusicDatabaseManager dbManager;

    private final ExecutorService executorService;
    
    // Constants for progress logging
    private static final int LOG_PROGRESS_THRESHOLD = 100;
    
    // Progress tracking variables
    private AtomicInteger artistsProcessed = new AtomicInteger(0);
    private AtomicInteger songsProcessed = new AtomicInteger(0);

    
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
                               LastFmAPIManager lastFmManager,
                               MusicDatabaseManager dbManager) {
        this.isOnline = isOnline;
        this.spotifyManager = spotifyManager;
        this.musicBrainzManager = musicBrainzManager;
        this.lastFmManager = lastFmManager;
        this.dbManager = dbManager;
        this.executorService = Executors.newFixedThreadPool(3);
    }
    
    /**
     * Enriches user music data with additional information from external services
     * Only enriches items that don't already have complete information from the database.
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
        
        // First, fetch complete information from database for all items
        dbManager.fetchCompleteDataFromDatabase(userData);
        
        // After database lookup, filter only items that still need enrichment
        List<Artist> artistsToEnrich = userData.getArtists().stream()
            .filter(this::artistNeedsEnrichment)
            .collect(Collectors.toList());
            
        List<Song> songsToEnrich = userData.getSongs().stream()
            .filter(this::songNeedsEnrichment)
            .collect(Collectors.toList());
        
        LOGGER.info("Starting data enrichment for {} songs and {} artists (filtered from {} songs and {} artists)", 
                  songsToEnrich.size(), artistsToEnrich.size(), 
                  userData.getSongs().size(), userData.getArtists().size());
        
        try {
            // Enrich only filtered artists with parallel processing
            if (!artistsToEnrich.isEmpty()) {
                enrichArtists(artistsToEnrich);
            }
            
            // Enrich only filtered songs with parallel processing
            if (!songsToEnrich.isEmpty()) {
                enrichSongs(songsToEnrich);
            }
            
            // Count enriched items
            long artistsWithGenres = userData.getArtists().stream()
                .filter(a -> a.getGenres() != null && !a.getGenres().isEmpty())
                .count();
                
            long songsWithAdditionalInfo = userData.getSongs().stream()
                .filter(s -> s.getReleaseDate() != null || s.getPopularity() > 0 || s.getImageUrl() != null || s.getPreviewUrl() != null || s.getSpotifyLink() != null || s.getAlbumName() != null)
                .count();
                
            LOGGER.info("Enrichment completed. {} artists with genres, {} songs with additional info",
                      artistsWithGenres, songsWithAdditionalInfo);
            
            // Save only enriched data to the database
            try {
                // Use the new selective save method
                dbManager.saveEnrichedUserData(userData);
            } catch (SQLException e) {
                LOGGER.error("Error saving enriched data to database: {}", e.getMessage(), e);
            }
            
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
                future.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                LOGGER.warn("Artist enrichment timed out after 5 seconds");
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
            // Get artist info from Spotify using ID
            Map<String, Object> spotifyInfo = spotifyManager.getArtistInfo(artist.getSpotifyId());
            if (spotifyInfo != null && !spotifyInfo.isEmpty()) {
                
                updateArtistFromSpotify(artist, spotifyInfo);
                LOGGER.debug("Enriched artist {} using Spotify ID", artist.getName());
            }
            
            // FIXED: Only try enrichment by name if we haven't already tried using ID
            // This prevents infinite recursion when data cannot be found
            // If still missing data, try other sources using name
            if ((artist.getImageUrl() == null || artist.getGenres().isEmpty()) && 
                spotifyInfo != null && !spotifyInfo.isEmpty()) {
                // Try Last.fm and MusicBrainz directly without calling enrichArtistByName
                // Last.fm
                try {
                    Map<String, Object> lastFmInfo = lastFmManager.getArtistInfo(artist.getName());
                    if (lastFmInfo != null && !lastFmInfo.isEmpty()) {
                        updateArtistFromLastFm(artist, lastFmInfo);
                        LOGGER.debug("Supplemented artist {} using Last.fm", artist.getName());
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to get Last.fm data for {}: {}", artist.getName(), e.getMessage());
                }
                
                // MusicBrainz if still needed
                if (artist.getImageUrl() == null || artist.getGenres().isEmpty()) {
                    try {
                        Map<String, Object> mbInfo = musicBrainzManager.getArtistInfo(artist.getName());
                        if (mbInfo != null && !mbInfo.isEmpty()) {
                            updateArtistFromMusicBrainz(artist, mbInfo);
                            LOGGER.debug("Supplemented artist {} using MusicBrainz", artist.getName());
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to get MusicBrainz data for {}: {}", artist.getName(), e.getMessage());
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
            // Try to get artist info from Spotify
            Map<String, Object> spotifyInfo = spotifyManager.getArtistInfoByName(artist.getName());
            if (spotifyInfo != null && !spotifyInfo.isEmpty()) {
                updateArtistFromSpotify(artist, spotifyInfo);
                LOGGER.debug("Enriched artist {} using Spotify name search", artist.getName());
            }

            // If no genres, try MusicBrainz
            if (artist.getImageUrl() == null || artist.getGenres().isEmpty()) {
                Map<String, Object> mbInfo = musicBrainzManager.getArtistInfo(artist.getName());
                if (mbInfo != null && !mbInfo.isEmpty()) {
                    updateArtistFromMusicBrainz(artist, mbInfo);
                    LOGGER.debug("Enriched artist {} using MusicBrainz", artist.getName());
                }
            }

            // If still no genres yet, try Last.fm
            if (artist.getGenres() == null || artist.getGenres().isEmpty()) {
                Map<String, Object> lastFmInfo = lastFmManager.getArtistInfo(artist.getName());
                if (lastFmInfo != null && !lastFmInfo.isEmpty()) {
                    updateArtistFromLastFm(artist, lastFmInfo);
                    LOGGER.debug("Enriched artist {} using Last.fm", artist.getName());
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
                future.get(5, TimeUnit.SECONDS);
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
            // Try to get song info from Spotify using ID
            Map<String, Object> spotifyInfo = spotifyManager.getTrackInfo(song.getSpotifyId());
            if (spotifyInfo != null && !spotifyInfo.isEmpty()) {
                updateSongFromSpotify(song, spotifyInfo);
                LOGGER.debug("Enriched song {} by {} using Spotify ID", 
                          song.getTitle(), song.getArtist().getName());
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
            // URL encode the title and artist name
            String encodedTitle = encodeForUrl(song.getTitle());
            String encodedArtistName = encodeForUrl(song.getArtist().getName());
            
            // Try to get song info from Spotify using search
            Map<String, Object> spotifyInfo = spotifyManager.getTrackInfoByName(encodedTitle, encodedArtistName);
            if (spotifyInfo != null && !spotifyInfo.isEmpty()) {
                
                updateSongFromSpotify(song, spotifyInfo);
                LOGGER.debug("Enriched song {} by {} using Spotify search", 
                          song.getTitle(), song.getArtist().getName());
            }            
        } catch (Exception e) {
            LOGGER.warn("Failed to enrich song {} by name and artist: {}", 
                     song.getTitle(), e.getMessage());
        }
    }
    
    /**
     * Enriches a specific artist by name or ID
     * Only performs enrichment if the artist data is incomplete.
     * 
     * @param artist The artist to enrich
     * @return The enriched artist
     */
    public Artist enrichSingleArtist(Artist artist) {
        if (!isOnline) {
            LOGGER.warn("Cannot enrich artist in offline mode");
            return artist;
        }
        
        // Skip enrichment if artist already has complete information
        if (!artistNeedsEnrichment(artist)) {
            LOGGER.debug("Skipping enrichment for artist {} as it already has complete information", artist.getName());
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
     * Only performs enrichment if the song data is incomplete.
     * 
     * @param song The song to enrich
     * @return The enriched song
     */
    public Song enrichSingleSong(Song song) {
        if (!isOnline) {
            LOGGER.warn("Cannot enrich song in offline mode");
            return song;
        }
        
        // Skip enrichment if song already has complete information
        if (!songNeedsEnrichment(song)) {
            LOGGER.debug("Skipping enrichment for song {} by {} as it already has complete information", 
                       song.getTitle(), song.getArtist().getName());
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
                // Handle different date formats from Spotify (full date, year-month, or year only)
                if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    // Full date format: YYYY-MM-DD
                    song.setReleaseDate(java.sql.Date.valueOf(dateStr));
                } else if (dateStr.matches("\\d{4}-\\d{2}")) {
                    // Year-month format: YYYY-MM
                    song.setReleaseDate(java.sql.Date.valueOf(dateStr + "-01"));
                } else if (dateStr.matches("\\d{4}")) {
                    // Year only format: YYYY
                    song.setReleaseDate(java.sql.Date.valueOf(dateStr + "-01-01"));
                } else {
                    LOGGER.warn("Unknown date format: {}", dateStr);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse release date: {} - {}", dateStr, e.getMessage());
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
    
    /**
     * Checks if an artist needs enrichment
     * 
     * @param artist The artist to check
     * @return True if the artist needs enrichment, false otherwise
     */
    private boolean artistNeedsEnrichment(Artist artist) {
        return artist.getGenres() == null || artist.getGenres().isEmpty() || artist.getImageUrl() == null || artist.getImageUrl().isEmpty();
    }
    
    /**
     * Checks if a song needs enrichment
     * 
     * @param song The song to check
     * @return True if the song needs enrichment, false otherwise
     */
    private boolean songNeedsEnrichment(Song song) {
        return song.getReleaseDate() == null || song.getPopularity() == 0 || song.getImageUrl() == null || song.getImageUrl().isEmpty() || song.getSpotifyLink() == null || song.getSpotifyLink().isEmpty() || song.getAlbumName() == null || song.getAlbumName().isEmpty();
    }
}
