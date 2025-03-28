package services.output;

import models.Playlist;
import models.Song;

import java.io.FileWriter;
import java.io.IOException;

public class M3uPlaylistExporter implements PlaylistExporter {
    @Override
    public void export(Playlist playlist, String destination) {
        try (FileWriter writer = new FileWriter(destination)) {
 output
            writer.append("#EXTM3U\n"); 

            for (Song song : playlist.getSongs()) {
                writer.append("#EXTINF:").append(String.valueOf(song.getDurationMs())).append(",")
                      .append(song.getArtistName()).append(" - ").append(song.getTitle()).append("\n");
                writer.append(song.getFilePath()).append("\n");

            writer.append("#EXTM3U\n"); // M3U playlist header

            for (Song song : playlist.getSongs()) {
                writer.append("#EXTINF:").append(String.valueOf(song.getDuration())).append(",")
                      .append(song.getArtist()).append(" - ").append(song.getTitle()).append("\n");
                writer.append(song.getFilePath()).append("\n"); // Assuming Song has a file path
main
            }

            System.out.println("Playlist exported as M3U to: " + destination);
        } catch (IOException e) {
            System.err.println("Error exporting M3U: " + e.getMessage());
        }
    }
}

