package services.datasources;

import interfaces.MusicDataSource;
import models.UserMusicData;
import models.Song;
import models.Artist;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

public class CsvHistorySource implements MusicDataSource {
    private final Path csvPath;
    private String format;
    private static final String SPOTIFY = "spotify";
    private static final String APPLE_MUSIC = "apple_music";
    private static final String YANDEX_MUSIC = "yandex_music";
    private static final String GENERIC = "generic";
    
    // Common field names for different platforms
    private static final Map<String, List<String>> FIELD_MAPPINGS = new HashMap<>();
    static {
        // Track name mappings
        FIELD_MAPPINGS.put("trackName", Arrays.asList(
            "track name", "track", "title", "name", "song", "track_name", "trackname"
        ));
        
        // Artist mappings
        FIELD_MAPPINGS.put("artist", Arrays.asList(
            "artist", "artist name", "artistname", "artist_name", "performer"
        ));
        
        // Album mappings
        FIELD_MAPPINGS.put("album", Arrays.asList(
            "album", "album name", "albumname", "album_name", "collection"
        ));
        
        // Timestamp mappings
        FIELD_MAPPINGS.put("timestamp", Arrays.asList(
            "timestamp", "time", "date", "played at", "end time", "endtime", "play_date", 
            "datetime", "date_time", "time_played"
        ));
        
        // Duration mappings
        FIELD_MAPPINGS.put("duration", Arrays.asList(
            "duration", "duration_ms", "length", "ms_played", "played_ms", "time_played"
        ));
    }

    public CsvHistorySource(String path, String format) {
        this.csvPath = Path.of(path);
        this.format = format;
    }

    @Override
    public UserMusicData loadMusicData() {
        UserMusicData userData = new UserMusicData();
        
        if (!isSourceAvailable()) {
            System.err.println("CSV file is not available: " + csvPath);
            return userData;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath.toFile(), StandardCharsets.UTF_8))) {
            // Read header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                System.err.println("CSV file is empty");
                return userData;
            }
            
            // Auto-detect format if not specified
            if (format == null || format.isEmpty() || format.equalsIgnoreCase("auto")) {
                format = detectFormat(headerLine);
                System.out.println("Detected format: " + format);
            }
            
            // Parse the CSV headers and determine column indices
            String[] headers = parseCSVLine(headerLine);
            Map<String, Integer> columnIndices = mapColumnIndices(headers);
            
            if (!columnIndices.containsKey("trackName") || !columnIndices.containsKey("artist")) {
                System.err.println("Required columns not found in CSV. Need at least track name and artist");
                return userData;
            }
            
            // Process data rows
            String line;
            Map<String, Integer> playCount = new HashMap<>();
            Map<String, Artist> artists = new HashMap<>();
            Map<String, Song> songs = new HashMap<>();
            
            while ((line = reader.readLine()) != null) {
                String[] values = parseCSVLine(line);
                
                // Skip if we don't have enough data
                if (values.length <= Math.max(columnIndices.get("trackName"), columnIndices.get("artist"))) {
                    continue;
                }
                
                String trackName = values[columnIndices.get("trackName")].trim();
                String artistName = values[columnIndices.get("artist")].trim();
                
                if (trackName.isEmpty() || artistName.isEmpty()) {
                    continue;
                }
                
                // Create unique key for song
                String songKey = trackName + " - " + artistName;
                
                // Update play count
                playCount.put(songKey, playCount.getOrDefault(songKey, 0) + 1);
                
                // Get or create artist
                Artist artist = artists.computeIfAbsent(artistName, Artist::new);
                
                // Get or create song
                Song song = songs.computeIfAbsent(songKey, k -> {
                    Song s = new Song(trackName, artistName);
                    
                    // Add optional fields if available
                    if (columnIndices.containsKey("album") && values.length > columnIndices.get("album")) {
                        s.setAlbum(values[columnIndices.get("album")]);
                    }
                    
                    if (columnIndices.containsKey("duration") && values.length > columnIndices.get("duration")) {
                        try {
                            String durationStr = values[columnIndices.get("duration")];
                            long durationMs = parseDuration(durationStr, format);
                            s.setDurationMs(durationMs);
                        } catch (NumberFormatException e) {
                            // Skip duration if not parseable
                        }
                    }
                    
                    return s;
                });
                
                // Extract timestamp if available
                if (columnIndices.containsKey("timestamp") && values.length > columnIndices.get("timestamp")) {
                    String timestamp = values[columnIndices.get("timestamp")];
                    LocalDateTime dateTime = parseTimestamp(timestamp, format);
                    if (dateTime != null) {
                        // Could store this in a listening history object if needed
                    }
                }
            }
            
            // Set the data in UserMusicData
            userData.setSongs(new ArrayList<>(songs.values()));
            userData.setArtists(new ArrayList<>(artists.values()));
            
            // Set play counts for songs
            for (Song song : userData.getSongs()) {
                String songKey = song.getTitle() + " - " + song.getArtistName();
                song.setPlayCount(playCount.getOrDefault(songKey, 0));
            }
            
            System.out.println("Successfully loaded " + userData.getSongs().size() + " songs from CSV history");
            
        } catch (IOException e) {
            System.err.println("Error reading CSV file: " + e.getMessage());
        }
        
        return userData;
    }
    
    @Override
    public boolean isSourceAvailable() {
        return csvPath != null && csvPath.toFile().exists() && csvPath.toFile().isFile();
    }
    
    private String detectFormat(String headerLine) {
        headerLine = headerLine.toLowerCase();
        
        if (headerLine.contains("spotify") || 
            (headerLine.contains("track") && headerLine.contains("artist") && headerLine.contains("ms_played"))) {
            return SPOTIFY;
        } else if (headerLine.contains("apple") || 
                  (headerLine.contains("artist name") && headerLine.contains("title") && headerLine.contains("end time"))) {
            return APPLE_MUSIC;
        } else if (headerLine.contains("yandex") || headerLine.contains("яндекс") || 
                  (headerLine.contains("artist") && headerLine.contains("title") && headerLine.contains("duration_ms"))) {
            return YANDEX_MUSIC;
        } else {
            return GENERIC;
        }
    }
    
    private String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (i < line.length() - 1 && line.charAt(i + 1) == '"') {
                    // Handle escaped quote
                    field.append('"');
                    i++; // Skip the next quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                result.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        
        // Add the last field
        result.add(field.toString());
        
        return result.toArray(new String[0]);
    }
    
    private Map<String, Integer> mapColumnIndices(String[] headers) {
        Map<String, Integer> indices = new HashMap<>();
        
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase().trim();
            
            // Find field mappings
            for (Map.Entry<String, List<String>> entry : FIELD_MAPPINGS.entrySet()) {
                String fieldType = entry.getKey();
                List<String> possibleNames = entry.getValue();
                
                if (possibleNames.contains(header) || possibleNames.stream().anyMatch(header::contains)) {
                    indices.put(fieldType, i);
                    break;
                }
            }
        }
        
        return indices;
    }
    
    private long parseDuration(String durationStr, String format) {
        if (durationStr == null || durationStr.isEmpty()) {
            return 0;
        }
        
        // Remove any non-numeric characters except decimal points
        durationStr = durationStr.replaceAll("[^\\d.]", "");
        
        if (durationStr.isEmpty()) {
            return 0;
        }
        
        try {
            if (format.equals(SPOTIFY) || format.equals(YANDEX_MUSIC)) {
                // Typically already in milliseconds
                return Long.parseLong(durationStr);
            } else if (format.equals(APPLE_MUSIC)) {
                // Might be in seconds, convert to ms
                double seconds = Double.parseDouble(durationStr);
                return (long) (seconds * 1000);
            } else {
                // Try to determine if it's seconds or milliseconds
                if (durationStr.length() <= 6) { // Likely seconds or minutes
                    double seconds = Double.parseDouble(durationStr);
                    return (long) (seconds * 1000);
                } else { // Likely milliseconds
                    return Long.parseLong(durationStr);
                }
            }
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    private LocalDateTime parseTimestamp(String timestamp, String format) {
        if (timestamp == null || timestamp.isEmpty()) {
            return null;
        }
        
        // Common date formats
        String[] patterns = {
            "yyyy-MM-dd'T'HH:mm:ss'Z'", // ISO
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", // ISO with milliseconds
            "yyyy-MM-dd HH:mm:ss", // Generic
            "MM/dd/yyyy HH:mm:ss", // US format
            "dd/MM/yyyy HH:mm:ss", // EU format
            "yyyy/MM/dd HH:mm:ss", // Alternative
            "yyyy-MM-dd", // Date only
            "MM/dd/yyyy", // US date
            "dd/MM/yyyy" // EU date
        };
        
        // Platform-specific formats
        if (format.equals(SPOTIFY)) {
            patterns = new String[] { "yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd HH:mm:ss" };
        } else if (format.equals(APPLE_MUSIC)) {
            patterns = new String[] { "yyyy-MM-dd HH:mm:ss", "MM/dd/yyyy HH:mm:ss" };
        } else if (format.equals(YANDEX_MUSIC)) {
            patterns = new String[] { "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd HH:mm:ss" };
        }
        
        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
                return LocalDateTime.parse(timestamp, formatter);
            } catch (DateTimeParseException e) {
                // Try next pattern
            }
        }
        
        return null;
    }
}