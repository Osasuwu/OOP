package utils;

import models.Artist;
import models.Song;
import models.UserMusicData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Manages genre normalization and mapping
 */
public class GenreManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenreManager.class);
    private final GenreMapper genreMapper;

    public GenreManager() {
        this.genreMapper = new GenreMapper();
    }

    /**
     * Normalizes all genres in the user's music data
     * 
     * @param data The music data to normalize
     */
    public void normalizeGenres(UserMusicData data) {
        LOGGER.debug("Normalizing genres for {} songs and {} artists",
                data.getSongs().size(), data.getArtists().size());

        // Process songs with genres
        for (Song song : data.getSongs()) {
            if (song.getGenres() != null && !song.getGenres().isEmpty()) {
                Set<String> genres = new HashSet<>(song.getGenres());
                song.setGenres(new ArrayList<>(genreMapper.normalizeGenres(genres)));
            }
        }
        
        // Process artists with genres
        for (Artist artist : data.getArtists()) {
            if (artist.getGenres() != null && !artist.getGenres().isEmpty()) {
                Set<String> genres = new HashSet<>(artist.getGenres());
                artist.setGenres(new ArrayList<>(genreMapper.normalizeGenres(genres)));
            }
        }
        
        LOGGER.debug("Genre normalization completed");
    }
}
