package services;

import models.*;
import utils.GenreMapper;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that generates playlists based on user music data and preferences
 */
public class PlaylistGeneratorService {
    private final GenreMapper genreMapper;
    
    public PlaylistGeneratorService() {
        this.genreMapper = new GenreMapper();
    }
    
    /**
     * Generate a playlist based on user data and preferences
     * @param userData The user's music data
     * @param preferences Playlist generation preferences
     * @return A generated playlist
     */
    public Playlist generatePlaylist(UserMusicData userData, PlaylistPreferences preferences) {
        Playlist playlist = new Playlist();
        playlist.setName(preferences.getName());
        
        List<Song> candidateSongs = new ArrayList<>(userData.getSongs());
        
        // Filter by genres if specified
        if (preferences.getGenres() != null && !preferences.getGenres().isEmpty()) {
            Set<String> normalizedGenres = preferences.getGenres().stream()
                .map(genreMapper::normalizeGenre)
                .collect(Collectors.toSet());
            
            candidateSongs = candidateSongs.stream()
                .filter(song -> hasMatchingGenre(song, normalizedGenres))
                .collect(Collectors.toList());
        }
        
        // Apply additional filters
        if (preferences.getExcludeArtists() != null && !preferences.getExcludeArtists().isEmpty()) {
            Set<String> excludedArtists = new HashSet<>(preferences.getExcludeArtists());
            candidateSongs = candidateSongs.stream()
                .filter(song -> !excludedArtists.contains(song.getArtistName()))
                .collect(Collectors.toList());
        }
        
        // Sort by relevance (e.g., play count if available)
        candidateSongs.sort(Comparator.comparing(Song::getPlayCount).reversed());
        
        // Select songs for the playlist
        List<Song> selectedSongs = selectSongs(candidateSongs, preferences);
        playlist.setSongs(selectedSongs);
        
        return playlist;
    }
    
    private boolean hasMatchingGenre(Song song, Set<String> targetGenres) {
        if (song.getGenres() == null || song.getGenres().isEmpty()) {
            return false;
        }
        
        return song.getGenres().stream()
            .map(genreMapper::normalizeGenre)
            .anyMatch(targetGenres::contains);
    }
    
    private List<Song> selectSongs(List<Song> candidateSongs, PlaylistPreferences preferences) {
        int count = preferences.getSongCount();
        if (count <= 0) {
            count = 20; // Default song count
        }
        
        // Limit to requested count
        List<Song> selectedSongs = new ArrayList<>();
        
        if (candidateSongs.size() <= count) {
            // Not enough songs, use all available
            selectedSongs.addAll(candidateSongs);
        } else {
            // Select songs based on strategy
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
        }
        
        return selectedSongs;
    }
    
    private List<Song> selectRandomSongs(List<Song> candidateSongs, int count) {
        List<Song> shuffled = new ArrayList<>(candidateSongs);
        Collections.shuffle(shuffled);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }
    
    private List<Song> selectPopularSongs(List<Song> candidateSongs, int count) {
        // Already sorted by play count in the calling method
        return candidateSongs.subList(0, Math.min(count, candidateSongs.size()));
    }
    
    private List<Song> selectDiverseSongs(List<Song> candidateSongs, int count) {
        // Select songs with diverse artists
        List<Song> selected = new ArrayList<>();
        Set<String> includedArtists = new HashSet<>();
        
        // First pass: get one song per artist
        for (Song song : candidateSongs) {
            if (selected.size() >= count) break;
            
            if (!includedArtists.contains(song.getArtistName())) {
                selected.add(song);
                includedArtists.add(song.getArtistName());
            }
        }
        
        // Second pass: fill remaining slots
        if (selected.size() < count) {
            for (Song song : candidateSongs) {
                if (selected.size() >= count) break;
                
                if (!selected.contains(song)) {
                    selected.add(song);
                }
            }
        }
        
        return selected;
    }
    
    private List<Song> selectBalancedSongs(List<Song> candidateSongs, int count) {
        // Balance between diversity and popularity
        List<Song> popular = selectPopularSongs(candidateSongs, count/2);
        List<Song> diverse = selectDiverseSongs(
            candidateSongs.stream()
                .filter(s -> !popular.contains(s))
                .collect(Collectors.toList()),
            count - popular.size()
        );
        
        List<Song> result = new ArrayList<>(popular);
        result.addAll(diverse);
        return result;
    }
}
