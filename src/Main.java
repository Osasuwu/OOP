package main;

import models.User;
import models.Song;
import services.PlaylistGenerator;
import services.MusicAPIService;
import java.util.Arrays;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        User user = new User("Petr", Arrays.asList("Indie", "Rock"));
        PlaylistGenerator generator = new PlaylistGenerator();
        MusicAPIService apiService = new MusicAPIService();
        
        apiService.fetchSongsFromSpotify();
        
        List<Song> playlist = generator.generatePlaylist(user);
        System.out.println("Generated Playlist for " + user.getUsername() + ":");
        for (Song song : playlist) {
            System.out.println(song.getTitle() + " by " + song.getArtist() + " (" + song.getGenre() + ")");
        }
    }
}