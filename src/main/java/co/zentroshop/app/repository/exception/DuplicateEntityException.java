package co.zentroshop.app.repository.exception;

public class DuplicateEntityException extends RuntimeException {

    public DuplicateEntityException(Object id) {
        super("Entity with ID '" + id + "' already exists");
    }

    public DuplicateEntityException(Object id, String message) {
        super("Entity with ID '" + id + "' already exists: " + message);
    }

    public DuplicateEntityException(Object id, Throwable cause) {
        super("Entity with ID '" + id + "' already exists", cause);
    }

    public DuplicateEntityException(String message) {
        super(message);
    }

    public DuplicateEntityException(String message, Throwable cause) {
        super(message, cause);
    }
}