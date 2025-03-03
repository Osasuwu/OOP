package services.datasources;

import interfaces.MusicDataSource;
import models.UserMusicData;
import java.nio.file.Path;

public class CsvHistorySource implements MusicDataSource {
    private final Path csvPath;
    private final String format;

    public CsvHistorySource(String path, String format) {
        this.csvPath = Path.of(path);
        this.format = format;
    }

    @Override
    public UserMusicData loadMusicData() {
        UserMusicData userData = new UserMusicData();
        // Implementation
        return userData;
    }
    
    @Override
    public boolean isSourceAvailable() {
        return csvPath != null && csvPath.toFile().exists() && csvPath.toFile().isFile();
    }
} 