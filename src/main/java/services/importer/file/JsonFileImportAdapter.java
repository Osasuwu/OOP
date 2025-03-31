package services.importer.file;

import models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.importer.ImportException;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.text.*;
import java.util.regex.Pattern;

/**
 * Implementation of FileImportAdapter for JSON file format.
 */
public class JsonFileImportAdapter implements FileImportAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonFileImportAdapter.class);
    private static final String[] SUPPORTED_EXTENSIONS = {".json"};
    
    // Patterns for field name matching
    private static final Pattern TITLE_KEYS_PATTERN = Pattern.compile("(?i)title|track(_name)?|song(_name)?|name");
    private static final Pattern ARTIST_KEYS_PATTERN = Pattern.compile("(?i)artist(_name)?|performer");
    private static final Pattern ALBUM_KEYS_PATTERN = Pattern.compile("(?i)album(_name)?");
    private static final Pattern GENRE_KEYS_PATTERN = Pattern.compile("(?i)genre");
    private static final Pattern ID_KEYS_PATTERN = Pattern.compile("(?i)id|spotify(_id)?");
    private static final Pattern DATE_KEYS_PATTERN = Pattern.compile("(?i)date|timestamp|played(_at)?|time");
    
    // Date formats to try when parsing
    private static final SimpleDateFormat[] DATE_FORMATS = {
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"),
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"),
        new SimpleDateFormat("yyyy/MM/dd HH:mm:ss"),
        new SimpleDateFormat("MM/dd/yyyy HH:mm:ss"),
        new SimpleDateFormat("MMMM d, yyyy 'at' hh:mma", Locale.US)
    };
    
    @Override
    public UserMusicData importFromFile(Path filePath) throws ImportException {
        LOGGER.info("Starting JSON import from file: {}", filePath);
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            
            String jsonContent = content.toString().trim();
            if (jsonContent.isEmpty()) {
                throw new ImportException("Empty JSON content");
            }
            
            // Basic structure validation
            if (!jsonContent.startsWith("{") && !jsonContent.startsWith("[")) {
                throw new ImportException("Invalid JSON format, must start with '{' or '['");
            }
            
            // Create user data object
            UserMusicData userData = new UserMusicData();
            
            // Parse JSON content (simplified version)
            int processedCount = parseJsonContent(jsonContent, userData);
            
            LOGGER.info("JSON import completed. Processed {} objects", processedCount);
            LOGGER.info("Imported {} songs, {} artists, {} play history entries",
                      userData.getSongs().size(), userData.getArtists().size(), 
                      userData.getPlayHistory().size());
            
            return userData;
        } catch (IOException e) {
            LOGGER.error("Error reading JSON file: {}", filePath, e);
            throw new ImportException("Error reading JSON file: " + e.getMessage(), e);
        }
    }
    
    private int parseJsonContent(String jsonContent, UserMusicData userData) throws ImportException {
        // Simplified JSON parsing logic - in a real implementation would use a proper JSON library
        int processedCount = 0;
        
        try {
            boolean isArray = jsonContent.trim().startsWith("[");
            
            if (isArray) {
                // Process JSON array format
                String[] items = extractJsonArrayItems(jsonContent);
                for (String item : items) {
                    if (processJsonObject(item, userData)) {
                        processedCount++;
                    }
                }
            } else {
                // Process single JSON object
                if (processJsonObject(jsonContent, userData)) {
                    processedCount = 1;
                }
            }
            
            return processedCount;
        } catch (Exception e) {
            throw new ImportException("Failed to parse JSON content: " + e.getMessage(), e);
        }
    }
    
    private String[] extractJsonArrayItems(String jsonArray) {
        // Very simplified JSON array parsing
        // Remove the opening/closing brackets
        String content = jsonArray.trim().substring(1, jsonArray.length() - 1);
        List<String> items = new ArrayList<>();
        
        StringBuilder currentItem = new StringBuilder();
        int bracketsNestLevel = 0;
        
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            
            if (c == '{') {
                bracketsNestLevel++;
                currentItem.append(c);
            } else if (c == '}') {
                bracketsNestLevel--;
                currentItem.append(c);
                
                if (bracketsNestLevel == 0) {
                    items.add(currentItem.toString());
                    currentItem = new StringBuilder();
                    
                    // Skip the comma and whitespace after an object
                    while (i + 1 < content.length() && (content.charAt(i + 1) == ',' || Character.isWhitespace(content.charAt(i + 1)))) {
                        i++;
                    }
                }
            } else if (bracketsNestLevel > 0) {
                currentItem.append(c);
            }
        }
        
        return items.toArray(new String[0]);
    }
    
    private boolean processJsonObject(String jsonObject, UserMusicData userData) {
        // Very simplified JSON object parsing
        try {
            Map<String, String> fields = extractFields(jsonObject);
            
            if (fields.isEmpty()) {
                return false;
            }
            
            // Look for title
            String title = null;
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (TITLE_KEYS_PATTERN.matcher(entry.getKey()).matches()) {
                    title = entry.getValue();
                    break;
                }
            }
            
            // Look for artist
            String artistName = null;
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (ARTIST_KEYS_PATTERN.matcher(entry.getKey()).matches()) {
                    artistName = entry.getValue();
                    break;
                }
            }
            
            // Skip if essential data is missing
            if (title == null || artistName == null) {
                LOGGER.warn("Skipping JSON object missing title or artist");
                return false;
            }
            
            // Create artist
            Artist artist = userData.findOrCreateArtist(artistName);
            
            // Create song
            Song song = new Song(title, artistName);
            song.setArtist(artist);
            
            // Look for album
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (ALBUM_KEYS_PATTERN.matcher(entry.getKey()).matches()) {
                    song.setAlbumName(entry.getValue());
                    break;
                }
            }
            
            // Look for genre
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (GENRE_KEYS_PATTERN.matcher(entry.getKey()).matches()) {
                    List<String> genres = new ArrayList<>();
                    genres.add(entry.getValue());
                    song.setGenres(genres);
                    break;
                }
            }
            
            // Add song to user data
            userData.addSong(song);
            
            // Look for date
            Date playDate = null;
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (DATE_KEYS_PATTERN.matcher(entry.getKey()).matches()) {
                    playDate = parseDate(entry.getValue());
                    if (playDate != null) {
                        break;
                    }
                }
            }
            
            // Create play history if date exists
            if (playDate != null) {
                PlayHistory playHistory = new PlayHistory();
                playHistory.setSong(song);
                playHistory.setTimestamp(playDate);
                userData.addPlayHistory(playHistory);
            }
            
            return true;
        } catch (Exception e) {
            LOGGER.warn("Error processing JSON object: {}", e.getMessage());
            return false;
        }
    }
    
    private Map<String, String> extractFields(String jsonObject) {
        Map<String, String> fields = new HashMap<>();
        
        // Remove curly braces
        jsonObject = jsonObject.trim();
        if (jsonObject.startsWith("{")) {
            jsonObject = jsonObject.substring(1);
        }
        if (jsonObject.endsWith("}")) {
            jsonObject = jsonObject.substring(0, jsonObject.length() - 1);
        }
        
        // Split into key-value pairs
        boolean inQuotes = false;
        StringBuilder currentPair = new StringBuilder();
        
        for (int i = 0; i < jsonObject.length(); i++) {
            char c = jsonObject.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
                currentPair.append(c);
            } else if (c == ',' && !inQuotes) {
                processKeyValuePair(currentPair.toString().trim(), fields);
                currentPair = new StringBuilder();
            } else {
                currentPair.append(c);
            }
        }
        
        // Process the last pair
        if (currentPair.length() > 0) {
            processKeyValuePair(currentPair.toString().trim(), fields);
        }
        
        return fields;
    }
    
    private void processKeyValuePair(String pair, Map<String, String> fields) {
        int colonIndex = findUnquotedColon(pair);
        if (colonIndex != -1) {
            String key = pair.substring(0, colonIndex).trim();
            String value = pair.substring(colonIndex + 1).trim();
            
            // Remove quotes from key
            if (key.startsWith("\"") && key.endsWith("\"")) {
                key = key.substring(1, key.length() - 1);
            }
            
            // Remove quotes from value
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            
            fields.put(key, value);
        }
    }
    
    private int findUnquotedColon(String text) {
        boolean inQuotes = false;
        
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ':' && !inQuotes) {
                return i;
            }
        }
        
        return -1;
    }
    
    private Date parseDate(String dateStr) {
        for (SimpleDateFormat format : DATE_FORMATS) {
            try {
                return format.parse(dateStr);
            } catch (ParseException e) {
                // Try the next format
            }
        }
        
        try {
            // Try parsing as milliseconds
            return new Date(Long.parseLong(dateStr));
        } catch (NumberFormatException e) {
            // Not a number
        }
        
        return null;
    }
    
    @Override
    public boolean canHandle(Path filePath) {
        if (filePath == null) return false;
        
        String fileName = filePath.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
