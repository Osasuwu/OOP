package services.output;

import models.Playlist;
import models.Song;

import java.io.FileWriter;
import java.io.IOException;

public class CsvPlaylistExporter implements PlaylistExporter {
    @Override
    public void export(Playlist playlist, String destination) {
        try (FileWriter writer = new FileWriter(destination)) {
            writer.append("Title,Artist,Duration (seconds)\n"); // CSV header

            for (Song song : playlist.getSongs()) {
                writer.append(song.getTitle()).append(",")
                      .append(song.getArtist()).append(",")
                      .append(String.valueOf(song.getDuration())).append("\n");
            }

            System.out.println("Playlist exported as CSV to: " + destination);
        } catch (IOException e) {
            System.err.println("Error exporting CSV: " + e.getMessage());
        }
    }
}
