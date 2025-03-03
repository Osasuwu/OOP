package services.datasources;

import interfaces.MusicDataSource;
import models.*;
import java.io.*;
import java.util.*;
import java.time.*;
import java.time.format.*;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class IFTTTSpotifySource implements MusicDataSource {
    private final String csvFilePath;
    private static final String[] KNOWN_IFTTT_HEADERS = {
        "PlayedAt,Title,Artist",  // Common IFTTT format
        "Date,Title,Artist",      // Alternative format
        "Timestamp,Track,Artist"  // Another variation
    };
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("\"MMMM d, yyyy 'at' hh:mma\"", Locale.ENGLISH);

    public IFTTTSpotifySource(String csvFilePath) {
        this.csvFilePath = csvFilePath;
    }

    @Override
    public UserMusicData loadMusicData() {
        UserMusicData userData = new UserMusicData();
        Map<String, Artist> artists = new HashMap<>();
        Map<String, Song> songs = new HashMap<>();
        List<PlayHistory> history = new ArrayList<>();

        try {
            List<Map<String, String>> csvData = readCSV(csvFilePath);
            
            for (Map<String, String> row : csvData) {
                String artistName = row.get("Artist");
                String title = row.get("Title");
                String playedAt = row.get("PlayedAt");
                String spotifyId = row.get("SpotifyId");
                String spotifyLink = row.get("SpotifyLink");

                // Create or get Artist
                Artist artist = artists.computeIfAbsent(artistName, name -> {
                    Artist a = new Artist();
                    a.setName(name);
                    return a;
                });

                // Create or get Song
                String songKey = title + "|" + artistName;
                Song song = songs.computeIfAbsent(songKey, k -> {
                    Song s = new Song();
                    s.setTitle(title);
                    s.setArtist(artistName);
                    s.setSpotifyId(spotifyId);
                    return s;
                });

                // Create PlayHistory entry
                PlayHistory entry = new PlayHistory();
                entry.setSong(song);
                entry.setPlayedAt(parseDateTime(playedAt));
                entry.setPlayCount(1);
                history.add(entry);
            }

            userData.setSongs(new ArrayList<>(songs.values()));
            userData.setArtists(new ArrayList<>(artists.values()));
            userData.setPlayHistory(history);

        } catch (Exception e) {
            System.err.println("Error reading IFTTT Spotify CSV: " + e.getMessage());
        }

        return userData;
    }

    @Override
    public boolean isSourceAvailable() {
        if (csvFilePath == null) return false;
        
        File file = new File(csvFilePath);
        if (!file.exists() || !file.isFile()) return false;

        try {
            String header = getCSVHeader(csvFilePath);
            return Arrays.stream(KNOWN_IFTTT_HEADERS)
                        .anyMatch(knownHeader -> 
                            header.replaceAll("\\s+", "")
                                  .equalsIgnoreCase(knownHeader.replaceAll("\\s+", "")));
        } catch (Exception e) {
            return false;
        }
    }

    private List<Map<String, String>> readCSV(String filePath) throws IOException {
        List<Map<String, String>> data = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String[] headers = br.readLine().split(",");
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = parseCSVLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length && i < values.length; i++) {
                    row.put(headers[i].trim(), values[i].trim());
                }
                data.add(row);
            }
        }
        return data;
    }

    private String getCSVHeader(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            return br.readLine();
        }
    }

    private String[] parseCSVLine(String line) {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            // Try different IFTTT date formats
            String[] formats = {
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss",
                "MM/dd/yyyy HH:mm:ss",
                "MMMM d, yyyy 'at' hh:mma"
            };

            for (String format : formats) {
                try {
                    return LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern(format));
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.err.println("Error parsing date: " + dateTimeStr);
        }
        return LocalDateTime.now(); // Fallback to current time if parsing fails
    }
} 