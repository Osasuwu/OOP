package services.ui;

import java.util.*;

/**
 * User interface for interacting with the application
 */
public class UserInterface {
    private final Scanner scanner;
    private final List<String> playlists;
    private final Map<String, List<String>> playlistSongs;

    public UserInterface() {
        this.scanner = new Scanner(System.in);
        this.playlists = new ArrayList<>();
        this.playlistSongs = new HashMap<>();
        seedSampleData();
    }

    private void seedSampleData() {
        playlists.add("Chill Vibes");
        playlists.add("Workout Mix");
        playlists.add("Top Hits");

        playlistSongs.put("Chill Vibes", new ArrayList<>(Arrays.asList("Lo-fi Dreams", "Relaxing Waves")));
        playlistSongs.put("Workout Mix", new ArrayList<>(Arrays.asList("Power Boost", "High Energy")));
        playlistSongs.put("Top Hits", new ArrayList<>(Arrays.asList("Chartbuster", "Pop Anthem")));
    }

    public void start() {
        while (true) {
            System.out.println("\n===== Spotify CLI Menu =====");
            System.out.println("1. View Playlists");
            System.out.println("2. View Songs in a Playlist");
            System.out.println("3. Create a New Playlist");
            System.out.println("4. Add Song to a Playlist");
            System.out.println("5. Remove Song from a Playlist");
            System.out.println("6. Delete a Playlist");
            System.out.println("7. Search for a Song");
            System.out.println("8. Exit");
            System.out.print("Choose an option: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    viewPlaylists();
                    break;
                case "2":
                    viewSongsInPlaylist();
                    break;
                case "3":
                    createNewPlaylist();
                    break;
                case "4":
                    addSongToPlaylist();
                    break;
                case "5":
                    removeSongFromPlaylist();
                    break;
                case "6":
                    deletePlaylist();
                    break;
                case "7":
                    searchSong();
                    break;
                case "8":
                    System.out.println("Exiting... Thank you for using Spotify CLI!");
                    scanner.close();
                    return;
                default:
                    System.out.println("Invalid choice! Please try again.");
            }
        }
    }

    private void viewPlaylists() {
        if (playlists.isEmpty()) {
            System.out.println("No playlists found.");
            return;
        }
        System.out.println("\nAvailable Playlists:");
        for (int i = 0; i < playlists.size(); i++) {
            System.out.println((i + 1) + ". " + playlists.get(i));
        }
    }

    private void viewSongsInPlaylist() {
        System.out.print("Enter playlist name: ");
        String playlistName = scanner.nextLine();
        List<String> songs = playlistSongs.get(playlistName);

        if (songs == null) {
            System.out.println("Playlist not found!");
            return;
        }

        if (songs.isEmpty()) {
            System.out.println("No songs in this playlist.");
            return;
        }

        System.out.println("\nSongs in " + playlistName + ":");
        for (String song : songs) {
            System.out.println("- " + song);
        }
    }

    private void createNewPlaylist() {
        System.out.print("Enter new playlist name: ");
        String newPlaylist = scanner.nextLine();

        if (playlists.contains(newPlaylist)) {
            System.out.println("Playlist already exists!");
            return;
        }

        playlists.add(newPlaylist);
        playlistSongs.put(newPlaylist, new ArrayList<>());
        System.out.println("Playlist '" + newPlaylist + "' created successfully.");
    }

    private void addSongToPlaylist() {
        System.out.print("Enter playlist name: ");
        String playlistName = scanner.nextLine();
        if (!playlistSongs.containsKey(playlistName)) {
            System.out.println("Playlist not found!");
            return;
        }

        System.out.print("Enter song name: ");
        String song = scanner.nextLine();

        playlistSongs.get(playlistName).add(song);
        System.out.println("Song '" + song + "' added to " + playlistName + ".");
    }

    private void removeSongFromPlaylist() {
        System.out.print("Enter playlist name: ");
        String playlistName = scanner.nextLine();
        if (!playlistSongs.containsKey(playlistName)) {
            System.out.println("Playlist not found!");
            return;
        }

        List<String> songs = playlistSongs.get(playlistName);
        if (songs.isEmpty()) {
            System.out.println("No songs in this playlist to remove.");
            return;
        }

        System.out.print("Enter song name to remove: ");
        String song = scanner.nextLine();

        if (!songs.remove(song)) {
            System.out.println("Song not found in the playlist.");
        } else {
            System.out.println("Song '" + song + "' removed from " + playlistName + ".");
        }
    }

    private void deletePlaylist() {
        System.out.print("Enter playlist name to delete: ");
        String playlistName = scanner.nextLine();

        if (!playlists.remove(playlistName)) {
            System.out.println("Playlist not found!");
            return;
        }

        playlistSongs.remove(playlistName);
        System.out.println("Playlist '" + playlistName + "' deleted.");
    }

    private void searchSong() {
        System.out.print("Enter song name to search: ");
        String songName = scanner.nextLine();

        boolean found = false;
        for (Map.Entry<String, List<String>> entry : playlistSongs.entrySet()) {
            if (entry.getValue().contains(songName)) {
                System.out.println("Song found in playlist: " + entry.getKey());
                found = true;
            }
        }

        if (!found) {
            System.out.println("Song not found in any playlist.");
        }
    }
}
