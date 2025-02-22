package services;

import models.User;
import models.Song;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlaylistGenerator {
    private Connection connection;

    
    public PlaylistGenerator(Connection connection) {
        this.connection = connection;
    }

    public List<Song> generatePlaylist(User user) { //creates an empty list to store everything
        List<Song> playlist = new ArrayList<>();
        Random random = new Random(); //random selection

        for (String genre : user.getFavoriteGenres()) {
            List<Song> genreSongs = fetchSongsByGenre(genre); //loop through all the favorite songs of the user
            
            if (!genreSongs.isEmpty()) {
                
                Song randomSong = genreSongs.get(random.nextInt(genreSongs.size())); //pick a random song from the genre that listner prefurs
                playlist.add(randomSong);
            }
        }
        return playlist;
    }

    private List<Song> fetchSongsByGenre(String genre) {
        List<Song> songs = new ArrayList<>();
        String sql = "SELECT title, artist, genre FROM songs WHERE genre = ?"; //fetch the songs from the databass

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, genre);
            ResultSet resultSet = statement.executeQuery();

            while (resultSet.next()) {
                String title = resultSet.getString("title");
                String artist = resultSet.getString("artist");
                String genreFromDB = resultSet.getString("genre");
                songs.add(new Song(title, artist, genreFromDB));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return songs;
    }
} 
