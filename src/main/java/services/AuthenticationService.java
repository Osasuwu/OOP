package services;

import java.util.Scanner;
import java.util.UUID;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Service for handling user authentication
 * Supports both login and signup functionality
 */
public class AuthenticationService {
    private Scanner scanner;
    
    public AuthenticationService() {
        this.scanner = new Scanner(System.in);
    }
    
    /**
     * Prompt the user to either log in or sign up
     * @return the user ID after successful authentication or null if canceled
     */
    public String promptLoginOrSignup() {
        while (true) {
            System.out.println("\n===== Authentication =====");
            System.out.println("1. Log in");
            System.out.println("2. Sign up");
            System.out.println("3. Cancel");
            System.out.print("Choose an option: ");
            
            String choice = scanner.nextLine().trim();
            
            switch (choice) {
                case "1":
                    String userId = login();
                    if (userId != null) {
                        return userId;
                    }
                    break;
                case "2":
                    String newUserId = signup();
                    if (newUserId != null) {
                        return newUserId;
                    }
                    break;
                case "3":
                    return null;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }
    
    /**
     * Handle the login process
     * @return User ID if successful, null otherwise
     */
    private String login() {
        System.out.println("\n----- Login -----");
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        
        System.out.print("Email: ");
        String email = scanner.nextLine().trim();
        
        try {
            // Get database connection - assume we're using the application-wide connection
            Connection conn = getDatabaseConnection();
            String userId = getUserId(conn, username, email);
            
            if (userId != null) {
                System.out.println("Login successful!");
                return userId;
            } else {
                System.out.println("User not found. Please check your credentials or sign up.");
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error during login: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Handle the signup process
     * @return New user ID if successful, null otherwise
     */
    private String signup() {
        System.out.println("\n----- Sign Up -----");
        System.out.print("Username: ");
        String username = scanner.nextLine().trim();
        
        System.out.print("Email: ");
        String email = scanner.nextLine().trim();
        
        if (username.isEmpty() || email.isEmpty()) {
            System.out.println("Username and email are required.");
            return null;
        }
        
        try {
            // Get database connection
            Connection conn = getDatabaseConnection();
            
            // Check if user already exists
            String existingUserId = getUserId(conn, username, email);
            if (existingUserId != null) {
                System.out.println("User already exists. Please log in instead.");
                return existingUserId;
            }
            
            // Create new user
            UUID newUserId = createUser(conn, username, email);
            if (newUserId != null) {
                System.out.println("User created successfully!");
                return newUserId.toString();
            } else {
                System.out.println("Failed to create user.");
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error during signup: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Get user ID from database
     */
    private String getUserId(Connection conn, String username, String email) throws SQLException {
        String query = "SELECT id FROM users WHERE username = ? AND email = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, email);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("id");
            }
        }
        return null;
    }
    
    /**
     * Create a new user in the database
     */
    private UUID createUser(Connection conn, String username, String email) throws SQLException {
        String query = "INSERT INTO users (id, username, email) VALUES (?, ?, ?)";
        UUID userId = UUID.randomUUID();
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setObject(1, userId);
            stmt.setString(2, username);
            stmt.setString(3, email);
            stmt.executeUpdate();
        }
        return userId;
    }
    
    /**
     * Get a database connection
     */
    private Connection getDatabaseConnection() throws SQLException {
        // In a real implementation, this would be handled by a database connection manager
        // For now, this is a placeholder
        try {
            services.database.MusicDatabaseManager dbManager = new services.database.MusicDatabaseManager(true, "default");
            return dbManager.getConnection();
        } catch (Exception e) {
            throw new SQLException("Failed to get database connection: " + e.getMessage());
        }
    }
}
