package services.selection;

import services.utils.Logger;
import services.music.Song;
import services.utils.FilterManager;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The SongSelector class is responsible for selecting songs from a given list
 * based on user preferences and filtering criteria.
 */
public class SongSelector {

    private final Logger logger;
    private final FilterManager filterManager;

    /**
     * Constructor initializing the SongSelector with required utilities.
     */
    public SongSelector() {
        this.logger = new Logger();
        this.filterManager = new FilterManager();
    }

    /**
     * Selects a song based on a given criteria.
     *
     * @param songs    The list of available songs.
     * @param criteria The selection criteria.
     * @return An optional Song that matches the criteria.
     */
    public Optional<Song> selectSong(List<Song> songs, String criteria) {
        logger.logInfo("Selecting a song based on criteria: " + criteria);

        // Filter the songs using the provided criteria
        List<Song> filteredSongs = filterManager.applyFilters(songs, criteria);
        logger.logInfo("Filtered songs count: " + filteredSongs.size());

        if (filteredSongs.isEmpty()) {
            logger.logWarning("No songs match the criteria: " + criteria);
            return Optional.empty();
        }

        // Select the first matching song
        Song selectedSong = filteredSongs.get(0);
        logger.logInfo("Selected song: " + selectedSong.getTitle() + " by " + selectedSong.getArtist());

        return Optional.of(selectedSong);
    }

    /**
     * Selects multiple songs that match the criteria.
     *
     * @param songs    The list of available songs.
     * @param criteria The selection criteria.
     * @param limit    The maximum number of songs to select.
     * @return A list of selected songs.
     */
    public List<Song> selectMultipleSongs(List<Song> songs, String criteria, int limit) {
        logger.logInfo("Selecting up to " + limit + " songs based on criteria: " + criteria);

        // Filter and limit the results
        List<Song> selectedSongs = filterManager.applyFilters(songs, criteria)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());

        logger.logInfo("Selected " + selectedSongs.size() + " songs.");

        return selectedSongs;
    }
}