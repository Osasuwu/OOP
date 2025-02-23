package services.datasources;

import interfaces.MusicDataSource;
import models.UserMusicData;

public class SpotifyHistorySource implements MusicDataSource {
    @Override
    public UserMusicData loadMusicData() {
        UserMusicData userData = new UserMusicData();
        // Implementation
        return userData;
    }
    
    @Override
    public boolean isSourceAvailable() {
        // Check if Spotify data is available
        return true;
    }
} 