package utils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GenreMapper {

    // Enhancement 1: Use ConcurrentHashMap for thread-safety.
    private static final Map<String, String> GENRE_MAPPING = new ConcurrentHashMap<>();

    // Enhancement 2: Corrections for synonyms/misspellings.
    private static final Map<String, String> CORRECTIONS = Map.of(
        "hiphop", "hip-hop",
        "rnb", "r&b",
        "eletronic", "electronic",
        "lofi", "lo-fi",
        "popmusic", "pop"
    );

    // Enhancement 6: LRU Cache for frequently used genre lookups.
    private static final Map<String, String> CACHE = new LinkedHashMap<>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 100;
        }
    };

    static {
        // Main genres and their variants.
        addGenreVariants("hip-hop", "hip hop", "hiphop", "rap", "trap", "gangsta rap", "conscious hip hop", 
            "underground hip hop", "old school hip hop", "southern hip hop", "west coast rap", "east coast rap",
            "alternative rap", "alternative hip hop", "pop rap", "rap rock", "cloud rap", "progressive rap",
            "political hip hop", "hardcore hip hop", "hip-hop/rap");

        addGenreVariants("electronic", "electronic", "electronica", "electro", "synthwave", "chillwave", 
            "ambient electronic", "glitch", "idm", "experimental electronic", "indietronica", 
            "alternative electronic", "digital fusion");

        addGenreVariants("rock", "rock & roll", "rock and roll", "rock'n'roll", "hard rock", "classic rock", 
            "progressive rock", "psychedelic rock", "garage rock", "art rock", "southern rock", "post-rock",
            "piano rock", "pop rock", "surf rock", "comedy rock", "indie folk rock", "noise rock", "rock opera");

        addGenreVariants("pop", "pop music", "popular", "synth pop", "dream pop", "chamber pop", "baroque pop", 
            "power pop", "sophisti-pop", "electropop", "europop", "k-pop", "j-pop", "traditional pop",
            "powerpop", "psychedelic pop", "city pop", "folk pop", "jazz pop", "pop soul", "twee pop");

        addGenreVariants("indie", "indie", "indie pop", "indie rock", "indie folk", "indie electronic", 
            "bedroom pop", "lo-fi indie", "indie punk", "indie garage rock", "hopebeat");

        addGenreVariants("alternative", "alternative", "alternative rock", "alternative pop", "alternative metal", 
            "alternative country", "alt-folk", "alt-indie", "grunge", "alt z", "alternative r&b", "alternative folk");

        addGenreVariants("singer-songwriter", "singer-songwriter", "acoustic", "female vocalists");

        addGenreVariants("soundtrack", "soundtrack", "film soundtrack", "videogame soundtrack", "video game music",
            "indie game soundtrack", "film composer", "composer", "instrumental", "16-bit", "chiptune", "otacore");

        addGenreVariants("japanese", "japanese", "j-pop", "shibuya-kei", "anime");

        addGenreVariants("folk", "folk", "folk rock", "contemporary folk", "traditional folk", "american folk", 
            "british folk", "celtic folk", "nordic folk", "folk baroque", "pagan folk", "dark folk");

        addGenreVariants("jazz", "jazz", "smooth jazz", "fusion jazz", "bebop", "swing", "cool jazz", "modal jazz", 
            "free jazz", "latin jazz", "jazz funk", "jazz rap", "acid jazz", "nu jazz", "vocal jazz");

        addGenreVariants("ambient", "ambient", "dark ambient", "space ambient", "drone", "ambient dub", 
            "ambient techno", "ambient pop", "downtempo", "chillout", "lounge");

        addGenreVariants("lo-fi", "lo-fi", "lo-fi hip hop", "lo-fi beats", "chillhop", "jazzhop", "lo-fi indie",
            "instrumental hip hop", "boom bap");

        addGenreVariants("r&b", "r&b", "contemporary r&b", "urban contemporary", "rnb", "soul", "motown",
            "neo soul", "pop soul");

        // Regional variants.
        addGenreVariants("regional", "american", "british", "japanese", "icelandic", "australian", "mexican",
            "italian", "michigan", "utah", "arizona", "ohio", "texas", "sheffield", "provo");

        // Special categories.
        addGenreVariants("bogus", "bogus artist", "bogus", "mergeme", "spamess", "fixme", "cleanup",
            "special purpose", "special purpose artist", "meta artist");
    }

    private static void addGenreVariants(String mainGenre, String... variants) {
        for (String variant : variants) {
            GENRE_MAPPING.put(variant.toLowerCase(), mainGenre);
        }
    }

    // Enhancement 2, 3 & 6: Modified normalizeGenre: apply corrections, check cache, and suggest a genre if not found.
    public static String normalizeGenre(String genre) {
        if (genre == null) return null;
        String lowercaseGenre = genre.toLowerCase().trim();

        // Enhancement 6: Look up in cache first.
        if (CACHE.containsKey(lowercaseGenre)) {
            return CACHE.get(lowercaseGenre);
        }

        // Enhancement 2: Fix misspellings using the corrections map.
        if (CORRECTIONS.containsKey(lowercaseGenre)) {
            lowercaseGenre = CORRECTIONS.get(lowercaseGenre);
        }

        String normalized;
        if (GENRE_MAPPING.containsKey(lowercaseGenre)) {
            normalized = GENRE_MAPPING.get(lowercaseGenre);
        } else {
            // Enhancement 3: Suggest the closest match when genre is not found.
            normalized = findClosestMatch(lowercaseGenre);
        }

        CACHE.put(lowercaseGenre, normalized);
        return normalized;
    }

    private static String findClosestMatch(String input) {
        int minDistance = Integer.MAX_VALUE;
        String closestMatch = input;
        for (String key : GENRE_MAPPING.keySet()) {
            int distance = levenshteinDistance(input, key);
            if (distance < minDistance) {
                minDistance = distance;
                closestMatch = key;
            }
        }
        // Return the main genre corresponding to the closest match.
        return GENRE_MAPPING.getOrDefault(closestMatch, closestMatch);
    }

    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1),
                            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1));
                }
            }
        }
        return dp[a.length()][b.length()];
    }

    public static Set<String> normalizeGenres(Set<String> genres) {
        Set<String> normalized = new HashSet<>();
        for (String genre : genres) {
            String normalizedGenre = normalizeGenre(genre);
            if (normalizedGenre != null && !normalizedGenre.equals("bogus")) {
                normalized.add(normalizedGenre);
            }
        }
        return normalized;
    }

    public static Set<String> getMainGenres() {
        return new HashSet<>(GENRE_MAPPING.values());
    }

    public static boolean isKnownGenre(String genre) {
        if (genre == null) return false;
        String lowercaseGenre = genre.toLowerCase().trim();
        return GENRE_MAPPING.containsKey(lowercaseGenre) || GENRE_MAPPING.containsValue(lowercaseGenre);
    }
    
    // Enhancement 5: Load genre mappings from database.
    public static void loadGenresFromDatabase(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT variant, main_genre FROM genre_mapping");
        while (rs.next()) {
            String variant = rs.getString("variant").toLowerCase().trim();
            String mainGenre = rs.getString("main_genre");
            GENRE_MAPPING.put(variant, mainGenre);
        }
        rs.close();
        stmt.close();
    }
}