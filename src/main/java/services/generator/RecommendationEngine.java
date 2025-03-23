package services.recommendation;

import services.data.DataFetcher;
import services.filter.FilterManager;
import services.utils.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates song recommendations based on user listening history, preferences, and popularity.
 * Utilizes filtering and scoring mechanisms to deliver highly accurate suggestions.
 */
public class RecommendationEngine {

    private final DataFetcher dataFetcher;
    private final FilterManager filterManager;
    private final Logger logger;

    /**
     * Constructor initializes required services.
     */
    public RecommendationEngine() {
        this.dataFetcher = new DataFetcher();
        this.filterManager = new FilterManager();
        this.logger = new Logger();
    }

    /**
     * Generates recommendations based on user listening history.
     *
     * @param userHistory A list of songs the user has previously listened to.
     * @param maxResults  The maximum number of recommendations to return.
     * @return A list of recommended songs.
     */
    public List<Map<String, Object>> generateRecommendations(List<Map<String, Object>> userHistory, int maxResults) {
        logger.logInfo("Generating personalized recommendations for user...");

        if (userHistory.isEmpty()) {
            logger.logWarning("User history is empty. Returning top trending songs instead.");
            return getTrendingSongs(maxResults);
        }

        Set<String> favoriteGenres = new HashSet<>();
        Set<String> favoriteArtists = new HashSet<>();
        Map<String, Integer> genreCount = new HashMap<>();
        Map<String, Integer> artistCount = new HashMap<>();

        // Analyze user history
        for (Map<String, Object> song : userHistory) {
            String genre = (String) song.get("genre");
            String artist = (String) song.get("artist");

            favoriteGenres.add(genre);
            favoriteArtists.add(artist);

            genreCount.put(genre, genreCount.getOrDefault(genre, 0) + 1);
            artistCount.put(artist, artistCount.getOrDefault(artist, 0) + 1);
        }

        // Sort genres and artists by frequency
        List<String> sortedGenres = genreCount.entrySet()
                .stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<String> sortedArtists = artistCount.entrySet()
                .stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        logger.logInfo("User's favorite genres: " + sortedGenres);
        logger.logInfo("User's favorite artists: " + sortedArtists);

        // Fetch songs matching user preferences
        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (String genre : sortedGenres) {
            recommendations.addAll(filterManager.filterBySingleCriteria("genre", genre));
        }
        for (String artist : sortedArtists) {
            recommendations.addAll(filterManager.filterBySingleCriteria("artist", artist));
        }

        // Remove duplicates and limit results
        return recommendations.stream()
                .distinct()
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    /**
     * Fetches the top trending songs based on popularity.
     *
     * @param topN The number of top songs to return.
     * @return A list of the most popular songs.
     */
    public List<Map<String, Object>> getTrendingSongs(int topN) {
        logger.logInfo("Fetching top " + topN + " trending songs...");
        return filterManager.getTopSongs(topN);
    }

    /**
     * Generates recommendations by mixing trending and personalized songs.
     *
     * @param userHistory User's listening history.
     * @param maxResults  The maximum number of recommendations.
     * @return A list of mixed recommendations.
     */
    public List<Map<String, Object>> generateHybridRecommendations(List<Map<String, Object>> userHistory, int maxResults) {
        logger.logInfo("Generating hybrid recommendations...");
        List<Map<String, Object>> personalized = generateRecommendations(userHistory, maxResults / 2);
        List<Map<String, Object>> trending = getTrendingSongs(maxResults / 2);

        List<Map<String, Object>> combined = new ArrayList<>();
        combined.addAll(personalized);
        combined.addAll(trending);

        return combined.stream()
                .distinct()
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    /**
     * Generates recommendations based on user preferences.
     *
     * @param favoriteGenres  List of favorite genres.
     * @param favoriteArtists List of favorite artists.
     * @param maxResults      Maximum number of recommendations.
     * @return A list of recommended songs.
     */
    public List<Map<String, Object>> generateRecommendationsFromPreferences(List<String> favoriteGenres, List<String> favoriteArtists, int maxResults) {
        logger.logInfo("Generating recommendations based on user preferences...");

        List<Map<String, Object>> recommendations = new ArrayList<>();
        for (String genre : favoriteGenres) {
            recommendations.addAll(filterManager.filterBySingleCriteria("genre", genre));
        }
        for (String artist : favoriteArtists) {
            recommendations.addAll(filterManager.filterBySingleCriteria("artist", artist));
        }

        return recommendations.stream()
                .distinct()
                .limit(maxResults)
                .collect(Collectors.toList());
    }
}
