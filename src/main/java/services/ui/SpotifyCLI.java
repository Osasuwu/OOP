package services.ui;

import java.util.*;
import java.io.*;

/**
 * Command-line interface for Spotify functionality
 */
public class SpotifyCLI {
    private static final String USERS_FILE = "users.txt";
    private static final Scanner scanner = new Scanner(System.in);
    private static Map<String, User> users = new HashMap<>();
    private static User currentUser = null;

    public static void main(String[] args) {
        loadUsers();
        showMainMenu();
        saveUsers();
        scanner.close();
    }

    private static void showMainMenu() {
        while (true) {
            System.out.println("\n===== Spotify CLI =====");
            System.out.println("1. Sign Up");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Choose an option: ");
            
            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
                continue;
            }

            switch (choice) {
                case 1: signUp(); break;
                case 2: login(); break;
                case 3: return;
                default: System.out.println("Invalid choice! Try again.");
            }
        }
    }

    private static void signUp() {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        
        if (users.containsKey(username)) {
            System.out.println("Username already exists!");
            return;
        }
        
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        
        User newUser = new User(username, password);
        users.put(username, newUser);
        
        System.out.println("User created successfully!");
    }

    private static void login() {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        
        User user = users.get(username);
        if (user != null && user.password.equals(password)) {
            System.out.println("Login successful!");
            currentUser = user;
            showUserMenu();
        } else {
            System.out.println("Invalid username or password!");
        }
    }

    private static void showUserMenu() {
        while (true) {
            System.out.println("\n==== User Menu ====");
            System.out.println("1. Create Playlist");
            System.out.println("2. View Playlists");
            System.out.println("3. Logout");
            System.out.print("Choose an option: ");
            int choice;
            try {
                choice = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
                continue;
            }

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
                writer.write(user.username + "," + user.password);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Error saving user data: " + e.getMessage());
        }
    }

    // User class to store user data
    static class User {
        String username;
        String password;
        List<String> playlists = new ArrayList<>();
        
        User(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}
