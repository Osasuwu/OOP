package services.importer.service;

import models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import services.UserAPI.SpotifyAPIManager;
import services.importer.ImportException;

import java.util.*;
import java.util.Date;

/**
 * Implementation of ServiceImportAdapter for Spotify streaming service.
 */
public class SpotifyServiceImportAdapter implements ServiceImportAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpotifyServiceImportAdapter.class);
    private final SpotifyAPIManager spotifyApi;
    
    public SpotifyServiceImportAdapter() {
        this.spotifyApi = new SpotifyAPIManager();
    }
    
    @Override
    public UserMusicData importFromService(Map<String, String> credentials) throws ImportException {
        LOGGER.info("Starting Spotify import");
        
        String accessToken = credentials.get("access_token");
        String userId = credentials.get("user_id"); // Optional, may be derived from token
        
        if (accessToken == null || accessToken.isEmpty()) {
            throw new ImportException("Spotify access token is required");
        }
        
        try {
            // Connect to Spotify API
            connectToSpotifyApi(accessToken);
            
            // Retrieve user's playlists, liked songs, etc.
            List<Map<String, String>> userSongs = fetchUserLibrary(userId);
            
            UserMusicData userData = new UserMusicData();
            
            // Process retrieved data
            for (Map<String, String> songData : userSongs) {
                Artist artist = new Artist(songData.getOrDefault("artistName", "Unknown Artist"));
                artist.setId(UUID.randomUUID().toString()); // Generate ID since this is new data
                
                Song song = new Song(songData.getOrDefault("title", "Unknown Title"), artist.getName());
                song.setId(UUID.randomUUID().toString());
                song.setArtist(artist);
                song.setSpotifyId(songData.get("spotify_id"));
                song.setAlbumName(songData.get("album"));
                
                if (songData.containsKey("duration_ms")) {
                    try {
                        song.setDurationMs(Integer.parseInt(songData.get("duration_ms")));
                    } catch (NumberFormatException e) {
                        // Silently ignore parsing errors
                    }
                }
                
                // Add play history entry if timestamp exists
                if (songData.containsKey("played_at")) {
                    try {
                        PlayHistory playEntry = new PlayHistory();
                        playEntry.setSong(song);
                        playEntry.setTimestamp(new Date(Long.parseLong(songData.get("played_at"))));
                        userData.addPlayHistory(playEntry);
                    } catch (NumberFormatException e) {
                        // Silently ignore parsing errors for timestamps
                    }
                }
                
                // Add data to the result
                userData.addArtist(artist);
                userData.addSong(song);
            }
            
            // Use API manager to fetch additional data if needed
            try {
                // 1. Get user's saved tracks
                List<Map<String, Object>> savedTracks = spotifyApi.getUserSavedTracks(accessToken);
                
                // 2. Get user's top artists
                List<Map<String, Object>> topArtists = spotifyApi.getUserTopArtists(accessToken);
                
                // 3. Get user's recently played tracks
                List<Map<String, Object>> recentlyPlayed = spotifyApi.getUserRecentlyPlayed(accessToken);
                
                // Process additional data
                processTracks(savedTracks, userData);
                processArtists(topArtists, userData);
                processPlayHistory(recentlyPlayed, userData);
            } catch (Exception e) {
                // Log but don't fail if additional data fetching fails
                LOGGER.warn("Error fetching additional Spotify data: {}", e.getMessage());
            }
            
            LOGGER.info("Spotify import completed. Found {} songs, {} artists, {} play history entries",
                      userData.getSongs().size(), userData.getArtists().size(), 
                      userData.getPlayHistory().size());
            
            return userData;
        } catch (Exception e) {
            LOGGER.error("Error importing from Spotify: {}", e.getMessage(), e);
            throw new ImportException("Error importing from Spotify: " + e.getMessage(), e);
        }
    }
    
    private void connectToSpotifyApi(String accessToken) {
        // Initialize connection to Spotify API
        LOGGER.debug("Connecting to Spotify API with token: {}", accessToken.substring(0, 5) + "...");
        // In a real implementation, this would establish the API connection
    }
    
    private List<Map<String, String>> fetchUserLibrary(String userId) {
        // In a real implementation, this would call the Spotify API
        // and return actual user data
        
        // Mock data for example
        List<Map<String, String>> songs = new ArrayList<>();
        
        Map<String, String> song1 = new HashMap<>();
        song1.put("title", "Bohemian Rhapsody");
        song1.put("artistName", "Queen");
        song1.put("spotify_id", "6l8GvAyoUZwWDgF1e4822w");
        song1.put("album", "A Night at the Opera");
        song1.put("duration_ms", "354947");
        songs.add(song1);
        
        Map<String, String> song2 = new HashMap<>();
        song2.put("title", "Imagine");
        song2.put("artistName", "John Lennon");
        song2.put("spotify_id", "7pKfPomDEeI4TPT6EOYjn9");
        song2.put("album", "Imagine");
        song2.put("duration_ms", "183093");
        songs.add(song2);
        
        LOGGER.debug("Fetched {} songs from user library", songs.size());
        return songs;
    }
    
    private void processTracks(List<Map<String, Object>> tracks, UserMusicData userData) {
        // Skip if no tracks
        if (tracks == null || tracks.isEmpty()) return;
        
        LOGGER.debug("Processing {} tracks from Spotify API", tracks.size());
        
        for (Map<String, Object> trackData : tracks) {
            try {
                String title = (String) trackData.get("name");
                String artistName = (String) trackData.get("artist");
                
                // Create artist if needed
                Artist artist = userData.findOrCreateArtist(artistName);
                
                // Create song
                Song song = new Song(title, artistName);
                song.setId(UUID.randomUUID().toString());
                song.setArtist(artist);
                song.setSpotifyId((String) trackData.get("spotify_id"));
                song.setAlbumName((String) trackData.get("album"));
                
                if (trackData.containsKey("duration_ms")) {
                    song.setDurationMs(((Number) trackData.get("duration_ms")).intValue());
                }
                
                @SuppressWarnings("unchecked")
                List<String> genres = (List<String>) trackData.get("genres");
                if (genres != null) {
                    song.setGenres(new ArrayList<>(genres));
                }
                
                userData.addSong(song);
            } catch (Exception e) {
                LOGGER.warn("Error processing Spotify track: {}", e.getMessage());
            }
        }
    }
    
    private void processArtists(List<Map<String, Object>> artists, UserMusicData userData) {
        // Skip if no artists
        if (artists == null || artists.isEmpty()) return;
        
        LOGGER.debug("Processing {} artists from Spotify API", artists.size());
        
        for (Map<String, Object> artistData : artists) {
            try {
                String name = (String) artistData.get("name");
                
                // Find or create artist
                Artist artist = userData.findOrCreateArtist(name);
                
                // Update artist properties
                artist.setSpotifyId((String) artistData.get("spotify_id"));
                
                if (artistData.containsKey("popularity")) {
                    artist.setPopularity(((Number) artistData.get("popularity")).intValue());
                }
                
                @SuppressWarnings("unchecked")
                List<String> genres = (List<String>) artistData.get("genres");
                if (genres != null) {
                    artist.setGenres(new ArrayList<>(genres));
                }
                
                artist.setImageUrl((String) artistData.get("image_url"));
            } catch (Exception e) {
                LOGGER.warn("Error processing Spotify artist: {}", e.getMessage());
            }
        }
    }
    
    private void processPlayHistory(List<Map<String, Object>> history, UserMusicData userData) {
        // Skip if no history
        if (history == null || history.isEmpty()) return;
        
        LOGGER.debug("Processing {} play history entries from Spotify API", history.size());
        
        for (Map<String, Object> entry : history) {
            try {
                // Get or create song
                String title = (String) entry.get("track_name");
                String artistName = (String) entry.get("artist_name");
                
                // Find the song in our collection
                Song song = null;
                for (Song s : userData.getSongs()) {
                    if (s.getTitle().equals(title) && s.getArtist().getName().equals(artistName)) {
                        song = s;
                        break;
                    }
                }
                
                // If song not found, create it
                if (song == null) {
                    Artist artist = userData.findOrCreateArtist(artistName);
                    song = new Song(title, artistName);
                    song.setId(UUID.randomUUID().toString());
                    song.setArtist(artist);
                    userData.addSong(song);
                }
                
                // Create play history entry
                PlayHistory playEntry = new PlayHistory();
                playEntry.setSong(song);
                
                // Parse timestamp
                if (entry.containsKey("played_at")) {
                    playEntry.setTimestamp(new Date(((Number) entry.get("played_at")).longValue()));
                } else {
                    // Use current time if no timestamp
                    playEntry.setTimestamp(new Date());
                }
                
                userData.addPlayHistory(playEntry);
            } catch (Exception e) {
                LOGGER.warn("Error processing Spotify play history: {}", e.getMessage());
            }
        }
    }
    
    @Override
    public String getServiceName() {
        return "spotify";
    }
}
