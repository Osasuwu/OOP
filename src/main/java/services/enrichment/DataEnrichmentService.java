package services.enrichment;

import models.*;
import services.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for enriching music data with information from external APIs
 * before saving to the database
 */
public class DataEnrichmentService {
    private final LastFmAPIManager lastFmAPI;
    private final SpotifyAPIManager spotifyAPI;
    private final MusicBrainzAPIManager musicBrainzAPI;
    private final ExecutorService executorService;
    private static final Logger logger = LoggerFactory.getLogger(DataEnrichmentService.class);
    
    public DataEnrichmentService() {
        this.lastFmAPI = new LastFmAPIManager();
        this.spotifyAPI = new SpotifyAPIManager();
        this.musicBrainzAPI = new MusicBrainzAPIManager();

        // Use a thread pool to make API calls in parallel
        this.executorService = Executors.newFixedThreadPool(10);
    }
    
    /**
     * Enriches user music data with additional information from APIs
     * @param userData The user music data to enrich
     * @return The enriched user music data
     */
    public UserMusicData enrichUserData(UserMusicData userData) {
        if (userData == null || userData.isEmpty()) {
            logger.info("No data to enrich, skipping enrichment process");
            return userData;
        }
        
        logger.info("Starting enrichment process for {} artists and {} songs", 
                    userData.getArtists().size(), userData.getSongs().size());
        
        try {
            // Enrich artists first
            long startTimeArtists = System.currentTimeMillis();
            logger.info("Beginning artist enrichment...");
            enrichArtists(userData);
            long artistEnrichmentTime = System.currentTimeMillis() - startTimeArtists;
            logger.info("Artist enrichment completed in {} ms", artistEnrichmentTime);
            
            // Then enrich songs (which might use enriched artist data)
            long startTimeSongs = System.currentTimeMillis();
            logger.info("Beginning song enrichment...");
            enrichSongs(userData);
            long songEnrichmentTime = System.currentTimeMillis() - startTimeSongs;
            logger.info("Song enrichment completed in {} ms", songEnrichmentTime);
            
            // Log statistics about enriched data
            int artistsWithGenres = (int)userData.getArtists().stream()
                .filter(a -> a.getGenres() != null && !a.getGenres().isEmpty())
                .count();
            
            int songsWithGenres = (int)userData.getSongs().stream()
                .filter(s -> s.getGenres() != null && !s.getGenres().isEmpty())
                .count();
                
            int songsWithAlbums = (int)userData.getSongs().stream()
                .filter(s -> s.getAlbum() != null && !s.getAlbum().isEmpty())
                .count();
                
            logger.info("Enrichment results: {}/{} artists with genres, {}/{} songs with genres, {}/{} songs with albums",
                artistsWithGenres, userData.getArtists().size(),
                songsWithGenres, userData.getSongs().size(),
                songsWithAlbums, userData.getSongs().size());
            
            System.out.println("Data enrichment completed successfully!");
        } catch (Exception e) {
            logger.error("Error during data enrichment: {}", e.getMessage(), e);
            System.err.println("Error during data enrichment: " + e.getMessage());
        }
        
        return userData;
    }
    
    /**
     * Enriches artist information using LastFm API
     * @param userData The user data containing artists to enrich
     */
    private void enrichArtists(UserMusicData userData) {
        List<Artist> artists = userData.getArtists();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        int alreadyEnrichedCount = 0;
        int toEnrichCount = 0;
        
        for (Artist artist : artists) {
            // Skip artists that already have complete data
            if (artist.getGenres() != null && !artist.getGenres().isEmpty() &&
                artist.getPopularity() > 0 && artist.getImageUrl() != null && !artist.getImageUrl().isEmpty()) {
                alreadyEnrichedCount++;
                continue;
            }
            
            toEnrichCount++;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    logger.debug("Enriching artist: {}", artist.getName());
                    long startTime = System.currentTimeMillis();
                    
                    Map<String, Object> artistInfo = lastFmAPI.getArtistInfo(artist.getName());
                    
                    // Update artist bio if missing
                    if ((artist.getPopularity() == 0) && 
                        artistInfo.containsKey("popularity")) {
                        artist.setPopularity((int) artistInfo.get("popularity"));
                        logger.debug("Updated popularity for artist: {}", artist.getName());
                    }
                    
                    // Update genres if missing
                    if ((artist.getGenres() == null || artist.getGenres().isEmpty())) {
                        List<String> genres = null;
                        
                        // Use Spotify API to get genres if available
                        if (artistInfo.containsKey("genres")) {
                            @SuppressWarnings("unchecked")
                            List<String> spotifyGenres = (List<String>) artistInfo.get("genres");
                            genres = spotifyGenres;
                            logger.debug("Found {} genres from Spotify for artist: {}", 
                                        genres.size(), artist.getName());
                        } 
                        
                        // Use MusicBrainz API as fallback if genres not available
                        if (genres == null || genres.isEmpty()) {
                            genres = musicBrainzAPI.getArtistGenres(artist.getName());
                            if(genres != null && !genres.isEmpty()) {
                                logger.debug("Found {} genres from MusicBrainz for artist: {}", 
                                            genres.size(), artist.getName());
                            } 
                            
                            // Use LastFM as last resort
                            if (genres == null || genres.isEmpty()) {
                                genres = lastFmAPI.getArtistGenres(artist.getName());
                                if(genres != null && !genres.isEmpty()) {
                                    logger.debug("Found {} genres from LastFM for artist: {}", 
                                                genres.size(), artist.getName());
                                }
                            }
                        }
                        
                        if (genres != null && !genres.isEmpty()) {
                            artist.setGenres(genres);
                            logger.debug("Set {} genres for artist: {}", genres.size(), artist.getName());
                        } else {
                            logger.debug("No genres found for artist: {}", artist.getName());
                        }
                    }

                    // Update image URL if missing
                    if ((artist.getImageUrl() == null || artist.getImageUrl().isEmpty()) && 
                        artistInfo.containsKey("imageUrl")) {
                        artist.setImageUrl((String) artistInfo.get("imageUrl"));
                        logger.debug("Updated image URL for artist: {}", artist.getName());
                    }
                    
                    long duration = System.currentTimeMillis() - startTime;
                    logger.debug("Completed enriching artist {} in {} ms", artist.getName(), duration);
                    
                    // Throttle API requests to avoid rate limits
                    Thread.sleep(100);
                } catch (Exception e) {
                    logger.error("Error enriching artist {}: {}", artist.getName(), e.getMessage());
                }
            }, executorService);
            
            futures.add(future);
        }
        
        logger.info("Waiting for {} artist enrichment tasks to complete (skipped {} already enriched)", 
                    toEnrichCount, alreadyEnrichedCount);
        
        // Wait for all artist enrichment tasks to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        logger.info("All artist enrichment tasks completed");
    }
    
    /**
     * Enriches song information using Spotify API
     * @param userData The user data containing songs to enrich
     */
    private void enrichSongs(UserMusicData userData) {
        List<Song> songs = userData.getSongs();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        int alreadyEnrichedCount = 0;
        int toEnrichCount = 0;
        
        for (Song song : songs) {
            // Skip songs that already have complete data
            if (song.getAlbum() != null && !song.getAlbum().isEmpty() && 
                song.getGenres() != null && !song.getGenres().isEmpty()) {
                alreadyEnrichedCount++;
                continue;
            }
            
            toEnrichCount++;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    logger.debug("Enriching song: {} by {}", song.getTitle(), song.getArtistName());
                    long startTime = System.currentTimeMillis();
                    
                    enrichSong(song, userData);
                    
                    long duration = System.currentTimeMillis() - startTime;
                    logger.debug("Completed enriching song {} in {} ms", song.getTitle(), duration);
                    
                    // Throttle API requests to avoid rate limits
                    Thread.sleep(100);
                } catch (Exception e) {
                    logger.error("Error enriching song {} by {}: {}", 
                                song.getTitle(), song.getArtistName(), e.getMessage());
                }
            }, executorService);
            
            futures.add(future);
        }
        
        logger.info("Waiting for {} song enrichment tasks to complete (skipped {} already enriched)", 
                    toEnrichCount, alreadyEnrichedCount);
        
        // Wait for all song enrichment tasks to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        logger.info("All song enrichment tasks completed");
    }
    
    private void enrichSong(Song song, UserMusicData userData) {
        if (song == null) {
            return;
        }
        
        String songTitle = song.getTitle();
        
        try {
            // Make sure we have an artist reference
            Artist artist = song.getArtist();
            
            // If still null, create a new artist from the artistName
            if (artist == null && song.getArtistName() != null) {
                artist = new Artist(song.getArtistName());
                song.setArtist(artist);
            }
            
            // Skip if we don't have enough info
            if (artist == null || artist.getName() == null || artist.getName().isEmpty()) {
                logger.warn("Cannot enrich song {} - missing artist information", songTitle);
                return;
            }
            
            // Continue with enrichment logic
            // Try to get song details from Spotify using track ID if available
            String trackId = song.getSpotifyId();
            Map<String, Object> songInfo;
            
            if (trackId != null && !trackId.isEmpty()) {
                songInfo = spotifyAPI.getTrackInfo(trackId);
            } else {
                // Search by title and artist if ID not available
                songInfo = spotifyAPI.searchTrack(song.getTitle(), song.getArtist().getName());
            }
            
            // Update album if missing
            if ((song.getAlbum() == null || song.getAlbum().isEmpty()) && 
                songInfo.containsKey("album")) {
                song.setAlbum((String) songInfo.get("album"));
            }
            
            // Update duration if missing
            if (song.getDurationMs() <= 0 && songInfo.containsKey("duration_ms")) {
                int duration = ((Number) songInfo.get("duration_ms")).intValue();
                song.setDurationMs(duration);
            }
            
            // Update genres if missing
            if ((song.getGenres() == null || song.getGenres().isEmpty())) {
                // Try getting genres from the song's artist if we have enriched it
                Artist enrichedArtist = userData.getArtists().stream()
                    .filter(a -> a.getName().equals(song.getArtist().getName()))
                    .findFirst()
                    .orElse(null);
                    
                if (enrichedArtist != null && enrichedArtist.getGenres() != null && !enrichedArtist.getGenres().isEmpty()) {
                    song.setGenres(new ArrayList<>(enrichedArtist.getGenres()));
                } else if (songInfo.containsKey("genres")) {
                    @SuppressWarnings("unchecked")
                    List<String> genres = (List<String>) songInfo.get("genres");
                    song.setGenres(genres);
                }
            }
            
                            } catch (Exception e) {
            logger.error("Error enriching song {}: {}", songTitle, e.getMessage());
        }
    }
    
    /**
     * Shuts down the executor service
     */
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
