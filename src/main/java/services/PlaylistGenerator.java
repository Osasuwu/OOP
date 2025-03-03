package services;

import models.UserMusicData;
import models.Playlist;
import models.PlaylistPreferences;

public class PlaylistGenerator {

    public Playlist generatePlaylist(UserMusicData userData, PlaylistPreferences preferences) {
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

    private Playlist generateFromHistory(UserMusicData userData, PlaylistPreferences prefs) {
        // Analyze play history patterns
        // Consider:
        // 1. Most played artists/songs
        // 2. Time of day preferences
        // 3. Genre distribution
        // 4. Recent vs old plays
        return new Playlist();
    }

    private Playlist generateFromSongs(UserMusicData userData, PlaylistPreferences prefs) {
        // Use available song data to find similar songs
        // Consider:
        // 1. Genre matching
        // 2. Popularity ranges
        // 3. Audio features if available
        return new Playlist();
    }

    private Playlist generateFromArtists(UserMusicData userData, PlaylistPreferences prefs) {
        // Use artist data to find similar artists and their songs
        // Consider:
        // 1. Genre overlap
        // 2. Popularity ranges
        // 3. Related artists from Spotify
        return new Playlist();
    }

    private Playlist generateFromUserInput(PlaylistPreferences prefs) {
        // Implementation
        return new Playlist();
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
