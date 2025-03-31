package services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.config.Config;
import services.database.MusicDatabaseManager;
import models.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

public class AuthenticationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);
    private User currentUser;
    private Config config;
    private MusicDatabaseManager dbManager;
    
    public AuthenticationService() {
        config = Config.getInstance();
        loadSavedLogin();
        // Initialize database with null user if no saved login
        this.dbManager = new MusicDatabaseManager(true, currentUser);
    }

    private void loadSavedLogin() {
        Map<String, String> savedLogin = config.getLastLogin();
        String userId = savedLogin.get("userId");
        String username = savedLogin.get("username");
        String email = savedLogin.get("email");
        
        if (userId != null && username != null) {
            this.currentUser = new User(userId, username, email);
            LOGGER.info("Loaded saved login for user: {}", username);
        }
    }

    private void saveLogin() {
        if (currentUser != null) {
            config.setLastLogin(currentUser.getId(), currentUser.getName(), currentUser.getEmail());
            LOGGER.info("Saved login for user: {}", currentUser.getName());
        }
    }

    public boolean hasValidSession() {
        return currentUser != null;
    }

    public User promptLoginOrSignup() {
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("\n===== Authentication =====");
            System.out.println("1. Log in");
            System.out.println("2. Sign up");
            System.out.println("3. Cancel");
            System.out.print("Choose an option: ");
            
            String choice = scanner.nextLine();
            switch (choice) {
                case "1":
                    System.out.println("\n----- Login -----");
                    System.out.print("Username: ");
                    String username = scanner.nextLine();
                    System.out.print("Email: ");
                    String email = scanner.nextLine();
                    try {
                        User user = login(username, email);
                        if (user != null) {
                            System.out.println("Login successful!");
                            return user;
                        } else {
                            System.out.println("Login failed. Invalid credentials.");
                            System.exit(1);
                        }
                    } catch (SQLException e) {
                        System.out.println("Login error: " + e.getMessage());
                        System.exit(1);
                    }
                    break;
                    
                case "2":
                    System.out.println("\n----- Sign Up -----");
                    System.out.print("Username: ");
                    username = scanner.nextLine();
                    System.out.print("Email: ");
                    email = scanner.nextLine();
                    try {
                        User user = signUp(username, email);
                        if (user != null) {
                            System.out.println("Sign up successful!");
                            return user;
                        } else {
                            System.out.println("Sign up failed. Username or email already exists.");
                            System.exit(1);
                        }
                    } catch (SQLException e) {
                        System.out.println("Sign up error: " + e.getMessage());
                        System.exit(1);
                    }
                    break;
                    
                case "3":
                    System.out.println("Authentication cancelled.");
                    System.exit(0);
                    break;
                    
                default:
                    System.out.println("Invalid option.");
                    System.exit(1);
            }
            return null;
        }
    }

    public User login(String username, String email) throws SQLException {
        try {
            // Check if user exists
            String sql = "SELECT id, username, email FROM users WHERE username = ? AND email = ?";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, email);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    String userId = rs.getString("id");
                    String dbUsername = rs.getString("username");
                    String dbEmail = rs.getString("email");
                    
                    this.currentUser = new User(userId, dbUsername, dbEmail);
                    saveLogin();
                    LOGGER.info("User logged in successfully: {}", username);
                    return currentUser;
                }
                LOGGER.info("Login failed for user: {}", username);
                return null;
            }
        } catch (SQLException e) {
            LOGGER.error("Database error during login: {}", e.getMessage());
            throw e;
        }
    }

    public User signUp(String username, String email) throws SQLException {
        // Check if user already exists
        String checkSql = "SELECT id FROM users WHERE username = ? OR email = ?";
        try (Connection conn = dbManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setString(1, username);
                stmt.setString(2, email);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    LOGGER.warn("User already exists: {}", username);
                    return null;
                }
            }

            // Create new user
            String insertSql = "INSERT INTO users (id, username, email, created_at) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                String userId = UUID.randomUUID().toString();
                stmt.setObject(1, UUID.fromString(userId));
                stmt.setString(2, username);
                stmt.setString(3, email);
                stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                
                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    this.currentUser = new User(userId, username, email);
                    saveLogin();
                    LOGGER.info("New user created successfully: {}", username);
                    return currentUser;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Database error during signup: {}", e.getMessage());
            throw e;
        }
        return null;
    }

    public User getUser() {
        return currentUser;
    }

    /**
     * Logs out the current user by clearing the saved credentials
     */
    public void logout() {
        if (currentUser != null) {
            LOGGER.info("Logging out user: {}", currentUser != null ? currentUser.getName() : "null");
        }
        this.currentUser = null;
        config.clearLogin();
    }

    public User getCurrentUser() {
        return currentUser;
    }
}
