package models;

import java.util.List;
import java.util.ArrayList;

public class Playlist {
    private String name;
    private List<Song> songs = new ArrayList<>();
    private String description;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<Song> getSongs() { return songs; }
    public void setSongs(List<Song> songs) { this.songs = songs; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
} 