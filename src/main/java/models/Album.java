package models;

import java.util.ArrayList;
import java.util.List;

public class Album {
    private int id;
    private String title;
    private String artist;
    private int year;
    private List<String> songs;
    private double score;
    
    // Constructor
    public Album(int id, String title, String artist, int year) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.year = year;
        this.songs = new ArrayList<>();
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getArtist() {
        return artist;
    }
    
    public void setArtist(String artist) {
        this.artist = artist;
    }
    
    public int getYear() {
        return year;
    }
    
    public void setYear(int year) {
        this.year = year;
    }
    
    public List<String> getSongs() {
        return songs;
    }
    
    public void addSong(String song) {
        this.songs.add(song);
    }

    public void setScore(double score) {
        this.score = score;
    }

    public double getScore() {
        return score;
    }
    
    @Override
    public String toString() {
        return "Album{" +
               "id=" + id +
               ", title='" + title + '\'' +
               ", artist='" + artist + '\'' +
               ", year=" + year +
               ", songs=" + songs +
               '}';
    }
}
