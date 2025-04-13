package services.importer;

/**
 * Exception thrown when there is a problem with importing data.
 */
public class ImportException extends Exception {
    
    public ImportException(String message) {
        super(message);
    }
    
    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
