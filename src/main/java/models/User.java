package models;

import java.util.List;
import java.util.Map;

public class User {
    private String id;
    private String name;
    private String email;
    private String passwordHash;
    private String passwordSalt;
    private Map<String, List<Object>> preferences;

    public User(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public User(String id, String name, String email, String passwordHash, String passwordSalt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    public Map<String, List<Object>> getPreferences() {
        return preferences;
    }

    public void setPreferences(Map<String, List<Object>> preferences) {
        this.preferences = preferences;
    }
}
