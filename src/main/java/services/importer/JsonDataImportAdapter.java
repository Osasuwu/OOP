package services.importer;

import models.UserMusicData;
import models.Song;
import models.Artist;
import models.PlayHistory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of DataImportAdapter for JSON file format.
 * Provides flexible JSON import with the following features:
 * - Support for both array and object JSON structures
 * - Intelligent field mapping based on common naming patterns
 * - Date parsing with multiple format support
 * - Flexible artist and song extraction
 * - Metadata handling for various streaming platforms
 */
public class JsonDataImportAdapter implements DataImportAdapter {
    private static final Logger logger = LoggerFactory.getLogger(JsonDataImportAdapter.class);
    private static final String[] SUPPORTED_EXTENSIONS = {".json"};
    
    // Patterns for intelligent field detection
    private static final Pattern TITLE_KEYS_PATTERN = Pattern.compile("(?i)^(title|track|song|name)$");
    private static final Pattern ARTIST_KEYS_PATTERN = Pattern.compile("(?i)^(artist|performer|artist_?name)$");
    private static final Pattern ALBUM_KEYS_PATTERN = Pattern.compile("(?i)^(album|record|collection)$");
    private static final Pattern GENRE_KEYS_PATTERN = Pattern.compile("(?i)^(genre|category|style|tag)$");
    private static final Pattern DATE_KEYS_PATTERN = Pattern.compile("(?i)^(date|time|timestamp|played_?at|listen_?time)$");
    private static final Pattern ID_KEYS_PATTERN = Pattern.compile("(?i)^(id|track_?id|spotify_?id)$");
    
    // Date formats to try when parsing timestamps
    private final SimpleDateFormat[] dateFormats = {
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
        new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"),
        new SimpleDateFormat("MM/dd/yyyy HH:mm:ss"),
        new SimpleDateFormat("MMMM d, yyyy 'at' hh:mma", Locale.US)
    };
    
    /**
     * Constructs a new JSON adapter with default settings
     */
    public JsonDataImportAdapter() {
        logger.debug("Initializing JSON data import adapter");
        for (SimpleDateFormat format : dateFormats) {
            format.setLenient(true);
        }
    }
    
    @Override
    public UserMusicData importFromFile(Path filePath) throws ImportException {
        logger.info("Starting JSON import from file: {}", filePath);
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            return importFromStream(inputStream, filePath.getFileName().toString());
        } catch (IOException e) {
            logger.error("Failed to read JSON file: {}", filePath, e);
            throw new ImportException("Failed to read JSON file: " + e.getMessage(), e);
        }
    }

    @Override
    public UserMusicData importFromStream(InputStream inputStream, String sourceName) throws ImportException {
        logger.info("Starting JSON import from stream: {}", sourceName);
        UserMusicData userData = new UserMusicData();
        
        try {
            // Read JSON content as string
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            
            String jsonContent = content.toString().trim();
            if (jsonContent.isEmpty()) {
                logger.warn("Empty JSON content in: {}", sourceName);
                throw new ImportException("Empty JSON content");
            }
            
            // Basic structure validation
            if (!jsonContent.startsWith("{") && !jsonContent.startsWith("[")) {
                logger.error("Invalid JSON format, must start with '{{' or '[': {}", sourceName);
                throw new ImportException("Invalid JSON format, must start with '{' or '['");
            }
            
            // Parse the JSON content with intelligence
            int entryCount = parseJsonContent(jsonContent, userData);
            
            logger.info("JSON import completed. Processed {} objects from {}", entryCount, sourceName);
            logger.info("Imported {} songs, {} artists, and {} play history entries",
                      userData.getSongs().size(), userData.getArtists().size(), 
                      userData.getPlayHistory().size());
            
            return userData;
        } catch (IOException e) {
            logger.error("Error reading JSON data: {}", sourceName, e);
            throw new ImportException("Error reading JSON data: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean canHandle(Path filePath) {
        String fileName = filePath.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Parse JSON content and extract music data.
     * Handles both array of objects and single object structures.
     * 
     * @param jsonContent The raw JSON content as a string
     * @param userData The UserMusicData object to populate
     * @return The number of JSON objects processed
     */
    private int parseJsonContent(String jsonContent, UserMusicData userData) {
        logger.debug("Starting JSON content parsing, content size: {} bytes", jsonContent.length());
        
        // This is a simplified parser for demonstration.
        // In a real implementation, use a JSON library like Jackson or Gson
        
        // Basic sanity check - make sure it looks like JSON
        if (!jsonContent.trim().startsWith("{") && !jsonContent.trim().startsWith("[")) {
            logger.warn("Content doesn't look like JSON, skipping");
            return 0;
        }
        
        // Simple heuristic to detect if this is an array of objects
        // or a single object structure
        boolean isArray = jsonContent.trim().startsWith("[");
        int processedCount = 0;
        
        if (isArray) {
            logger.debug("Detected JSON array structure");
            // Extremely simplified array parsing - this is just for demonstration
            // In reality, you would use a real JSON parser
            String[] jsonObjects = jsonContent.trim()
                .replace("[", "")
                .replace("]", "")
                .split("\\},\\s*\\{");
                
            for (String jsonObj : jsonObjects) {
                // Ensure each object has braces
                if (!jsonObj.trim().startsWith("{")) jsonObj = "{" + jsonObj;
                if (!jsonObj.trim().endsWith("}")) jsonObj = jsonObj + "}";
                
                logger.debug("Processing JSON array element {}/{}", processedCount+1, jsonObjects.length);
                if (processJsonObject(jsonObj, userData)) {
                    processedCount++;
                }
            }
        } else {
            logger.debug("Detected single JSON object structure");
            // Single object processing
            if (processJsonObject(jsonContent, userData)) {
                processedCount = 1;
            }
        }
        
        logger.info("JSON parsing complete: processed {} objects", processedCount);
        return processedCount;
    }
    
    /**
     * Process a single JSON object and extract music data
     * 
     * @param jsonObject The JSON object string
     * @param userData The UserMusicData object to populate
     * @return true if the object was successfully processed
     */
    private boolean processJsonObject(String jsonObject, UserMusicData userData) {
        try {
            // Extract fields from JSON object
            Map<String, String> fields = extractJsonFields(jsonObject);
            
            if (fields.isEmpty()) {
                logger.warn("Failed to extract any fields from JSON object");
                return false;
            }
            
            logger.debug("Extracted {} fields from JSON object", fields.size());
            
            // Look for title/track name
            String title = extractByPattern(fields, TITLE_KEYS_PATTERN);
            
            // Look for artist
            String artistName = extractByPattern(fields, ARTIST_KEYS_PATTERN);
            
            // If missing basic data, try to find in nested objects or arrays
            if ((title == null || artistName == null) && 
                (jsonObject.contains("{") || jsonObject.contains("["))) {
                logger.debug("Missing basic data, checking for nested objects");
                // Complex nested object handling would go here
                // For this simple implementation, we'll skip complex objects
            }
            
            // Skip if missing essential data
            if (title == null || artistName == null) {
                logger.warn("Skipping JSON object missing title or artist");
                return false;
            }
            
            // Create and add artist
            Artist artist = userData.findOrCreateArtist(artistName);
            
            // Create song
            Song song = new Song(title, artistName);
            song.setArtist(artist);
            
            // Extract album if available
            String album = extractByPattern(fields, ALBUM_KEYS_PATTERN);
            if (album != null) {
                song.setAlbum(album);
                logger.debug("Added album: '{}'", album);
            }
            
            // Extract genre if available
            String genre = extractByPattern(fields, GENRE_KEYS_PATTERN);
            if (genre != null) {
                List<String> genres = new ArrayList<>();
                genres.add(genre);
                song.setGenres(genres);
                logger.debug("Added genre: '{}'", genre);
            }
            
            // Extract ID if available
            String id = extractByPattern(fields, ID_KEYS_PATTERN);
            if (id != null) {
                song.setSpotifyId(id);
                logger.debug("Added ID: '{}'", id);
            }
            
            // Add song to user data
            userData.addSong(song);
            
            // Look for timestamp/play date
            String dateStr = extractByPattern(fields, DATE_KEYS_PATTERN);
            if (dateStr != null) {
                Date playDate = parseDate(dateStr);
                if (playDate != null) {
                    PlayHistory playEntry = new PlayHistory();
                    playEntry.setSong(song);
                    playEntry.setTimestamp(playDate);
                    userData.addPlayHistory(playEntry);
                    logger.debug("Added play history for song '{}' at '{}'", title, playDate);
                }
            }
            
            logger.debug("Successfully processed song: '{}' by '{}'", title, artistName);
            return true;
        } catch (Exception e) {
            logger.error("Error processing JSON object: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Extract a field value from the field map based on a regex pattern
     * 
     * @param fields Map of field names to values
     * @param pattern Pattern to match against field names
     * @return The value of the first matching field, or null if none match
     */
    private String extractByPattern(Map<String, String> fields, Pattern pattern) {
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (pattern.matcher(entry.getKey()).matches()) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    /**
     * Try to parse a date string using multiple formats
     * 
     * @param dateStr The date string to parse
     * @return The parsed Date or null if parsing fails
     */
    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        // Clean up the date string
        dateStr = dateStr.replace("\"", "").trim();
        
        for (SimpleDateFormat format : dateFormats) {
            try {
                return format.parse(dateStr);
            } catch (ParseException e) {
                // Try next format
            }
        }
        
        logger.warn("Failed to parse date with any format: '{}'", dateStr);
        return null;
    }
    
    /**
     * Extract fields from a JSON object string into a map
     * This is a simplified JSON parser - in a real implementation,
     * use a proper JSON parsing library
     * 
     * @param jsonObject The JSON object string
     * @return Map of field names to values
     */
    private Map<String, String> extractJsonFields(String jsonObject) {
        Map<String, String> fields = new HashMap<>();
        
        try {
            // Strip outer braces
            String content = jsonObject.trim();
            if (content.startsWith("{")) content = content.substring(1);
            if (content.endsWith("}")) content = content.substring(0, content.length() - 1);
            
            // Split by commas, but not commas inside quotes
            boolean inQuotes = false;
            StringBuilder currentPair = new StringBuilder();
            
            for (char c : content.toCharArray()) {
                if (c == '"') {
                    inQuotes = !inQuotes;
                    currentPair.append(c);
                } else if (c == ',' && !inQuotes) {
                    processKeyValuePair(currentPair.toString(), fields);
                    currentPair = new StringBuilder();
                } else {
                    currentPair.append(c);
                }
            }
            
            // Process the last pair
            if (currentPair.length() > 0) {
                processKeyValuePair(currentPair.toString(), fields);
            }
            
        } catch (Exception e) {
            logger.error("Error extracting JSON fields: {}", e.getMessage(), e);
        }
        
        return fields;
    }
    
    /**
     * Process a key-value pair string from JSON and add to the fields map
     * 
     * @param pair The key-value pair string (e.g., "key": "value")
     * @param fields The map to add the key-value pair to
     */
    private void processKeyValuePair(String pair, Map<String, String> fields) {
        pair = pair.trim();
        if (pair.isEmpty()) return;
        
        // Find the colon that separates key and value
        int colonPos = findKeyValueSeparator(pair);
        if (colonPos == -1) return;
        
        // Extract key and value
        String key = pair.substring(0, colonPos).trim();
        String value = pair.substring(colonPos + 1).trim();
        
        // Clean up key (remove quotes)
        if (key.startsWith("\"") && key.endsWith("\"")) {
            key = key.substring(1, key.length() - 1);
        }
        
        // Clean up value (remove quotes)
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        
        // Add to fields map
        fields.put(key, value);
    }
    
    /**
     * Find the position of the colon that separates key and value in a JSON pair
     * Accounts for colons inside quoted strings
     * 
     * @param pair The key-value pair string
     * @return The position of the separator colon, or -1 if not found
     */
    private int findKeyValueSeparator(String pair) {
        boolean inQuotes = false;
        for (int i = 0; i < pair.length(); i++) {
            char c = pair.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ':' && !inQuotes) {
                return i;
            }
        }
        return -1;
    }
}
