package services.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    
    // Enhancement 4: Define a static map for genre similarity.
    private static final Map<String, Set<String>> GENRE_SIMILARITY = Map.of(
        "hip-hop", Set.of("hip-hop", "rap", "r&b"),
        "electronic", Set.of("electronic", "edm", "house", "techno"),
        "rock", Set.of("rock", "alternative", "indie")
    );
    
    // Enhancement 2: In-memory indexes for performance lookups.
    private Map<String, List<Map<String, Object>>> genreIndex = new ConcurrentHashMap<>();
    private Map<String, List<Map<String, Object>>> artistIndex = new ConcurrentHashMap<>();

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
        // Build indexes for performance.
        buildIndexes(allSongs);
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
                    if (value instanceof List) {
                        // Enhancement 1: Support filtering by multiple genres.
                        List<String> genreList = (List<String>) value;
                        predicates.add(song -> {
                            Object g = song.get("genre");
                            return g != null && genreList.contains(g.toString());
                        });
                    } else if (value instanceof String) {
                        String genreVal = ((String) value).toLowerCase().trim();
                        // Enhancement 4: Use genre similarity for matching.
                        Set<String> relatedGenres = GENRE_SIMILARITY.getOrDefault(genreVal, Set.of(genreVal));
                        predicates.add(song -> {
                            Object songGenreObj = song.get("genre");
                            if (songGenreObj == null) return false;
                            String songGenre = songGenreObj.toString().toLowerCase().trim();
                            return relatedGenres.contains(songGenre);
                        });
                    }
                    break;
                case "artist":
                    predicates.add(song -> value.equals(song.get("artist")));
                    break;
                case "popularity":
                    if (value instanceof Map) {
                        // Enhancement 3: Allow range filtering.
                        Map<String, Integer> range = (Map<String, Integer>) value;
                        int min = range.getOrDefault("min", Integer.MIN_VALUE);
                        int max = range.getOrDefault("max", Integer.MAX_VALUE);
                        predicates.add(song -> {
                            int popularity = (int) song.getOrDefault("popularity", 0);
                            return popularity >= min && popularity <= max;
                        });
                    } else {
                        predicates.add(song -> (int) song.getOrDefault("popularity", 0) >= (int) value);
                    }
                    break;
                case "duration":
                    if (value instanceof Map) {
                        Map<String, Integer> range = (Map<String, Integer>) value;
                        int min = range.getOrDefault("min", Integer.MIN_VALUE);
                        int max = range.getOrDefault("max", Integer.MAX_VALUE);
                        predicates.add(song -> {
                            int duration = (int) song.getOrDefault("duration", 0);
                            return duration >= min && duration <= max;
                        });
                    } else {
                        predicates.add(song -> (int) song.getOrDefault("duration", 0) <= (int) value);
                    }
                    break;
                case "year":
                    if (value instanceof Map) {
                        Map<String, Integer> range = (Map<String, Integer>) value;
                        int min = range.getOrDefault("min", Integer.MIN_VALUE);
                        int max = range.getOrDefault("max", Integer.MAX_VALUE);
                        predicates.add(song -> {
                            int year = (int) song.getOrDefault("year", 0);
                            return year >= min && year <= max;
                        });
                    } else {
                        predicates.add(song -> (int) song.getOrDefault("year", 0) == (int) value);
                    }
                    break;
                default:
                    logger.warning("Unknown filter: " + key);
            }
        });
        return predicates.stream().reduce(x -> true, Predicate::and);
    }

    /**
     * Shorthand method to filter songs based on a single condition.
     *
     * @param key   The filter key (e.g., "genre").
     * @param value The filter value (e.g., "pop").
     * @return A list of filtered songs.
     */
    public List<Map<String, Object>> filterBySingleCriteria(String key, Object value) {
        if ("genre".equalsIgnoreCase(key) && value instanceof String) {
            return genreIndex.getOrDefault(value, Collections.emptyList());
        }
        if ("artist".equalsIgnoreCase(key) && value instanceof String) {
            return artistIndex.getOrDefault(value, Collections.emptyList());
        }
        return filterSongs(Collections.singletonMap(key, value));
    }

    /**
     * Builds in-memory indexes for songs based on genre and artist for faster lookups.
     *
     * @param songs The list of all songs.
     */
    private void buildIndexes(List<Map<String, Object>> songs) {
        genreIndex.clear();
        artistIndex.clear();
        for (Map<String, Object> song : songs) {
            String genre = (String) song.get("genre");
            if (genre != null) {
                genreIndex.computeIfAbsent(genre, k -> new ArrayList<>()).add(song);
            }
            String artist = (String) song.get("artist");
            if (artist != null) {
                artistIndex.computeIfAbsent(artist, k -> new ArrayList<>()).add(song);
            }
        }
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
     * Applies fuzzy search filters to a list of Song objects for better title and artist matching.
     *
     * @param songs    The list of Song objects to filter.
     * @param criteria The filter criteria text.
     * @return A list of Song objects that match the filter.
     */
    public List<models.Song> applyFilters(List<models.Song> songs, String criteria) {
        String lowerCriteria = criteria.toLowerCase();
        return songs.stream()
                .filter(song -> levenshteinDistance(song.getTitle().toLowerCase(), lowerCriteria) < 3 ||
                                levenshteinDistance(song.getArtist().getName().toLowerCase(), lowerCriteria) < 3)
                .collect(Collectors.toList());
    }

    /**
     * Computes the Levenshtein distance between two strings.
     *
     * @param s1 The first string.
     * @param s2 The second string.
     * @return The Levenshtein distance.
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j],
                                    Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                }
            }
        }
        return dp[s1.length()][s2.length()];
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
        // Compute genre weights based on frequency.
        Map<String, Integer> genreWeight = computeGenreWeight(userHistory);
        
        return genreWeight.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .flatMap(entry -> filterBySingleCriteria("genre", entry.getKey()).stream())
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    /**
     * Computes weights for each genre based on the user's listening history.
     *
     * @param history The user's listening history.
     * @return A map of genre weights.
     */
    private Map<String, Integer> computeGenreWeight(List<Map<String, Object>> history) {
        Map<String, Integer> genreCount = new ConcurrentHashMap<>();
        for (Map<String, Object> song : history) {
            String genre = (String) song.get("genre");
            if (genre != null) {
                genreCount.put(genre, genreCount.getOrDefault(genre, 0) + 1);
            }
        }
        return genreCount;
    }
}