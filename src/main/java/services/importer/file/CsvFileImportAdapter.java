package services.importer.file;

import models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.importer.ImportException;
import utils.GenreMapper;

import java.io.*;
import java.nio.file.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of FileImportAdapter for CSV file format.
 * Handles both CSV and TSV files with intelligent format detection.
 * Features:
 * - Automatic header detection and column mapping
 * - Flexible date parsing with multiple format support
 * - Artist and song deduplication
 * - Multiple column name variations for flexible import
 * - Tab-separated value support with auto-detection
 */
public class CsvFileImportAdapter implements FileImportAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(CsvFileImportAdapter.class);
    private static final String[] SUPPORTED_EXTENSIONS = {".csv", ".tsv", ".txt"};
    
    // Patterns for automatic field detection
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{1,4}[/-]\\d{1,2}[/-]\\d{1,4}|\\d{1,2}\\s[A-Za-z]{3,12}\\s\\d{1,4}");
    private static final Pattern SPOTIFY_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9]{22}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://");
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}/\\d{4}|[A-Za-z]+ \\d{1,2}, \\d{4}");
    
    // Special pattern for Spotify timestamp format: "January 2, 2025 at 08:50PM - Song Title"
    private static final Pattern SPOTIFY_TIMESTAMP_SONG_PATTERN = 
            Pattern.compile("([A-Za-z]+ \\d{1,2}, \\d{4} at \\d{1,2}:\\d{2}[AP]M) - (.+)");
    
    // Array of date formats to try when parsing dates
    private static final SimpleDateFormat[] DATE_FORMATS = {
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
        new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US),
        new SimpleDateFormat("MM/dd/yyyy HH:mm:ss", Locale.US),
        new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US),
        new SimpleDateFormat("yyyy-MM-dd", Locale.US),
        new SimpleDateFormat("yyyy/MM/dd", Locale.US),
        new SimpleDateFormat("MM/dd/yyyy", Locale.US),
        new SimpleDateFormat("dd/MM/yyyy", Locale.US),
        new SimpleDateFormat("dd MMM yyyy", Locale.US),
        new SimpleDateFormat("MMM dd, yyyy", Locale.US),
        new SimpleDateFormat("MMMM d, yyyy 'at' hh:mma", Locale.US),
        new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", Locale.US),
        new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
    };
    
    @Override
    public UserMusicData importFromFile(Path filePath) throws ImportException {
        LOGGER.info("Starting CSV import from file: {}", filePath);
        
        UserMusicData userData = new UserMusicData();
        
        try {
            // Read all lines from the input stream first
            List<String> lines = Files.readAllLines(filePath);
            
            if (lines.isEmpty()) {
                throw new ImportException("Empty CSV content");
            }
            
            // Process the first line to determine if it's a header
            String firstLine = lines.get(0);
            String delimiter = detectDelimiter(firstLine);
            
            String[] firstLineValues = parseCsvLine(firstLine, delimiter);
            
            // Check if this is a headerless file
            boolean hasHeader = isHeaderLine(firstLineValues);
            String[] header;
            
            List<String> dataLines;
            if (hasHeader) {
                LOGGER.info("Header detected: {}", String.join(", ", firstLineValues));
                header = firstLineValues;
                dataLines = lines.subList(1, lines.size()); // Skip header for processing
            } else {
                LOGGER.info("No header detected, inferring column names");
                header = inferHeaderFromData(firstLineValues);
                LOGGER.info("Inferred headers: {}", String.join(", ", header));
                dataLines = lines; // Process all lines including the first one
            }
            
            int processedRows = 0;
            int skippedRows = 0;
            int successfulEntries = 0;
            
            // Track unique artists and songs to prevent duplicates
            Map<String, Artist> artistMap = new HashMap<>();
            Map<String, Song> songMap = new HashMap<>();
            
            // Process data lines
            for (String line : dataLines) {
                if (line.trim().isEmpty()) {
                    skippedRows++;
                    continue;
                }
                String[] values = parseCsvLine(line, delimiter);
                boolean processed = processRow(values, header, userData, artistMap, songMap);
                if (processed) {
                    successfulEntries++;
                }
                processedRows++;
                
                // Log progress for large files
                if (processedRows % 1000 == 0) {
                    LOGGER.info("Processed {} rows so far...", processedRows);
                }
            }
            
            // Add all unique artists and songs to the user data
            userData.setArtists(new ArrayList<>(artistMap.values()));
            userData.setSongs(new ArrayList<>(songMap.values()));
            
            LOGGER.info("CSV import completed. Processed: {} rows, Skipped: {} empty rows, " +
                      "Successfully imported: {} entries", 
                      processedRows, skippedRows, successfulEntries);
            
            LOGGER.info("Found {} unique artists, {} unique songs, {} play history entries",
                      artistMap.size(), songMap.size(), userData.getPlayHistory().size());
            
            return userData;
        } catch (IOException e) {
            LOGGER.error("Error reading CSV file: {}", filePath, e);
            throw new ImportException("Error reading CSV file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if the line is likely a header line based on content analysis
     * Uses heuristic detection based on keywords and patterns
     */
    private boolean isHeaderLine(String[] values) {
        if (values.length == 0) return false;
        
        // Common header keywords
        String[] commonHeaders = {"artist", "title", "track", "song", "album", "genre", "date", "timestamp", "id"};
        int headerKeywordMatches = 0;
        int potentialDataFields = 0;
        
        for (String value : values) {
            String lowerValue = value.toLowerCase().trim();
            
            // Check for common header names
            for (String header : commonHeaders) {
                if (lowerValue.contains(header)) {
                    headerKeywordMatches++;
                    break;
                }
            }
            
            // Check for data patterns that likely aren't headers
            if (DATE_PATTERN.matcher(value).find() || 
                SPOTIFY_ID_PATTERN.matcher(value).matches() || 
                URL_PATTERN.matcher(value).find()) {
                potentialDataFields++;
            }
        }
        
        // If we find multiple potential header names and few data patterns, it's likely a header
        return headerKeywordMatches >= 2 || (headerKeywordMatches > 0 && potentialDataFields == 0);
    }
    
    private String detectDelimiter(String line) {
        // Detect if the file is tab-delimited, comma-delimited, or semicolon-delimited
        if (line.contains("\t") && !line.contains(",")) {
            return "\t"; // Tab-delimited
        }
        if (line.contains(",")) {
            return ","; // Comma-delimited
        }
        if (line.contains(";")) {
            return ";"; // Semicolon-delimited (common in European CSV)
        }
        return ","; // Default to comma-delimited
    }
    
    private String[] parseCsvLine(String line, String delimiter) {
        // Handle quoted values with commas inside them
        List<String> tokens = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentToken = new StringBuilder();
        
        for (char c : line.toCharArray()) {
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter.charAt(0) && !inQuotes) {
                tokens.add(currentToken.toString().trim());
                currentToken = new StringBuilder();
            } else {
                currentToken.append(c);
            }
        }
        
        // Add the last token
        tokens.add(currentToken.toString().trim());
        
        // Clean up tokens (remove surrounding quotes)
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("\"") && token.endsWith("\"")) {
                tokens.set(i, token.substring(1, token.length() - 1));
            }
        }
        
        return tokens.toArray(new String[0]);
    }
    
    /**
     * Infer column headers from data when no explicit headers are present
     */
    private String[] inferHeaderFromData(String[] dataLine) {
        String[] inferredHeaders = new String[dataLine.length];
        
        for (int i = 0; i < dataLine.length; i++) {
            String value = dataLine[i].trim();
            
            // Check for various patterns to infer column meaning
            if (i == 0 && TIMESTAMP_PATTERN.matcher(value).find()) {
                inferredHeaders[i] = "timestamp";
            } else if (SPOTIFY_ID_PATTERN.matcher(value).matches()) {
                inferredHeaders[i] = "spotify_id";
            } else if (URL_PATTERN.matcher(value).find()) {
                inferredHeaders[i] = "url";
            } else if (i == 0) {
                inferredHeaders[i] = "title"; // First column typically contains track name
            } else if (i == 1) {
                inferredHeaders[i] = "artist"; // Second column typically contains artist name
            } else if (i == 2) {
                inferredHeaders[i] = "album"; // Third column could be album
            } else {
                // Default column name
                inferredHeaders[i] = "column_" + (i + 1);
            }
        }
        
        return inferredHeaders;
    }
    
    /**
     * Process a single row of CSV data and add it to the UserMusicData object.
     * Handles flexible mapping of columns and data normalization.
     */
    private boolean processRow(String[] values, String[] header, UserMusicData userData, 
                              Map<String, Artist> artistMap, Map<String, Song> songMap) {
        if (values.length < 1) {
            LOGGER.debug("Skipping row with insufficient data (fewer than 1 column)");
            return false; // Skip rows without minimum data
        }
        
        // Map CSV data to object model
        Map<String, String> rowData = new HashMap<>();
        for (int i = 0; i < Math.min(header.length, values.length); i++) {
            String headerKey = header[i].toLowerCase();
            String value = values[i].trim();
            
            // Remove quotes if still present
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            
            rowData.put(headerKey, value);
        }
        
        // Special handling for Spotify format where the timestamp and song are merged
        // Example: "January 2, 2025 at 08:50PM - Song Title"
        String firstColumnValue = values[0].trim();
        Matcher spotifyFormatMatcher = SPOTIFY_TIMESTAMP_SONG_PATTERN.matcher(firstColumnValue);
        
        if (spotifyFormatMatcher.matches()) {
            // We have a combined timestamp and song title
            String timestamp = spotifyFormatMatcher.group(1);
            String songTitle = spotifyFormatMatcher.group(2).trim();
            Date playDate = tryParseDate(timestamp);
            
            // Get artist name from another column or use default
            String artistName = null;
            if (values.length > 1) {
                artistName = values[1].trim();
            }
            
            // If artist name is empty, try to get it from another field
            if (artistName == null || artistName.isEmpty()) {
                artistName = findValueByPossibleKeys(rowData, "artist", "artist_name", "performer", "artistname");
            }
            
            // If still no artist, use default
            if (artistName == null || artistName.isEmpty()) {
                artistName = "Unknown Artist";
            }
            
            return createSongEntry(songTitle, artistName, playDate, userData, artistMap, songMap);
        } 
        
        // Extract title and artist with flexible mapping
        String title = findValueByPossibleKeys(rowData, "title", "track", "track_name", "name", "song", "track title", "column_1");
        String artistName = findValueByPossibleKeys(rowData, "artist", "artist_name", "performer", "artistname", "column_2");
        Date playDate = null;
        
        // Check if title contains a timestamp and song title format
        if (title != null && title.contains(" - ") && isLikelyTimestamp(title.split(" - ")[0])) {
            String[] parts = title.split(" - ", 2);
            playDate = tryParseDate(parts[0]);
            title = parts[1].trim();
        }
        
        // If we still don't have title and artist, try to use the first two columns
        if ((title == null || title.isEmpty()) && values.length >= 1) {
            title = values[0].trim();
            
            // Handle possible timestamp in first column
            if (isLikelyTimestamp(title) && values.length >= 2) {
                playDate = tryParseDate(title);
                title = values[1].trim();
                
                // If there's a third column, use that as artist
                if (values.length >= 3) {
                    artistName = values[2].trim();
                }
            }
        }
        
        // If still no artist name and we have enough columns
        if ((artistName == null || artistName.isEmpty()) && values.length >= 2) {
            artistName = values[1].trim();
        }
        
        // If still no title or artist after all attempts
        if (title == null || title.isEmpty()) {
            LOGGER.debug("Could not find valid song title in row: {}", Arrays.toString(values));
            return false;
        }
        
        if (artistName == null || artistName.isEmpty()) {
            // Default artist name when none is available
            artistName = "Unknown Artist";
        }
        
        // If no play date found yet, try to extract from other fields
        if (playDate == null) {
            playDate = extractPlayDate(rowData, values);
        }
        
        return createSongEntry(title, artistName, playDate, userData, artistMap, songMap);
    }
    
    /**
     * Create a song entry with the given title, artist and play date
     */
    private boolean createSongEntry(String title, String artistName, Date playDate, UserMusicData userData,
                                   Map<String, Artist> artistMap, Map<String, Song> songMap) {
        if (title != null && !title.isEmpty()) {
            // Normalize title and artist name
            String normalizedTitle = normalizeText(title);
            String normalizedArtist = normalizeText(artistName);
            
            LOGGER.debug("Processing song: '{}' by '{}'", normalizedTitle, normalizedArtist);
            
            // Feature detection - prevent "feat." artist names being treated as separate artists
            if (normalizedTitle.toLowerCase().contains("feat.") || normalizedTitle.toLowerCase().contains("featuring")) {
                // Keep the original title, it likely contains featuring information
                LOGGER.debug("Title contains feature info: {}", normalizedTitle);
            }
            
            // Check if the artist field might actually contain a remix or feature
            if (normalizedArtist.toLowerCase().contains("remix") || 
                normalizedArtist.toLowerCase().contains("feat.") ||
                normalizedArtist.toLowerCase().contains("ft.")) {
                // This field might be part of the song title instead
                LOGGER.debug("Artist field '{}' might be part of song title, merging", normalizedArtist);
                normalizedTitle = normalizedTitle + " - " + normalizedArtist;
                normalizedArtist = "Unknown Artist";
            }
            
            // Get or create artist
            String artistKey = normalizedArtist.toLowerCase();
            Artist artist;
            if (artistMap.containsKey(artistKey)) {
                artist = artistMap.get(artistKey);
            } else {
                artist = new Artist(normalizedArtist);
                artist.setId(UUID.randomUUID().toString());
                artistMap.put(artistKey, artist);
            }
            
            // Get or create song - using compound key of artist + title
            String songKey = artistKey + ":" + normalizedTitle.toLowerCase();
            Song song;
            if (songMap.containsKey(songKey)) {
                song = songMap.get(songKey);
            } else {
                song = new Song(normalizedTitle, artist.getName());
                song.setId(UUID.randomUUID().toString());
                song.setArtist(artist);
                songMap.put(songKey, song);
            }
            
            // Create a play history entry
            PlayHistory playEntry = new PlayHistory();
            playEntry.setSong(song);
            playEntry.setTimestamp(playDate != null ? playDate : new Date());
            userData.addPlayHistory(playEntry);
            
            return true;
        } else {
            LOGGER.debug("Skipping row with missing title");
            return false;
        }
    }
    
    private String normalizeText(String input) {
        if (input == null) return "";
        
        // Basic normalization: trim whitespace
        String result = input.trim();
        
        // Remove excessive whitespace
        result = result.replaceAll("\\s+", " ");
        
        // Remove quotes at beginning/end 
        if (result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }
        
        return result;
    }
    
    private boolean isLikelyTimestamp(String value) {
        // Check if string looks like a date/time
        if (value == null) return false;
        
        // Common date patterns
        return value.matches(".*\\d{4}-\\d{2}-\\d{2}.*") || // ISO date
               value.matches(".*\\d{1,2}/\\d{1,2}/\\d{4}.*") || // MM/DD/YYYY
               value.matches(".*[A-Za-z]+ \\d{1,2}, \\d{4}.*") || // Month DD, YYYY
               value.matches(".*\\d{1,2} [A-Za-z]+ \\d{4}.*") || // DD Month YYYY
               value.toLowerCase().contains(" at "); // Contains "at" time marker
    }
    
    private String findValueByPossibleKeys(Map<String, String> data, String... possibleKeys) {
        for (String key : possibleKeys) {
            if (data.containsKey(key) && data.get(key) != null && !data.get(key).trim().isEmpty()) {
                return data.get(key);
            }
        }
        return null;
    }
    
    private Date extractPlayDate(Map<String, String> rowData, String[] values) {
        // First try common date column names
        String[] dateKeys = {"timestamp", "date", "played_at", "played", "time", "end_time", "listened_at"};
        for (String key : dateKeys) {
            String dateStr = rowData.get(key);
            if (dateStr != null && !dateStr.isEmpty()) {
                Date date = tryParseDate(dateStr);
                if (date != null) return date;
            }
        }
        
        // If no date found in expected columns, scan all columns for date-like values
        for (String value : values) {
            if (value != null && !value.isEmpty() && isLikelyTimestamp(value)) {
                Date date = tryParseDate(value);
                if (date != null) return date;
            }
        }
        
        // If no date was found, check for Spotify format "Month D, YYYY at HH:MMA" anywhere in the values
        Pattern spotifyFormat = Pattern.compile("([A-Za-z]+ \\d{1,2}, \\d{4} at \\d{1,2}:\\d{2}[AP]M)");
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                Matcher matcher = spotifyFormat.matcher(value);
                if (matcher.find()) {
                    Date date = tryParseDate(matcher.group(1));
                    if (date != null) return date;
                }
            }
        }
        
        return null;
    }
    
    private Date tryParseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        
        // Clean up the string
        dateStr = dateStr.trim();
        if (dateStr.startsWith("\"") && dateStr.endsWith("\"")) {
            dateStr = dateStr.substring(1, dateStr.length() - 1);
        }
        
        // Try each of the date formats
        for (SimpleDateFormat format : DATE_FORMATS) {
            try {
                format.setLenient(true);
                return format.parse(dateStr);
            } catch (ParseException e) {
                // Try the next format
            }
        }
        
        // Try to parse as timestamp (milliseconds since epoch)
        try {
            long timestamp = Long.parseLong(dateStr);
            return new Date(timestamp);
        } catch (NumberFormatException e) {
            // Not a number
        }
        
        // Try additional cleanup and retry
        // Remove common non-date characters that might interfere with parsing
        dateStr = dateStr.replaceAll("[^0-9A-Za-z:\\s/-]", " ").trim();
        for (SimpleDateFormat format : DATE_FORMATS) {
            try {
                format.setLenient(true);
                return format.parse(dateStr);
            } catch (ParseException e) {
                // Try the next format
            }
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
