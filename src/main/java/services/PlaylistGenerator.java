package services;

import java.util.ArrayList;
import java.util.Collections;
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
     * @param params Playlist generation parameters
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
    
    public Playlist generate(PlaylistParameters params) {
        // For initial testing, implement a simple dummy generation:
        Playlist playlist = new Playlist(params.getName());
        List<Song> songs = new ArrayList<>();
    
        // For demonstration, create a number of dummy songs equal to params.getSongCount()
        int count = params.getSongCount();
        for (int i = 0; i < count; i++) {
            // Create dummy song titles and artists.
            Song song = new Song("Song " + (i + 1), "Artist " + (i + 1));
            songs.add(song);
        }
    
        playlist.setSongs(songs);  // Make sure Playlist has a setSongs(List<Song>) method.
        return playlist;
    }
    
    private Playlist generateFromHistory(UserMusicData userData, PlaylistPreferences preferences) {
        // Create a default playlist using play history information
        Playlist playlist = new Playlist();
        playlist.setName(preferences.getName());
        
        // For history-based generation, we use available songs as candidate songs
        List<Song> candidateSongs = new ArrayList<>(userData.getSongs());
        
        // Apply filters and select songs based on the preferences
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
        
        // Create candidate songs from artist data by gathering top songs
        List<Song> candidateSongs = new ArrayList<>();
        for (Artist artist : userData.getArtists()) {
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
        // Assumes candidateSongs are already sorted by play count (if available)
        return candidateSongs.subList(0, Math.min(count, candidateSongs.size()));
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