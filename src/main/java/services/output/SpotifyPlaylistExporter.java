package services.output;

import models.Playlist;
import models.Song;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SpotifyPlaylistExporter implements PlaylistExporter {
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
        String fileName = playlist.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + "_spotify.txt"; // Replace invalid characters
        String fullPath = destination + File.separator + fileName;
        
        try (FileWriter writer = new FileWriter(fullPath)) {
            writer.write("# Spotify Playlist: " + playlist.getName() + "\n");
            writer.write("# Export Date: " + java.time.LocalDate.now() + "\n\n");
            
            List<String> spotifyIds = new ArrayList<>();
            
            // Collect all the valid Spotify IDs
            for (Song song : playlist.getSongs()) {
                String spotifyId = song.getSpotifyId();
                if (spotifyId != null && !spotifyId.isEmpty()) {
                    spotifyIds.add(spotifyId);
                    writer.write(song.getArtist().getName() + " - " + song.getTitle() + "\n");
                    writer.write("spotify:track:" + spotifyId + "\n\n");
                } else {
                    writer.write(song.getArtist().getName() + " - " + song.getTitle() + " (No Spotify ID available)\n\n");
                }
            }
            
            // Write a summary section with just the URIs for easy copy-paste
            if (!spotifyIds.isEmpty()) {
                writer.write("\n# Spotify URIs (Copy and paste these to import into Spotify):\n");
                for (String id : spotifyIds) {
                    writer.write("spotify:track:" + id + "\n");
                }
            }
            
            System.out.println("Playlist exported for Spotify to: " + fullPath);
            
            if (spotifyIds.isEmpty()) {
                System.out.println("Note: No Spotify IDs were found in the playlist. The export file contains only song titles.");
            } else {
                System.out.println("Found " + spotifyIds.size() + " songs with Spotify IDs out of " + playlist.getSongs().size() + " total songs.");
            }
        } catch (IOException e) {
            System.err.println("Error exporting Spotify playlist: " + e.getMessage());
            e.printStackTrace();
        }
    }
}