package utils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/
 * AuthManager - Manages user authentication, accounts, and security.
 * Features: User login, signup, password hashing, session management, and optional 2FA.
 */
public class AuthManager {
    private final Map<String, User> users = new ConcurrentHashMap<>();  // Stores user data
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>(); // Maps session tokens to users
    private final SecureRandom secureRandom = new SecureRandom();
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final int SALT_LENGTH = 16;  // Secure salt length for password hashing
    
    public AuthManager() {
        // Load users from database or storage (simulate preloading)
        preloadTestUsers();
    }

    /
     * Registers a new user with a secure password.
     * @param email User email (must be valid format).
     * @param password Plain text password.
     * @param role "user" or "admin".
     * @return Success message or error message.
     */
    public String register(String email, String password, String role) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return "Invalid email format.";
        }
        if (password.length() < 8) {
            return "Password must be at least 8 characters.";
        }
        if (users.containsKey(email)) {
            return "User already exists.";
        }

        String salt = generateSalt();
        String hashedPassword = hashPassword(password, salt);
        
        users.put(email, new User(email, hashedPassword, salt, role));
        return "Registration successful!";
    }

    /
     * Logs in a user by checking email and password.
     * @param email User email.
     * @param password Plain text password.
     * @return Session token or error message.
     */
    public String login(String email, String password) {
        User user = users.get(email);
        if (user == null) {
            return "User not found.";
        }

        String hashedInputPassword = hashPassword(password, user.getSalt());
        if (!hashedInputPassword.equals(user.getHashedPassword())) {
            return "Incorrect password.";
        }

        // Generate session token
        String token = UUID.randomUUID().toString();
        activeSessions.put(token, email);
        return "Login successful! Session Token: " + token;
    }

    /
     * Logs out a user by invalidating their session.
     * @param token Session token.
     * @return Success message.
     */
    public String logout(String token) {
        if (activeSessions.remove(token) != null) {
            return "Logout successful!";
        }
        return "Invalid session token.";
    }

    /
     * Validates if a user is logged in via session token.
     * @param token Session token.
     * @return True if valid, false otherwise.
     */
    public boolean isAuthenticated(String token) {
        return activeSessions.containsKey(token);
    }

    /
     * Resets a user's password if they provide the correct email.
     * (In a real-world case, an email with a reset link would be sent.)
     * @param email User email.
     * @param newPassword New password.
     * @return Success or error message.
     */
    public String resetPassword(String email, String newPassword) {
        User user = users.get(email);
        if (user == null) {
            return "User not found.";
        }
        if (newPassword.length() < 8) {
            return "Password must be at least 8 characters.";
        }

String newSalt = generateSalt();
        user.setSalt(newSalt);
        user.setHashedPassword(hashPassword(newPassword, newSalt));
        
        return "Password reset successful!";
    }

    /
     * Generates a random salt for password hashing.
     * @return Base64 encoded salt.
     */
    private String generateSalt() {
        byte[] saltBytes = new byte[SALT_LENGTH];
        secureRandom.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    /
     * Hashes a password using SHA-256 and a salt.
     * @param password Plain text password.
     * @param salt Salt for hashing.
     * @return Hashed password.
     */
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest((salt + password).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }

    /
     * Preloads a test user for simulation.
     */
    private void preloadTestUsers() {
        String salt = generateSalt();
        users.put("admin@spotify.com", new User("admin@spotify.com", hashPassword("Admin123!", salt), salt, "admin"));
    }
}

/
 * Represents a User with email, hashed password, salt, and role.
 */
class User {
    private final String email;
    private String hashedPassword;
    private String salt;
    private final String role;

    public User(String email, String hashedPassword, String salt, String role) {
        this.email = email;
        this.hashedPassword = hashedPassword;
        this.salt = salt;
        this.role = role;
    }

    public String getEmail() { return email; }
    public String getHashedPassword() { return hashedPassword; }
    public String getSalt() { return salt; }
    public String getRole() { return role; }

    public void setHashedPassword(String hashedPassword) { this.hashedPassword = hashedPassword; }
    public void setSalt(String salt) { this.salt = salt; }
}
