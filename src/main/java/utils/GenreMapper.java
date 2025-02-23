package utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class GenreMapper {
    private static final Map<String, String> GENRE_MAPPING = new HashMap<>();
    
    static {
        // Main genres and their variants
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
            
        // Regional variants
        addGenreVariants("regional", "american", "british", "japanese", "icelandic", "australian", "mexican",
            "italian", "michigan", "utah", "arizona", "ohio", "texas", "sheffield", "provo");
            
        // Special categories
        addGenreVariants("bogus", "bogus artist", "bogus", "mergeme", "spamess", "fixme", "cleanup",
            "special purpose", "special purpose artist", "meta artist");
    }
    
    private static void addGenreVariants(String mainGenre, String... variants) {
        for (String variant : variants) {
            GENRE_MAPPING.put(variant.toLowerCase(), mainGenre);
        }
    }
    
    public static String normalizeGenre(String genre) {
        if (genre == null) return null;
        String lowercaseGenre = genre.toLowerCase().trim();
        return GENRE_MAPPING.getOrDefault(lowercaseGenre, lowercaseGenre);
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
} 