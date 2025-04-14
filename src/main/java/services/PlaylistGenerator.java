package services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import models.Playlist;
import models.PlaylistParameters;
import models.PlaylistPreferences;
import models.Song;
import services.database.MusicDatabaseManager;
import utils.GenreMapper;
import utils.Logger;

/**
 * Service that generates playlists based on user preferences and parameters
 */
public class PlaylistGenerator {
    
    private MusicDatabaseManager dbManager;
    private Logger logger;
    
    public PlaylistGenerator() {
        this.logger = Logger.getInstance();
    }
    
    /**
     * Sets the database manager for retrieving songs
     * @param dbManager The music database manager
     */
    public void setDatabaseManager(MusicDatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    /**
     * Generate a playlist based on parameters and preferences
     * @param params Playlist generation parameters
     * @param preferences Playlist generation preferences
     * @return A generated playlist
     */
    public Playlist generatePlaylist(PlaylistParameters params, PlaylistPreferences preferences) {
        // Create a new playlist with the specified name
        Playlist playlist = new Playlist(params.getName());
        
        try {
            // Get songs from database based on preferences
            List<Song> candidateSongs = fetchCandidateSongs(params, preferences);
            
            // If no songs were found, return empty playlist
            if (candidateSongs.isEmpty()) {
                logger.warning("No candidate songs found for playlist generation");
                return playlist;
            }
            
            // Apply filters and select songs based on preferences
            List<Song> selectedSongs = filterAndSelectSongs(candidateSongs, preferences);
            playlist.setSongs(selectedSongs);
            
            logger.info("Generated playlist with " + selectedSongs.size() + " songs");
        } catch (Exception e) {
            logger.error("Error generating playlist: " + e.getMessage());
            e.printStackTrace();
        }
        
        return playlist;
    }
    
    /**
     * Fetches candidate songs from the database based on parameters and preferences
     */
    private List<Song> fetchCandidateSongs(PlaylistParameters params, PlaylistPreferences preferences) {
        if (dbManager == null) {
            logger.error("Database manager is not initialized");
            return new ArrayList<>();
        }
        
        // Fetch songs based on parameters
        List<Song> candidateSongs = new ArrayList<>();
        
        // If specific genres are specified in parameters, use them
        if (params.getGenres() != null && !params.getGenres().isEmpty()) {
            for (String genre : params.getGenres()) {
                candidateSongs.addAll(dbManager.getSongsByGenre(genre));
            }
        }
        // If specific artists are specified in parameters, use them
        else if (params.getArtists() != null && !params.getArtists().isEmpty()) {
            for (String artist : params.getArtists()) {
                candidateSongs.addAll(dbManager.getSongsByArtist(artist));
            }
        }
        // Otherwise, use preferences from user's history
        else {
            // If preferences indicate genres, use them
            if (preferences.getGenres() != null && !preferences.getGenres().isEmpty()) {
                for (String genre : preferences.getGenres()) {
                    candidateSongs.addAll(dbManager.getSongsByGenre(genre));
                }
            } else {
                // Get user's top songs
                candidateSongs = dbManager.getTopSongs(preferences.getSongCount() * 3);
            }
        }
        
        // If we still don't have enough songs, get popular songs
        if (candidateSongs.size() < preferences.getSongCount()) {
            candidateSongs.addAll(dbManager.getPopularSongs(preferences.getSongCount() * 2));
        }
        
        return candidateSongs;
    }
    
    /**
     * For basic testing and backward compatibility
     */
    public Playlist generate(PlaylistParameters params) {
        Playlist playlist = new Playlist(params.getName());
        List<Song> songs = new ArrayList<>();
    
        int count = params.getSongCount();
        for (int i = 0; i < count; i++) {
            Song song = new Song("Song " + (i + 1), "Artist " + (i + 1));
            songs.add(song);
        }
    
        playlist.setSongs(songs);
        return playlist;
    }
    
    private List<Song> filterAndSelectSongs(List<Song> candidateSongs, PlaylistPreferences preferences) {
        // Filter by genres if specified in the preferences
        if (preferences.getGenres() != null && !preferences.getGenres().isEmpty()) {
            Set<String> normalizedGenres = preferences.getGenres().stream()
                .map(GenreMapper::normalizeGenre)
                .collect(Collectors.toSet());
            
            candidateSongs = candidateSongs.stream()
                .filter(song -> hasMatchingGenre(song, normalizedGenres))
                .collect(Collectors.toList());
        }
        
        // Further filter: exclude songs by artists specified to be excluded
        if (preferences.getExcludeArtists() != null && !preferences.getExcludeArtists().isEmpty()) {
            Set<String> excludedArtists = new HashSet<>(preferences.getExcludeArtists());
            candidateSongs = candidateSongs.stream()
                .filter(song -> !excludedArtists.contains(song.getArtist().getName()))
                .collect(Collectors.toList());
        }
        
        // Select songs from the filtered candidate list based on the selection strategy
        return selectSongs(candidateSongs, preferences);
    }
    
    private boolean hasMatchingGenre(Song song, Set<String> targetGenres) {
        if (song.getGenres() == null || song.getGenres().isEmpty()) {
            return false;
        }
        
        return song.getGenres().stream()
            .map(GenreMapper::normalizeGenre)
            .anyMatch(targetGenres::contains);
    }
    
    private List<Song> selectSongs(List<Song> candidateSongs, PlaylistPreferences preferences) {
        int count = preferences.getSongCount();
        if (count <= 0) {
            count = 20; // Use 20 as the default song count
        }
        
        // If there are not enough candidate songs, return them all
        if (candidateSongs.size() <= count) {
            return new ArrayList<>(candidateSongs);
        }
        
        List<Song> selectedSongs;
        // Choose song selection strategy based on preferences
        switch (preferences.getSelectionStrategy()) {
            case RANDOM:
                selectedSongs = selectRandomSongs(candidateSongs, count);
                break;
            case POPULAR:
                selectedSongs = selectPopularSongs(candidateSongs, count);
                break;
            case DIVERSE:
                selectedSongs = selectDiverseSongs(candidateSongs, count);
                break;
            default:
                selectedSongs = selectBalancedSongs(candidateSongs, count);
        }
        
        return selectedSongs;
    }
    
    private List<Song> selectRandomSongs(List<Song> candidateSongs, int count) {
        List<Song> shuffled = new ArrayList<>(candidateSongs);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }
    
    private List<Song> selectPopularSongs(List<Song> candidateSongs, int count) {
        // Sort by popularity and take the top ones
        List<Song> sorted = candidateSongs.stream()
            .sorted((s1, s2) -> Integer.compare(s2.getPopularity(), s1.getPopularity()))
            .collect(Collectors.toList());
        
        return sorted.subList(0, Math.min(count, sorted.size()));
    }
    
    private List<Song> selectDiverseSongs(List<Song> candidateSongs, int count) {
        // Select songs so that a wide range of artists is represented
        List<Song> selected = new ArrayList<>();
        Set<String> includedArtists = new HashSet<>();
        
        // First pass: pick one song per artist
        for (Song song : candidateSongs) {
            if (selected.size() >= count)
                break;
            
            if (!includedArtists.contains(song.getArtist().getName())) {
                selected.add(song);
                includedArtists.add(song.getArtist().getName());
            }
        }
        
        // Second pass: if needed, fill remaining slots with any songs not already selected
        if (selected.size() < count) {
            for (Song song : candidateSongs) {
                if (selected.size() >= count)
                    break;
                
                if (!selected.contains(song)) {
                    selected.add(song);
                }
            }
        }
        
        return selected;
    }
    
    private List<Song> selectBalancedSongs(List<Song> candidateSongs, int count) {
        // Balance between popularity and diversity: half from popular songs, half from diverse selection.
        int popularCount = count / 2;
        List<Song> popular = selectPopularSongs(candidateSongs, popularCount);
        List<Song> remainder = candidateSongs.stream()
                                .filter(s -> !popular.contains(s))
                                .collect(Collectors.toList());
        List<Song> diverse = selectDiverseSongs(remainder, count - popular.size());
        
        List<Song> result = new ArrayList<>(popular);
        result.addAll(diverse);
        return result;
    }
}