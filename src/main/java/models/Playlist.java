package models;

import java.util.ArrayList;
import java.util.List;

public class Playlist {
    private String name;
    private List<Song> songs = new ArrayList<>(); // List to hold songs
    private String description;

    // No-argument constructor
    public Playlist() {
        this.name = "Unnamed Playlist"; // Default name
    }

    // Constructor that accepts a String
    public Playlist(String name) {
        this.name = name;
    }

    // Getter and setter for name
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    // Getter and setter for songs
    public List<Song> getSongs() {
        return songs;
    }
    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    // Getter and setter for description
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    // Added method: addSong
    public boolean addSong(Song song) {
        // Check if the song already exists in the list.
        // This uses the equals method of Song. Ensure Song has proper equals/hashCode or adjust if needed.
        if (!songs.contains(song)) {
            songs.add(song);
            return true;
        }
        return false;
    }

    // Optionally, you can add a removeSong method as well if needed:
    public boolean removeSong(Song song) {
        return songs.remove(song);
    }

}