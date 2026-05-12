
package co.zentroshop.app.repository.exception;

public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(Object id) {
        super("Entity with ID '" + id + "' not found");
    }

    public EntityNotFoundException(Object id, String message) {
        super("Entity with ID '" + id + "' not found: " + message);
    }

    public EntityNotFoundException(Object id, Throwable cause) {
        super("Entity with ID '" + id + "' not found", cause);
    }

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}