package services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import services.config.Config;
import services.database.MusicDatabaseManager;
import models.User;
import utils.PasswordUtils;

import java.security.SecureRandom;
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
                    System.out.print("Password: ");
                    String password = scanner.nextLine();
                    try {
                        User user = login(username, email, password);
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
                    System.out.print("Password: ");
                    password = scanner.nextLine();
                    System.out.print("Confirm Password: ");
                    String confirmPassword = scanner.nextLine();
                    
                    if (!password.equals(confirmPassword)) {
                        System.out.println("Passwords do not match!");
                        System.exit(1);
                    }
                    
                    try {
                        User user = signUp(username, email, password);
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

    public User login(String username, String email, String password) throws SQLException {
        try {
            // Check if user exists and get password hash and salt
            String sql = "SELECT id, username, email, password_hash, password_salt FROM users WHERE username = ? AND email = ?";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                stmt.setString(2, email);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    String userId = rs.getString("id");
                    String dbUsername = rs.getString("username");
                    String dbEmail = rs.getString("email");
                    String passwordHash = rs.getString("password_hash");
                    String passwordSalt = rs.getString("password_salt");
                    
                    // Verify the password
                    if (passwordHash != null && passwordSalt != null && 
                            PasswordUtils.verifyPassword(password, passwordHash, passwordSalt)) {
                        this.currentUser = new User(userId, dbUsername, dbEmail, passwordHash, passwordSalt);
                        saveLogin();
                        LOGGER.info("User logged in successfully: {}", username);
                        return currentUser;
                    }
                }
                LOGGER.info("Login failed for user: {}", username);
                return null;
            }
        } catch (SQLException e) {
            LOGGER.error("Database error during login: {}", e.getMessage());
            throw e;
        }
    }

    public User signUp(String username, String email, String password) throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            // Check if user already exists
            String checkSql = "SELECT id, password_hash FROM users WHERE username = ? OR email = ?";
            String userId = null;
            boolean userExistsWithoutPassword = false;
            
            try (PreparedStatement stmt = conn.prepareStatement(checkSql)) {
                stmt.setString(1, username);
                stmt.setString(2, email);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    userId = rs.getString("id");
                    String existingPasswordHash = rs.getString("password_hash");
                    userExistsWithoutPassword = (existingPasswordHash == null || existingPasswordHash.isEmpty());
                    
                    if (!userExistsWithoutPassword) {
                        LOGGER.warn("User already exists: {}", username);
                        return null;
                    }
                }
            }

            // Generate salt and hash for password
            String salt = PasswordUtils.generateSalt();
            String passwordHash = PasswordUtils.hashPassword(password, salt);

            if (userExistsWithoutPassword) {
                // Update existing user with password
                String updateSql = "UPDATE users SET password_hash = ?, password_salt = ? WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                    stmt.setString(1, passwordHash);
                    stmt.setString(2, salt);
                    stmt.setObject(3, UUID.fromString(userId));
                    
                    int rows = stmt.executeUpdate();
                    if (rows > 0) {
                        this.currentUser = new User(userId, username, email, passwordHash, salt);
                        saveLogin();
                        LOGGER.info("Password added to existing user: {}", username);
                        return currentUser;
                    }
                }
            } else {
                // Create new user
                String insertSql = "INSERT INTO users (id, username, email, password_hash, password_salt, created_at) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    userId = UUID.randomUUID().toString();
                    stmt.setObject(1, UUID.fromString(userId));
                    stmt.setString(2, username);
                    stmt.setString(3, email);
                    stmt.setString(4, passwordHash);
                    stmt.setString(5, salt);
                    stmt.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
                    
                    int rows = stmt.executeUpdate();
                    if (rows > 0) {
                        this.currentUser = new User(userId, username, email, passwordHash, salt);
                        saveLogin();
                        LOGGER.info("New user created successfully: {}", username);
                        return currentUser;
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Database error during signup: {}", e.getMessage());
            throw e;
        }
        return null;
    }

    // For backward compatibility with existing code
    public User login(String username, String email) throws SQLException {
        LOGGER.warn("Password-less login attempted, please update your code to use password authentication");
        return login(username, email, "");
    }
    
    // For backward compatibility with existing code
    public User signUp(String username, String email) throws SQLException {
        LOGGER.warn("Password-less signup attempted, please update your code to use secure passwords");
        return signUp(username, email, "defaultPassword");
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

    /**
     * Changes the password for the current user
     * 
     * @param currentPassword The current password for verification
     * @param newPassword The new password to set
     * @return true if password was changed successfully, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean changePassword(String currentPassword, String newPassword) throws SQLException {
        if (currentUser == null) {
            LOGGER.warn("Attempted to change password with no logged-in user");
            return false;
        }
        
        try {
            // First verify the current password
            String username = currentUser.getName();
            String email = currentUser.getEmail();
            
            User verifiedUser = login(username, email, currentPassword);
            if (verifiedUser == null) {
                LOGGER.warn("Current password verification failed during password change for user: {}", username);
                return false;
            }
            
            // Generate new salt and hash for the new password
            String newSalt = PasswordUtils.generateSalt();
            String newPasswordHash = PasswordUtils.hashPassword(newPassword, newSalt);
            
            // Update the password in the database
            String updateSql = "UPDATE users SET password_hash = ?, password_salt = ? WHERE id = ?";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, newPasswordHash);
                stmt.setString(2, newSalt);
                stmt.setObject(3, UUID.fromString(currentUser.getId()));
                
                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    // Update the current user object
                    currentUser.setPasswordHash(newPasswordHash);
                    currentUser.setPasswordSalt(newSalt);
                    LOGGER.info("Password changed successfully for user: {}", username);
                    return true;
                }
            }
            
            LOGGER.error("Failed to update password in database for user: {}", username);
            return false;
        } catch (SQLException e) {
            LOGGER.error("Database error during password change: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Initiates a password reset for a user.
     * In a real application, this would send an email with a reset token.
     * For this implementation, we'll just reset the password directly.
     * 
     * @param email The email address of the user requesting reset
     * @return A temporary password to give to the user, or null if the email was not found
     * @throws SQLException if a database error occurs
     */
    public String resetPassword(String email) throws SQLException {
        try {
            // Find the user by email
            String sql = "SELECT id, username FROM users WHERE email = ?";
            try (Connection conn = dbManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, email);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    String userId = rs.getString("id");
                    String username = rs.getString("username");
                    
                    // Generate a temporary password (in production, use a more secure method)
                    String tempPassword = generateTemporaryPassword();
                    
                    // Update the user's password in the database
                    String newSalt = PasswordUtils.generateSalt();
                    String newPasswordHash = PasswordUtils.hashPassword(tempPassword, newSalt);
                    
                    String updateSql = "UPDATE users SET password_hash = ?, password_salt = ? WHERE id = ?";
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setString(1, newPasswordHash);
                        updateStmt.setString(2, newSalt);
                        updateStmt.setObject(3, UUID.fromString(userId));
                        
                        int rows = updateStmt.executeUpdate();
                        if (rows > 0) {
                            LOGGER.info("Password reset successful for user: {}", username);
                            
                            // In a real application, email this temporary password to the user
                            // For now, just return it to be displayed
                            return tempPassword;
                        }
                    }
                }
                
                LOGGER.warn("Password reset failed: No user found with email {}", email);
                return null;
            }
        } catch (SQLException e) {
            LOGGER.error("Database error during password reset: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Generates a temporary password for password resets
     * 
     * @return A random temporary password
     */
    private String generateTemporaryPassword() {
        // Characters to use in the temporary password
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        
        // Create a secure random instance
        SecureRandom random = new SecureRandom();
        
        // Generate a random password of length 12
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            int randomIndex = random.nextInt(chars.length());
            password.append(chars.charAt(randomIndex));
        }
        
        return password.toString();
    }
}
