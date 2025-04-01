package services.output;

public class PlaylistExporterFactory {
    public static PlaylistExporter getExporter(String format) {
        // Return the appropriate exporter based on the format
        switch (format.toLowerCase()) {
            case "csv":
                return new CsvPlaylistExporter();
            case "m3u":
                return new M3uPlaylistExporter();
            case "spotify":
                return new SpotifyPlaylistExporter();
            // Add more formats as needed
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }
}
