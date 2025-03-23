package services.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import services.data.DataFetcher;
import utils.Logger;

/**
 * Manages filtering of songs based on multiple criteria such as genre, artist, popularity, duration, and more.
 * Ensures efficiency and modularity while providing customizable filtering options.
 */
public class FilterManager {

    private final DataFetcher dataFetcher;
    private final Logger logger;

    /**
     * Constructor initializes required services.
     */
    public FilterManager() {
        this.dataFetcher = new DataFetcher();
        this.logger = Logger.getInstance();
    }

    /**
     * Filters songs based on multiple criteria.
     *
     * @param criteria The filtering conditions as a map (e.g., "genre" -> "pop", "popularity" -> 50).
     * @return A filtered list of songs matching the criteria.
     */
    public List<Map<String, Object>> filterSongs(Map<String, Object> criteria) {
        logger.info("Applying filters: " + criteria);

        List<Map<String, Object>> allSongs = dataFetcher.fetchSongs();

        return allSongs.stream()
                .filter(createFilterPredicate(criteria))
                .collect(Collectors.toList());
    }

    /**
     * Creates a dynamic predicate based on the filtering criteria.
     *
     * @param criteria The filtering conditions as a map.
     * @return A predicate for filtering the songs.
     */
    private Predicate<Map<String, Object>> createFilterPredicate(Map<String, Object> criteria) {
        List<Predicate<Map<String, Object>>> predicates = new ArrayList<>();

        criteria.forEach((key, value) -> {
            switch (key.toLowerCase()) {
                case "genre":
                    predicates.add(song -> value.equals(song.get("genre")));
                    break;
                case "artist":
                    predicates.add(song -> value.equals(song.get("artist")));
                    break;
                case "popularity":
                    predicates.add(song -> (int) song.getOrDefault("popularity", 0) >= (int) value);
                    break;
                case "duration":
                    predicates.add(song -> (int) song.getOrDefault("duration", 0) <= (int) value);
                    break;
                case "year":
                    predicates.add(song -> (int) song.getOrDefault("year", 0) == (int) value);
                    break;
                default:
                    logger.warning("Unknown filter: " + key);
            }
        });

        return predicates.stream().reduce(x -> true, Predicate::and);
    }

    /**
     * Filters songs based on a single condition (shorthand method).
     *
     * @param key   The filter key (e.g., "genre").
     * @param value The filter value (e.g., "pop").
     * @return A list of filtered songs.
     */
    public List<Map<String, Object>> filterBySingleCriteria(String key, Object value) {
        return filterSongs(Collections.singletonMap(key, value));
    }

    /**
     * Finds the top N songs based on popularity.
     *
     * @param topN The number of top songs to return.
     * @return A list of top N songs sorted by popularity.
     */
    public List<Map<String, Object>> getTopSongs(int topN) {
        return dataFetcher.fetchSongs().stream()
                .sorted((a, b) -> Integer.compare((int) b.get("popularity"), (int) a.get("popularity")))
                .limit(topN)
                .collect(Collectors.toList());
    }

    /**
     * Filters songs by a list of multiple genres.
     *
     * @param genres The list of genres to include.
     * @return A list of filtered songs that match at least one genre.
     */
    public List<Map<String, Object>> filterByGenres(List<String> genres) {
        return filterSongs(Collections.singletonMap("genre", genres));
    }
/**
 * Filters a list of Song objects based on the provided criteria.
 * This implementation checks if the song title or the artist's name contains the criteria string (case-insensitive).
 *
 * @param songs    The list of Song objects to filter.
 * @param criteria The filter criteria text.
 * @return A list of Song objects that match the filter.
 */
public List<models.Song> applyFilters(List<models.Song> songs, String criteria) {
    String lowerCriteria = criteria.toLowerCase();
    return songs.stream()
            .filter(song -> song.getTitle().toLowerCase().contains(lowerCriteria) ||
                            song.getArtist().getName().toLowerCase().contains(lowerCriteria))
            .collect(Collectors.toList());
}
    /**
     * Fetches personalized recommendations based on user preferences.
     *
     * @param userHistory The user's listening history as a list of previously played songs.
     * @param maxResults  The maximum number of recommendations to return.
     * @return A list of recommended songs based on listening history.
     */
    public List<Map<String, Object>> getPersonalizedRecommendations(List<Map<String, Object>> userHistory, int maxResults) {
        logger.info("Generating personalized recommendations...");

        Set<String> favoriteGenres = new HashSet<>();
        Set<String> favoriteArtists = new HashSet<>();

        for (Map<String, Object> song : userHistory) {
            favoriteGenres.add((String) song.get("genre"));
            favoriteArtists.add((String) song.get("artist"));
        }

        List<Map<String, Object>> recommendations = new ArrayList<>();

        for (String genre : favoriteGenres) {
            recommendations.addAll(filterBySingleCriteria("genre", genre));
        }

        for (String artist : favoriteArtists) {
            recommendations.addAll(filterBySingleCriteria("artist", artist));
        }

        return recommendations.stream().limit(maxResults).collect(Collectors.toList());
    }
}