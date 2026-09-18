package fr.armand.backend.service;

import fr.armand.backend.exception.BusinessException;
import static fr.armand.backend.exception.BusinessException.Code.INVALID_INPUT;

final class InputValidation {
    private InputValidation() {
    }

    static String text(String value, String field, int minimum, int maximum) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isBlank() || cleaned.length() < minimum || cleaned.length() > maximum) {
            throw new BusinessException(INVALID_INPUT,
                    field + " doit contenir entre " + minimum + " et " + maximum + " caractères.");
        }
        return cleaned;
    }

    static void id(Long value, String field) {
        if (value == null || value <= 0) {
            throw new BusinessException(INVALID_INPUT, field + " doit être un identifiant positif.");
        }
    }
}
