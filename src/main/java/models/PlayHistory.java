package models;

import java.util.Date;

/**
 * Represents a song play history entry
 */
public class PlayHistory {
    private Song song;
    private Date timestamp;
    private long durationMs; // Duration of playback in milliseconds
    private boolean completed; // Whether the song was played to completion
    private String source; // Source of the play (e.g., "spotify", "local")
    
    public PlayHistory() {
        this.timestamp = new Date();
        this.durationMs = 0;
        this.completed = false;
        this.source = "unknown";
    }
    
    public PlayHistory(Song song, Date timestamp) {
        this.song = song;
        this.timestamp = timestamp;
        this.durationMs = 0;
        this.completed = false;
        this.source = "unknown";
    }

    public Song getSong() {
        return song;
    }

    public void setSong(Song song) {
        this.song = song;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}