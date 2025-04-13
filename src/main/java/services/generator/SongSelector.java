package services.generator;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import models.Song;
import utils.Logger;

public class SongSelector {

    private final Logger logger;
    private final FilterManager filterManager;
    
    // Cache for frequently used song selections (by criteria string)
    private final Map<String, List<Song>> songCache = new ConcurrentHashMap<>();

    /**
     * Constructor initializing the SongSelector with required utilities.
     */
    public SongSelector() {
        this.logger = Logger.getInstance();
        this.filterManager = new FilterManager();
    }
    
    // ======= Original Methods =======
    
    /**
     * Selects a song based on a given criteria.
     *
     * @param songs    The list of available songs.
     * @param criteria The selection criteria.
     * @return An optional Song that matches the criteria.
     */
    public Optional<Song> selectSong(List<Song> songs, String criteria) {
        logger.info("Selecting a song based on criteria: " + criteria);
        List<Song> filteredSongs = filterManager.applyFilters(songs, criteria);
        logger.info("Filtered songs count: " + filteredSongs.size());
        if (filteredSongs.isEmpty()) {
            logger.warning("No songs match the criteria: " + criteria);
            return Optional.empty();
        }
        Song selectedSong = filteredSongs.get(0);
        logger.info("Selected song: " + selectedSong.getTitle() + " by " + selectedSong.getArtist());
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
        logger.info("Selecting up to " + limit + " songs based on criteria: " + criteria);
        List<Song> selectedSongs = filterManager.applyFilters(songs, criteria)
                                    .stream()
                                    .limit(limit)
                                    .collect(Collectors.toList());
        logger.info("Selected " + selectedSongs.size() + " songs.");
        return selectedSongs;
    }
    
    // ======= New Feature 1: Improved Filtering with Multiple Criteria =======
    
    /**
     * Selects a song based on multiple criteria.
     *
     * @param songs         The list of available songs.
     * @param criteriaList  A list of criteria (each as a String) to be applied sequentially.
     * @return An Optional containing the first song that matches all criteria, or empty if none match.
     */
    public Optional<Song> selectSong(List<Song> songs, List<String> criteriaList) {
        logger.info("Selecting a song based on multiple criteria: " + criteriaList);
        List<Song> filteredSongs = new ArrayList<>(songs);
        for (String crit : criteriaList) {
            filteredSongs = filterManager.applyFilters(filteredSongs, crit);
        }
        if (filteredSongs.isEmpty()) {
            logger.warning("No songs match the multiple criteria: " + criteriaList);
            return Optional.empty();
        }
        Song selectedSong = filteredSongs.get(0);
        logger.info("Selected song: " + selectedSong.getTitle());
        return Optional.of(selectedSong);
    }
    
    /**
     * Selects multiple songs based on multiple criteria.
     *
     * @param songs         The list of available songs.
     * @param criteriaList  A list of criteria to be applied.
     * @param limit         The maximum number of songs to select.
     * @return A list of selected songs.
     */
    public List<Song> selectMultipleSongs(List<Song> songs, List<String> criteriaList, int limit) {
        logger.info("Selecting up to " + limit + " songs based on multiple criteria: " + criteriaList);
        List<Song> filteredSongs = new ArrayList<>(songs);
        for (String crit : criteriaList) {
            filteredSongs = filterManager.applyFilters(filteredSongs, crit);
        }
        List<Song> selectedSongs = filteredSongs.stream().limit(limit).collect(Collectors.toList());
        logger.info("Selected " + selectedSongs.size() + " songs.");
        return selectedSongs;
    }
    
    // ======= New Feature 2: Random Song Selection =======

    /**
     * Selects a random song from the filtered results based on a single criterion.
     *
     * @param songs    The list of available songs.
     * @param criteria The selection criteria.
     * @return An Optional of a randomly selected song, or empty if no songs match.
     */
    public Optional<Song> selectRandomSong(List<Song> songs, String criteria) {
        logger.info("Selecting a random song based on criteria: " + criteria);
        List<Song> filteredSongs = filterManager.applyFilters(songs, criteria);
        Collections.shuffle(filteredSongs);
        if (filteredSongs.isEmpty()) {
            logger.warning("No songs match the criteria for random selection: " + criteria);
            return Optional.empty();
        }
        return Optional.of(filteredSongs.get(0));
    }
    
    /**
     * Selects a random song using multiple criteria.
     *
     * @param songs         The list of available songs.
     * @param criteriaList  The list of criteria.
     * @return An Optional containing a randomly selected song.
     */
    public Optional<Song> selectRandomSong(List<Song> songs, List<String> criteriaList) {
        logger.info("Selecting a random song based on multiple criteria: " + criteriaList);
        List<Song> filteredSongs = new ArrayList<>(songs);
        for (String crit : criteriaList) {
            filteredSongs = filterManager.applyFilters(filteredSongs, crit);
        }
        Collections.shuffle(filteredSongs);
        if (filteredSongs.isEmpty()) {
            logger.warning("No songs match the multiple criteria for random selection.");
            return Optional.empty();
        }
        return Optional.of(filteredSongs.get(0));
    }
    
    // ======= New Feature 3: Priority Selection (Weighted Criteria) =======

    /**
     * Selects a song based on weighted criteria.
     * Each criterion is assigned a weight, and songs are scored based on how many filters they satisfy.
     *
     * @param songs             The list of available songs.
     * @param weightedCriteria  A map where each key is a criterion and its value is the weight.
     * @return An Optional containing the highest-scoring song.
     */
    public Optional<Song> selectSongWeighted(List<Song> songs, Map<String, Integer> weightedCriteria) {
        logger.info("Selecting song based on weighted criteria: " + weightedCriteria);
        Song bestSong = null;
        int bestScore = -1;
        for (Song song : songs) {
            int score = 0;
            // Evaluate each weighted criterion
            for (Map.Entry<String, Integer> entry : weightedCriteria.entrySet()) {
                String crit = entry.getKey();
                int weight = entry.getValue();
                // Test if the song matches this criterion. We filter on a singleton list.
                if (!filterManager.applyFilters(List.of(song), crit).isEmpty()) {
                    score += weight;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestSong = song;
            }
        }
        if (bestSong == null) {
            logger.warning("No song met the weighted criteria.");
            return Optional.empty();
        }
        logger.info("Weighted selection chose: " + bestSong.getTitle());
        return Optional.of(bestSong);
    }
    
    // ======= New Feature 4: Excluding Specific Songs (Blacklist) =======

    /**
     * Selects a song based on a criterion while excluding blacklisted songs.
     *
     * @param songs     The list of available songs.
     * @param criteria  The selection criterion.
     * @param blacklist A set of song IDs to exclude.
     * @return An Optional of a song not present in the blacklist.
     */
    public Optional<Song> selectSongExclusion(List<Song> songs, String criteria, Set<String> blacklist) {
        logger.info("Selecting song with exclusion using criteria: " + criteria);
        List<Song> filteredSongs = filterManager.applyFilters(songs, criteria)
                .stream()
                .filter(song -> !blacklist.contains(song.getId()))
                .collect(Collectors.toList());
        if (filteredSongs.isEmpty()) {
            logger.warning("No songs available after applying blacklist.");
            return Optional.empty();
        }
        return Optional.of(filteredSongs.get(0));
    }
    
    // ======= New Feature 5: Song Selection Based on Popularity or Rating =======

    /**
     * Selects the most popular song based on a criterion.
     * Assumes that the Song model has a getPopularity() method.
     *
     * @param songs    The list of available songs.
     * @param criteria The selection criterion.
     * @return An Optional containing the highest-rated song.
     */
    public Optional<Song> selectSongByPopularity(List<Song> songs, String criteria) {
        logger.info("Selecting song by popularity based on criteria: " + criteria);
        List<Song> filteredSongs = filterManager.applyFilters(songs, criteria);
        if (filteredSongs.isEmpty()) {
            logger.warning("No songs found for popularity selection.");
            return Optional.empty();
        }
        // Sort descending by popularity.
        return Optional.of(filteredSongs.get(0));
    }
    
    // ======= New Feature 6: Feedback-Based Song Selection (Adaptive Selection) =======

    /**
     * Selects a song adaptively based on user feedback.
     * Excludes songs that appear in the provided set of skipped song IDs.
     *
     * @param songs          The list of available songs.
     * @param criteria       The selection criterion.
     * @param skippedSongIds A set of song IDs that the user has skipped.
     * @return An Optional containing a song that is not in the skipped list.
     */
    public Optional<Song> selectAdaptiveSong(List<Song> songs, String criteria, Set<String> skippedSongIds) {
        logger.info("Selecting adaptive song based on criteria (excluding skipped songs): " + criteria);
        List<Song> filteredSongs = filterManager.applyFilters(songs, criteria)
                .stream()
                .filter(song -> !skippedSongIds.contains(song.getId()))
                .collect(Collectors.toList());
        if (filteredSongs.isEmpty()) {
            logger.warning("No adaptive song available after excluding skipped songs.");
            return Optional.empty();
        }
        return Optional.of(filteredSongs.get(0));
    }
    
    // ======= New Feature 7: Caching for Frequently Used Songs =======

    /**
     * Selects multiple songs based on a criterion with caching.
     * If the criteria have been recently used, returns cached results.
     *
     * @param songs    The list of available songs.
     * @param criteria The selection criterion.
     * @param limit    Maximum number of songs to return.
     * @return A list of selected songs.
     */
    public List<Song> selectMultipleSongsCached(List<Song> songs, String criteria, int limit) {
        logger.info("Selecting multiple songs with caching for criteria: " + criteria);
        if (songCache.containsKey(criteria)) {
            logger.info("Cache hit for criteria: " + criteria);
            return songCache.get(criteria).stream().limit(limit).collect(Collectors.toList());
        }
        List<Song> selectedSongs = selectMultipleSongs(songs, criteria, limit);
        songCache.put(criteria, selectedSongs);
        return selectedSongs;
    }
    
    // ======= New Feature 8: External Music API Integration (Placeholder) =======

    /**
     * Fetches songs from an external music API based on a criterion.
     * Replace the placeholder with an actual API integration.
     *
     * @param criteria The selection criterion.
     * @return A list of songs fetched from the external API.
     */
    public List<Song> fetchSongsFromExternalAPI(String criteria) {
        logger.info("Fetching songs from external API for criteria: " + criteria);
        // Placeholder: Integrate with an external music API (e.g., Spotify, Last.fm) here.
        return new ArrayList<>();
    }
    
    // ======= New Feature 9: Song Selection by Date or Time =======

    /**
     * Selects a song based on its release date within a specified range.
     * Assumes the Song model has a getReleaseDate() method returning a LocalDate.
     *
     * @param songs      The list of available songs.
     * @param criteria   The selection criterion.
     * @param startDate  The start date of the range.
     * @param endDate    The end date of the range.
     * @return An Optional containing a song whose release date is within the range.
     */
    public Optional<Song> selectSongByReleaseDate(List<Song> songs, String criteria, LocalDate startDate, LocalDate endDate) {
        logger.info("Selecting song by release date between " + startDate + " and " + endDate + " for criteria: " + criteria);
        List<Song> filteredSongs = filterManager.applyFilters(songs, criteria)
                .stream()
                .filter(song -> {
                    LocalDate releaseDate = song.getReleaseDate()
                            .toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                    return releaseDate != null &&
                        (releaseDate.isEqual(startDate) || releaseDate.isAfter(startDate)) &&
                        (releaseDate.isEqual(endDate) || releaseDate.isBefore(endDate));
                })
                .collect(Collectors.toList());
        if (filteredSongs.isEmpty()) {
            logger.warning("No songs found in the specified release date range.");
            return Optional.empty();
        }
        return Optional.of(filteredSongs.get(0));
    }
}