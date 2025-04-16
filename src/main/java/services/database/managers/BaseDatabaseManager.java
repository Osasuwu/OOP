package services.database.managers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import models.User;

public abstract class BaseDatabaseManager {
    protected static final Logger LOGGER = LoggerFactory.getLogger(BaseDatabaseManager.class);
    protected final boolean isOnline;
    protected final User user;

    protected BaseDatabaseManager(boolean isOnline, User user) {
        this.isOnline = isOnline;
        this.user = user;
    }

    protected boolean isOfflineMode() {
        return !isOnline;
    }

    protected String getOfflineStoragePath() {
        if (user == null) {
            LOGGER.warn("No user available, using default storage path");
            return "data/default/";
        }
        return String.format("data/%s/", user.getId());
    }
}
