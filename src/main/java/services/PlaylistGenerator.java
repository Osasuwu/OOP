package services;

import models.User;
import models.Song;
import java.util.ArrayList;
import java.util.List;

public class PlaylistGenerator {
    public List<Song> generatePlaylist(User user) {
        List<Song> playlist = new ArrayList<>();
        for (String genre : user.getFavoriteGenres()) {
            playlist.add(new Song("Sample Song", "Sample Artist", genre));
        }
        return playlist;
    }
}