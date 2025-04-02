package services.enrichment;

import models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.AppAPI.*;

import java.util.concurrent.*;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
                .filter(s -> s.getReleaseDate() != null || !s.getGenres().isEmpty())
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
        
        for (Artist artist : artists) {
            Future<Void> future = executorService.submit(() -> {
                try {
                    // URL encode the artist name before making API requests
                    String encodedArtistName = encodeForUrl(artist.getName());
                    
                    // Try to get artist info from Spotify
                    Map<String, Object> spotifyInfo = spotifyManager.getArtistInfo(encodedArtistName);
                    if (spotifyInfo != null && !spotifyInfo.isEmpty()) {
                        updateArtistFromSpotify(artist, spotifyInfo);
                    }

                    // If no genres, try MusicBrainz
                    if (artist.getImageUrl() == null || artist.getGenres().isEmpty()) {
                        Map<String, Object> mbInfo = musicBrainzManager.getArtistInfo(encodedArtistName);
                        if (mbInfo != null && !mbInfo.isEmpty()) {
                            updateArtistFromMusicBrainz(artist, mbInfo);
                        }
                    }

                    // If still no genres yet, try Last.fm
                    if (artist.getGenres() == null || artist.getGenres().isEmpty()) {
                        Map<String, Object> lastFmInfo = lastFmManager.getArtistInfo(encodedArtistName);
                        if (lastFmInfo != null && !lastFmInfo.isEmpty()) {
                            updateArtistFromLastFm(artist, lastFmInfo);
                        }
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
            } catch (Exception e) {
                LOGGER.warn("Artist enrichment task interrupted: {}", e.getMessage());
            }
        }
    }
    
    private void enrichSongs(List<Song> songs) {
        List<Future<Void>> futures = new ArrayList<>();
        
        for (Song song : songs) {
            Future<Void> future = executorService.submit(() -> {
                try {
                    // URL encode the title and artist name
                    String encodedTitle = encodeForUrl(song.getTitle());
                    String encodedArtistName = encodeForUrl(song.getArtist().getName());
                    
                    // Try to get song info from Spotify
                    if (song.getSpotifyId() != null || !song.getSpotifyId().isEmpty()) {
                        Map<String, Object> spotifyInfo = spotifyManager.getTrackInfo(song.getSpotifyId());
                        if (spotifyInfo != null && !spotifyInfo.isEmpty()) {
                            updateSongFromSpotify(song, spotifyInfo);
                        }
                    } else {
                        Map<String, Object> spotifyInfo = spotifyManager.searchTrack(encodedTitle, encodedArtistName);
                        if (spotifyInfo != null && !spotifyInfo.isEmpty()) {
                            updateSongFromSpotify(song, spotifyInfo);
                        }
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
        
        if (spotifyInfo.containsKey("genres")) {
            @SuppressWarnings("unchecked")
            List<String> genres = (List<String>) spotifyInfo.get("genres");
            song.setGenres(genres);
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
            executorService.shutdown();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
