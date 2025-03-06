package interfaces;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

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

    public static void main(String[] args) {
        UserInterface ui = new UserInterface();
        ui.start();
    }
}
        this.playlists = new ArrayList<>();
    }
}

public class SpotifyCLI {
    private static final String USERS_FILE = "users.txt";
    private static final Scanner scanner = new Scanner(System.in);
    private static Map<String, User> users = new HashMap<>();
    private static User currentUser = null;

    public static void main(String[] args) {
        loadUsers();
        showMainMenu();
    }

    private static void showMainMenu() {
        while (true) {
            System.out.println("\n==== Spotify CLI ====");
            System.out.println("1. Sign Up");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");
            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1: signUp(); break;
                case 2: login(); break;
                case 3: saveUsers(); System.exit(0);
                default: System.out.println("Invalid choice! Try again.");
            }
        }
    }

    private static void signUp() {
        System.out.print("Enter a username: ");
        String username = scanner.nextLine();
        if (users.containsKey(username)) {
            System.out.println("Username already exists! Try again.");
            return;
        }
        System.out.print("Enter a password: ");
        String password = scanner.nextLine();
        users.put(username, new User(username, password));
        System.out.println("Account created successfully!");
    }

    private static void login() {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        if (users.containsKey(username) && users.get(username).password.equals(password)) {
            currentUser = users.get(username);
            System.out.println("Login successful! Welcome, " + username);
            showUserMenu();
        } else {
            System.out.println("Invalid username or password! Try again.");
        }
    }

    private static void showUserMenu() {
        while (true) {
            System.out.println("\n==== User Menu ====");
            System.out.println("1. Create Playlist");
            System.out.println("2. View Playlists");
            System.out.println("3. Logout");
            System.out.print("Choose an option: ");
            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1: createPlaylist(); break;
                case 2: viewPlaylists(); break;
                case 3: currentUser = null; return;
                default: System.out.println("Invalid choice! Try again.");
            }
        }
    }

    private static void createPlaylist() {
        System.out.print("Enter playlist name: ");
        String playlistName = scanner.nextLine();
        if (currentUser.playlists.contains(playlistName)) {
            System.out.println("Playlist already exists!");
            return;
        }
        currentUser.playlists.add(playlistName);
        System.out.println("Playlist created successfully!");
    }

    private static void viewPlaylists() {
        if (currentUser.playlists.isEmpty()) {
            System.out.println("No playlists found.");
            return;
        }
        System.out.println("\nYour Playlists:");
        for (String playlist : currentUser.playlists) {
            System.out.println("- " + playlist);
        }
    }

private static void loadUsers() {
        try (BufferedReader reader = new BufferedReader(new FileReader(USERS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                User user = new User(parts[0], parts[1]);
                users.put(parts[0], user);
            }
        } catch (IOException e) {
            System.out.println("No existing users found.");
        }
    }

    private static void saveUsers() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USERS_FILE))) {
            for (User user : users.values()) {
                writer.write(user.username + "," + user.password + "\n");
            }
        } catch (IOException e) {
            System.out.println("Error saving users.");
        }
    }
}
