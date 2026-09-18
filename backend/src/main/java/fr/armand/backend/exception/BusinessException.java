package fr.armand.backend.exception;

public class BusinessException extends RuntimeException {
    // Le futur contrôleur traduira ces codes en réponses HTTP 400, 401, 403, 404 et 409.
    public enum Code { INVALID_INPUT, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, CONFLICT }

    private final Code code;

    public BusinessException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
