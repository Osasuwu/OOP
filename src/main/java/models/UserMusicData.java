package models;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Container for user music data, including artists, songs, and listening history.
 * Provides methods to find, add, and manipulate the data.
 */
public class UserMusicData {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserMusicData.class);
    
    private List<Artist> artists;
    private List<Song> songs;
    private List<PlayHistory> playHistory;
    
    public UserMusicData() {
        this.artists = new ArrayList<>();
        this.songs = new ArrayList<>();
        this.playHistory = new ArrayList<>();
    }
    
    public List<Artist> getArtists() {
        return artists;
    }
    
    public void setArtists(List<Artist> artists) {
        this.artists = artists;
    }
    
    public List<Song> getSongs() {
        return songs;
    }
    
    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }
    
    public List<PlayHistory> getPlayHistory() {
        return playHistory;
    }
    
    public void setPlayHistory(List<PlayHistory> playHistory) {
        this.playHistory = playHistory;
    }
    
    /**
     * Adds a song to the list, checking for duplicates based on title + artist
     */
    public void addSong(Song song) {
        if (song == null) return;

        if (song.getId() == null) {
            song.setId(UUID.randomUUID().toString());
        }
        
        boolean exists = false;
        for (Song existingSong : songs) {
            if (existingSong.getTitle().equalsIgnoreCase(song.getTitle()) 
                && existingSong.getArtist().getName().equalsIgnoreCase(song.getArtist().getName())) {
                exists = true;
                break;
            }
        }
        
        if (!exists) {
            songs.add(song);
        }
    }
    
    /**
     * Adds an artist to the list, checking for duplicates based on name
     */
    public void addArtist(Artist artist) {
        if (artist == null) return;

        if(artist.getId() == null) {
            artist.setId(UUID.randomUUID().toString());
        }
        
        boolean exists = false;
        for (Artist existingArtist : artists) {
            if (existingArtist.getName().equalsIgnoreCase(artist.getName())) {
                exists = true;
                break;
            }
        }
        
        if (!exists) {
            artists.add(artist);
        }
    }
    
    /**
     * Adds a play history entry
     */
    public void addPlayHistory(PlayHistory entry) {
        if (entry != null) {
            playHistory.add(entry);
        }
    }
    
    /**
     * Finds an artist by name or creates a new one if not found
     */
    public Artist findOrCreateArtist(String name) {
        if (name == null) return null;
        
        // First attempt to find an existing artist with same name
        for (Artist artist : artists) {
            if (artist.getName().equalsIgnoreCase(name)) {
                return artist;
            }
        }
        
        // Create new artist if not found
        Artist newArtist = new Artist(name);
        newArtist.setId(UUID.randomUUID().toString());
        artists.add(newArtist);
        return newArtist;
    }
    
    /**
     * Finds a song by title and artist or creates a new one if not found
     */
    public Song findOrCreateSong(String title, String artistName) {
        if (title == null || artistName == null) return null;
        
        // Find or create artist
        Artist artist = findOrCreateArtist(artistName);
        
        // First attempt to find existing song
        for (Song song : songs) {
            if (song.getTitle().equalsIgnoreCase(title) 
                && song.getArtist().getName().equalsIgnoreCase(artistName)) {
                return song;
            }
        }
        
        // Create new song if not found
        Song newSong = new Song(title, artistName);
        newSong.setId(UUID.randomUUID().toString());
        newSong.setArtist(artist);
        songs.add(newSong);
        return newSong;
    }
    
    /**
     * Merges another UserMusicData object into this one
     */
    public void merge(UserMusicData other) {
        if (other == null) return;
        
        // Create map of artists by name for efficient lookup
        Map<String, Artist> artistMap = new HashMap<>();
        for (Artist artist : artists) {
            artistMap.put(artist.getName().toLowerCase(), artist);
        }
        
        // Add other artists, updating if they already exist
        for (Artist otherArtist : other.getArtists()) {
            String key = otherArtist.getName().toLowerCase();
            if (artistMap.containsKey(key)) {
                Artist existingArtist = artistMap.get(key);
                // Update fields if they're empty and other has data
                if (existingArtist.getSpotifyId() == null && otherArtist.getSpotifyId() != null) {
                    existingArtist.setSpotifyId(otherArtist.getSpotifyId());
                }
                if (existingArtist.getGenres() == null || existingArtist.getGenres().isEmpty()) {
                    existingArtist.setGenres(otherArtist.getGenres());
                }
                if (existingArtist.getPopularity() == 0 && otherArtist.getPopularity() > 0) {
                    existingArtist.setPopularity(otherArtist.getPopularity());
                }
            } else {
                addArtist(otherArtist);
                artistMap.put(key, otherArtist);
            }
        }
        
        // Create map of songs by composite key for efficient lookup
        Map<String, Song> songMap = new HashMap<>();
        for (Song song : songs) {
            String key = (song.getArtist().getName() + ":" + song.getTitle()).toLowerCase();
            songMap.put(key, song);
        }
        
        // Add other songs, updating if they already exist
        for (Song otherSong : other.getSongs()) {
            String key = (otherSong.getArtist().getName() + ":" + otherSong.getTitle()).toLowerCase();
            if (songMap.containsKey(key)) {
                Song existingSong = songMap.get(key);
                // Update fields if they're empty and other has data
                if (existingSong.getAlbumName() == null && otherSong.getAlbumName() != null) {
                    existingSong.setAlbumName(otherSong.getAlbumName());
                }
                if (existingSong.getGenres() == null || existingSong.getGenres().isEmpty()) {
                    existingSong.setGenres(otherSong.getGenres());
                }
                if (existingSong.getReleaseDate() == null && otherSong.getReleaseDate() != null) {
                    existingSong.setReleaseDate(otherSong.getReleaseDate());
                }
            } else {
                // Need to update artist reference based on our local artists
                Artist matchingArtist = artistMap.get(otherSong.getArtist().getName().toLowerCase());
                if (matchingArtist != null) {
                    otherSong.setArtist(matchingArtist);
                }
                addSong(otherSong);
                songMap.put(key, otherSong);
            }
        }
        
        // Add all play history entries, no need to deduplicate as they can be the same song multiple times
        playHistory.addAll(other.getPlayHistory());
    }
    
    /**
     * Check if the data is empty
     */
    public boolean isEmpty() {
        return artists.isEmpty() && songs.isEmpty() && playHistory.isEmpty();
    }
}