package services.music.GenreMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GenreMapper class is responsible for mapping different music genres
 * to their related categories and subgenres.
 *
 * This class provides functionality to:
 * - Retrieve genre categories.
 * - Fetch similar genres.
 * - Convert genres to standard classifications.
 */
public class GenreMapper {

    private final Map<String, String> genreCategoryMap;
    private final Map<String, List<String>> relatedGenresMap;

    /**
     * Constructor initializes the genre mappings.
     */
    public GenreMapper() {
        genreCategoryMap = new HashMap<>();
        relatedGenresMap = new HashMap<>();
        initializeGenreMappings();
    }

    /**
     * Populates genre mappings with predefined values.
     */
    private void initializeGenreMappings() {
        // Populating main genre categories
        genreCategoryMap.put("Rock", "Alternative & Rock");
        genreCategoryMap.put("Metal", "Alternative & Rock");
        genreCategoryMap.put("Pop", "Pop & Mainstream");
        genreCategoryMap.put("Hip-Hop", "Rap & Hip-Hop");
        genreCategoryMap.put("Rap", "Rap & Hip-Hop");
        genreCategoryMap.put("Jazz", "Jazz & Blues");
        genreCategoryMap.put("Blues", "Jazz & Blues");
        genreCategoryMap.put("Electronic", "EDM & Electronic");
        genreCategoryMap.put("EDM", "EDM & Electronic");
        genreCategoryMap.put("Classical", "Classical & Instrumental");
        genreCategoryMap.put("Instrumental", "Classical & Instrumental");

        // Populating related genres
        relatedGenresMap.put("Rock", Arrays.asList("Alternative", "Indie Rock", "Hard Rock"));
        relatedGenresMap.put("Metal", Arrays.asList("Heavy Metal", "Death Metal", "Power Metal"));
        relatedGenresMap.put("Pop", Arrays.asList("Dance Pop", "Synth Pop", "Indie Pop"));
        relatedGenresMap.put("Hip-Hop", Arrays.asList("Trap", "Boom Bap", "Lo-Fi Hip-Hop"));
        relatedGenresMap.put("Jazz", Arrays.asList("Smooth Jazz", "Swing", "Bebop"));
        relatedGenresMap.put("Electronic", Arrays.asList("House", "Techno", "Dubstep"));
        relatedGenresMap.put("Classical", Arrays.asList("Baroque", "Romantic", "Modern Classical"));
    }

    /**
     * Returns the category of a given genre.
     *
     * @param genre The name of the genre.
     * @return The category it belongs to, or "Unknown" if not found.
     */
    public String getGenreCategory(String genre) {
        return genreCategoryMap.getOrDefault(genre, "Unknown");
    }

    /**
     * Retrieves a list of related genres based on the given genre.
     *
     * @param genre The genre to find related genres for.
     * @return A list of related genres or an empty list if none found.
     */
    public List<String> getRelatedGenres(String genre) {
        return relatedGenresMap.getOrDefault(genre, Collections.emptyList());
    }

    /**
     * Maps an unknown genre to the closest available standard genre.
     *
     * @param genre The input genre name.
     * @return The standardized genre name or "Unknown" if no match is found.
     */
    public String standardizeGenre(String genre) {
        String lowerCaseGenre = genre.toLowerCase();

        for (String key : genreCategoryMap.keySet()) {
            if (key.toLowerCase().contains(lowerCaseGenre) || lowerCaseGenre.contains(key.toLowerCase())) {
                return key;
            }
        }

        return "Unknown";
    }

    /**
     * Displays all mapped genres for debugging and validation.
     */
    public void printAllMappings() {
        System.out.println("Genre Category Mappings:");
        genreCategoryMap.forEach((genre, category) ->
            System.out.println("Genre: " + genre + " -> Category: " + category)
        );

        System.out.println("\nRelated Genres:");
        relatedGenresMap.forEach((genre, related) ->
            System.out.println("Genre: " + genre + " -> Related: " + related)
        );
    }
}
