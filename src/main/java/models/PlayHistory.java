package models;

import java.time.LocalDateTime;

public class PlayHistory {
    private Song song;
    private LocalDateTime playedAt;
    private int playCount;

    public Song getSong() { return song; }
    public void setSong(Song song) { this.song = song; }

    public LocalDateTime getPlayedAt() { return playedAt; }
    public void setPlayedAt(LocalDateTime playedAt) { this.playedAt = playedAt; }

    public int getPlayCount() { return playCount; }
    public void setPlayCount(int playCount) { this.playCount = playCount; }
} 