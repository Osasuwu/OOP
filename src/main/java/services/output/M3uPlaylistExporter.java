package services.output;

import models.Playlist;
import models.Song;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class M3uPlaylistExporter implements PlaylistExporter {
    @Override
    public void export(Playlist playlist, String destination) {
        // Create the directory if it doesn't exist
        File directory = new File(destination);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                System.err.println("Failed to create directory: " + destination);
                return;
            }
        }
        
        // Create the full file path
        String fileName = playlist.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + ".m3u"; // Replace invalid characters
        String fullPath = destination + File.separator + fileName;
        
        try (FileWriter writer = new FileWriter(fullPath)) {
            writer.append("#EXTM3U\n"); // M3U playlist header

            for (Song song : playlist.getSongs()) {
                // Calculate duration in seconds for M3U format (M3U expects seconds not ms)
                long durationSec = song.getDurationMs() / 1000;
                
                writer.append("#EXTINF:")
                      .append(String.valueOf(durationSec))
                      .append(",")
                      .append(song.getArtist().getName()) // Use the correct method
                      .append(" - ")
                      .append(song.getTitle())
                      .append("\n");
                
                // Use Spotify URL if available, otherwise use a placeholder
                String path = song.getSpotifyLink() != null ? song.getSpotifyLink() : 
                             (song.getFilePath() != null ? song.getFilePath() : song.getTitle() + ".mp3");
                writer.append(path).append("\n");
            }

            System.out.println("Playlist exported as M3U to: " + fullPath);
        } catch (IOException e) {
            System.err.println("Error exporting M3U: " + e.getMessage());
        }
    }
}
