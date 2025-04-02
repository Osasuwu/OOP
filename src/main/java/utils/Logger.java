package utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;

/**
 * Logger class for the application.
 * <p>
 * Enhancements:
 * 1. Multiple log levels: Console shows only WARNINGS/ERRORS; file logs everything.
 * 2. Configurable log directory and file name.
 * 3. Asynchronous logging: Logging is offloaded to a background thread.
 * 4. Log rotation: When the file reaches 10MB, it rotates keeping up to 5 backup logs.
 * 5. Custom log format: Includes timestamp, log level, and thread ID.
 */
public class Logger {
    
    // Singleton instance.
    private static final Logger instance = new Logger();
    
    // Underlying java.util.logging.Logger.
    private final java.util.logging.Logger logger;
    
    // Executor for asynchronous logging.
    private final ExecutorService logExecutor;
    
    // Configurable logging parameters.
    private final String logDirectory;
    private final String logFileName;
    
    // Private constructor initializes the logger.
    private Logger() {
        // Define default configuration.
        this.logDirectory = "logs";
        this.logFileName = "app.log";
        
        // Initialize the executor for asynchronous logging.
        this.logExecutor = Executors.newSingleThreadExecutor();
        
        // Create the underlying Logger instance.
        logger = java.util.logging.Logger.getLogger("SpotifyLogger");
        
        // Set up logger with the specified configuration.
        setupLogger(logDirectory, logFileName);
    }
    
    /**
     * Configures the logger with a custom log directory and file name.
     * Log rotation is enabled: 10 MB per file up to 5 files.
     * The console handler only shows warnings or errors, while the file handler logs everything.
     *
     * @param logDirectory The directory in which log files will be stored.
     * @param logFileName  The name of the log file.
     */
    private void setupLogger(String logDirectory, String logFileName) {
        try {
            // Create logs directory if it doesn't exist.
            Files.createDirectories(Paths.get(logDirectory));
            
            // Build the log file path.
            String logFilePath = logDirectory + "/" + logFileName;
            
            // Log rotation: maximum file size 10MB, 5 rotating files.
            FileHandler fileHandler = new FileHandler(logFilePath, 10 * 1024 * 1024, 5, true);
            // Set custom formatter.
            CustomLogFormatter formatter = new CustomLogFormatter();
            fileHandler.setFormatter(formatter);
            // File: log all messages.
            fileHandler.setLevel(Level.ALL);
            
            // Console handler: log only warnings and errors.
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setFormatter(formatter);
            consoleHandler.setLevel(Level.WARNING);
            
            // Add handlers to our logger.
            logger.addHandler(fileHandler);
            logger.addHandler(consoleHandler);
            logger.setUseParentHandlers(false);
            
        } catch (IOException e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
        }
    }
    
    /**
     * A custom log formatter that outputs the timestamp, log level, thread ID, and message.
     */
    private static class CustomLogFormatter extends SimpleFormatter {
        @Override
        public String format(LogRecord record) {
            // Fix: Convert record.getMillis() (a long) to a Date for proper formatting.
            return String.format("[%1$tF %1$tT] [%2$s] [Thread-%3$d] %4$s%n",
                new Date(record.getMillis()),
                record.getLevel(),
                record.getThreadID(),
                record.getMessage());
        }
    }
    
    /**
     * Returns the singleton instance of Logger.
     *
     * @return The Logger instance.
     */
    public static Logger getInstance() {
        return instance;
    }
    
    /**
     * Logs a message asynchronously at the specified level.
     *
     * @param level   The logging level.
     * @param message The message to log.
     */
    private void logAsync(Level level, String message) {
        logExecutor.submit(() -> logger.log(level, message));
    }
    
    /**
     * Logs an INFO level message.
     *
     * @param message The message to log.
     */
    public void info(String message) {
        logAsync(Level.INFO, message);
    }
    
    /**
     * Logs a WARNING level message.
     *
     * @param message The message to log.
     */
    public void warning(String message) {
        logAsync(Level.WARNING, message);
    }
    
    /**
     * Logs an ERROR (SEVERE) level message.
     *
     * @param message The message to log.
     */
    public void error(String message) {
        logAsync(Level.SEVERE, message);
    }
    
    /**
     * Shuts down the asynchronous logging executor.
     * Call this method when the application is about to exit.
     */
    public void shutdown() {
        logExecutor.shutdown();
    }
}