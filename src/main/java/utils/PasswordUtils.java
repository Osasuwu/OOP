package utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for secure password handling
 */
public class PasswordUtils {
    private static final int SALT_LENGTH = 16; // 16 bytes = 128 bits
    private static final String HASH_ALGORITHM = "SHA-512";
    
    /**
     * Generates a random salt for password hashing
     * 
     * @return A random salt as a Base64 string
     */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    /**
     * Hashes a password with the provided salt using SHA-512
     * 
     * @param password The plain-text password to hash
     * @param salt The salt to use for hashing
     * @return The hashed password as a Base64 string
     */
    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] saltBytes = Base64.getDecoder().decode(salt);
            md.update(saltBytes);
            byte[] hashedPassword = md.digest(password.getBytes());
            
            return Base64.getEncoder().encodeToString(hashedPassword);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password: " + e.getMessage(), e);
        }
    }
    
    /**
     * Verifies a password against a stored hash and salt
     * 
     * @param plainPassword The plain-text password to verify
     * @param storedHash The stored password hash
     * @param storedSalt The stored salt used for hashing
     * @return true if the password matches, false otherwise
     */
    public static boolean verifyPassword(String plainPassword, String storedHash, String storedSalt) {
        String computedHash = hashPassword(plainPassword, storedSalt);
        return computedHash.equals(storedHash);
    }
}