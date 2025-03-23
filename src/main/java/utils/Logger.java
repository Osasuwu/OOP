package com.spotify.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;

public class Logger {
    private static final Logger instance = new Logger();
    private final java.util.logging.Logger logger;

    private Logger() {
        logger = java.util.logging.Logger.getLogger("SpotifyLogger");
        setupLogger();
    }

    private void setupLogger() {
        try {
            // Create logs directory if it doesn't exist
            Files.createDirectories(Paths.get("logs"));

            // File Handler (logs saved in logs/app.log)
            FileHandler fileHandler = new FileHandler("logs/app.log", true);
            fileHandler.setFormatter(new SimpleFormatter());
            
            // Console Handler (Logs to terminal)
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(new SimpleFormatter());

            // Add handlers to logger
            logger.addHandler(fileHandler);
            logger.addHandler(consoleHandler);
            logger.setUseParentHandlers(false);
            
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }

    public static Logger getInstance() {
        return instance;
    }

    public void info(String message) {
        logger.log(Level.INFO, message);
    }

    public void warning(String message) {
        logger.log(Level.WARNING, message);
    }

    public void error(String message) {
        logger.log(Level.SEVERE, message);
    }
}
