package services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import models.Artist;
import models.Playlist;
import models.PlaylistParameters;
import models.PlaylistPreferences;
import models.Song;
import models.UserMusicData;
import utils.GenreMapper;

/**
 * Service that generates playlists based on user music data and preferences
 */
public class PlaylistGenerator {
    private final GenreMapper genreMapper;
    
    public PlaylistGenerator() {
        this.genreMapper = new GenreMapper();
    }
    
    /**
     * Generate a playlist based on user data and preferences
     * @param userData The user's music data
     * @param preferences Playlist generation preferences
     * @return A generated playlist
     */
    public Playlist generatePlaylist(UserMusicData userData, PlaylistParameters params, PlaylistPreferences preferences) {
        // Generate based on available data
        if (!userData.getPlayHistory().isEmpty()) {
            return generateFromHistory(userData, preferences);
        } else if (!userData.getSongs().isEmpty()) {
            return generateFromSongs(userData, preferences);
        } else if (!userData.getArtists().isEmpty()) {
            return generateFromArtists(userData, preferences);
        }
        
        return generateFromUserInput(preferences);
    }
    
    private Playlist generateFromHistory(UserMusicData userData, PlaylistPreferences preferences) {
        // Create a default playlist to start with
        Playlist playlist = new Playlist();
        playlist.setName(preferences.getName());
        
        // Analyze play history to determine favorite genres, artists, and songs
        List<Song> candidateSongs = new ArrayList<>(userData.getSongs());
        
        // Apply filters and select songs
        List<Song> selectedSongs = filterAndSelectSongs(candidateSongs, preferences);
        playlist.setSongs(selectedSongs);
        
        return playlist;
    }
    
    private Playlist generateFromSongs(UserMusicData userData, PlaylistPreferences preferences) {
        Playlist playlist = new Playlist();
        playlist.setName(preferences.getName());
        
        List<Song> candidateSongs = new ArrayList<>(userData.getSongs());
        List<Song> selectedSongs = filterAndSelectSongs(candidateSongs, preferences);
        playlist.setSongs(selectedSongs);
        
        return playlist;
    }
    
    private Playlist generateFromArtists(UserMusicData userData, PlaylistPreferences preferences) {
        Playlist playlist = new Playlist();
        playlist.setName(preferences.getName());
        
        // Create candidate songs from artist data
        List<Song> candidateSongs = new ArrayList<>();
        for (Artist artist : userData.getArtists()) {
            // Add top songs from each artist
            candidateSongs.addAll(artist.getTopSongs());
        }
        
        List<Song> selectedSongs = filterAndSelectSongs(candidateSongs, preferences);
        playlist.setSongs(selectedSongs);
        
        return playlist;
    }
    
    private Playlist generateFromUserInput(PlaylistPreferences preferences) {
        // Create an empty playlist when no user data is available
        Playlist playlist = new Playlist();
        playlist.setName(preferences.getName());
        playlist.setSongs(new ArrayList<>());
        return playlist;
    }
    
    private List<Song> filterAndSelectSongs(List<Song> candidateSongs, PlaylistPreferences preferences) {
        // Filter by genres if specified
        if (preferences.getGenres() != null && !preferences.getGenres().isEmpty()) {
            Set<String> normalizedGenres = preferences.getGenres().stream()
                .map(GenreMapper::normalizeGenre)
                .collect(Collectors.toSet());
            
            candidateSongs = candidateSongs.stream()
                .filter(song -> hasMatchingGenre(song, normalizedGenres))
                .collect(Collectors.toList());
        }
        
        // Apply additional filters
        if (preferences.getExcludeArtists() != null && !preferences.getExcludeArtists().isEmpty()) {
            Set<String> excludedArtists = new HashSet<>(preferences.getExcludeArtists());
            candidateSongs = candidateSongs.stream()
                .filter(song -> !excludedArtists.contains(song.getArtist().getName()))
                .collect(Collectors.toList());
        }
        
        // Sort by relevance (e.g., play count if available)
        candidateSongs.sort(Comparator.comparing(Song::getPlayCount).reversed());
        
        // Select songs for the playlist
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
            
            if (!includedArtists.contains(song.getArtist().getName())) {
                selected.add(song);
                includedArtists.add(song.getArtist().getName());
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
