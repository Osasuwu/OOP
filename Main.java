package com.spotify;

import com.spotify.auth.AuthManager;

import java.util.Scanner;

/**
 * Main entry point for the Spotify-like CLI.
 * Handles authentication and directs users to further functionalities.
 */
public class Main {
    private static final AuthManager authManager = new AuthManager();
    private static String currentSessionToken = null;  // Stores active session token
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        while (true) {
            showWelcomeMenu();
        }
    }

    /**
     * Displays the welcome menu with authentication options.
     */
    private static void showWelcomeMenu() {
        System.out.println("\n🎵 Welcome to Spotify CLI 🎵");
        System.out.println("1️⃣ Login");
        System.out.println("2️⃣ Register");
        System.out.println("3️⃣ Reset Password");
        System.out.println("4️⃣ Exit");
        System.out.print("👉 Choose an option: ");
        
        String choice = scanner.nextLine();
        switch (choice) {
            case "1": handleLogin(); break;
            case "2": handleRegistration(); break;
            case "3": handlePasswordReset(); break;
            case "4": exitApplication(); break;
            default: System.out.println("❌ Invalid option. Try again.");
        }
    }

    /**
     * Handles the login process.
     */
    private static void handleLogin() {
        System.out.print("\n📧 Enter your email: ");
        String email = scanner.nextLine();
        System.out.print("🔑 Enter your password: ");
        String password = scanner.nextLine();

        String result = authManager.login(email, password);
        if (result.startsWith("✅")) {
            currentSessionToken = result.split(": ")[1]; // Extract session token
            System.out.println(result);
            showMainMenu();
        } else {
            System.out.println(result);
        }
    }

    /**
     * Handles user registration.
     */
    private static void handleRegistration() {
        System.out.print("\n📧 Enter your email: ");
        String email = scanner.nextLine();
        System.out.print("🔑 Enter your password: ");
        String password = scanner.nextLine();

        String result = authManager.register(email, password, "user");
        System.out.println(result);
    }

    /**
     * Handles the password reset process.
     */
    private static void handlePasswordReset() {
        System.out.print("\n📧 Enter your email: ");
        String email = scanner.nextLine();
        System.out.print("🔑 Enter new password: ");
        String newPassword = scanner.nextLine();

        String result = authManager.resetPassword(email, newPassword);
        System.out.println(result);
    }

    /**
     * Displays the main menu after login.
     */
    private static void showMainMenu() {
        while (currentSessionToken != null) {
            System.out.println("\n🎵 Spotify Main Menu 🎵");
            System.out.println("1️⃣ View Playlists");
            System.out.println("2️⃣ Logout");
            System.out.print("👉 Choose an option: ");

            String choice = scanner.nextLine();
            switch (choice) {
                case "1": System.out.println("📂 Feature coming soon!"); break;
                case "2": handleLogout(); break;
                default: System.out.println("❌ Invalid option. Try again.");
            }
        }
    }

    /**
     * Handles user logout.
     */
    private static void handleLogout() {
        String result = authManager.logout(currentSessionToken);
        System.out.println(result);
        currentSessionToken = null;
    }

    /**
     * Exits the application.
     */
    private static void exitApplication() {
        System.out.println("👋 Exiting Spotify CLI. See you next time!");
        scanner.close();
        System.exit(0);
    }
}
