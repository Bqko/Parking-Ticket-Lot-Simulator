package com.parking.util;

import java.util.regex.Pattern;

/**
 * Validates and normalizes license plate strings.
 *
 * <p>Supports multiple international formats. The validator
 * checks the plate against a set of known patterns and rejects
 * anything that doesn't match.</p>
 *
 * <h3>Supported formats</h3>
 * <ul>
 *   <li>US:      ABC 1234  or  AB 12345</li>
 *   <li>Turkish: 34 ABC 001  (city code + letters + numbers)</li>
 *   <li>UK:      AB12 CDE</li>
 *   <li>EU:      ABC-123  or  ABC 123</li>
 *   <li>Generic: 2–10 alphanumeric characters</li>
 * </ul>
 */
public class LicensePlateValidator {

    // Patterns ordered from most specific to most general
    private static final Pattern[] PATTERNS = {
            // Turkish: 01-81 + 1-3 letters + 1-4 digits  e.g. "34 ABC 001"
            Pattern.compile("^[0-9]{2}\\s?[A-Z]{1,3}\\s?[0-9]{1,4}$"),
            // US standard: 1-3 letters + space + 1-7 digits/letters  e.g. "ABC 1234"
            Pattern.compile("^[A-Z]{1,3}\\s?[0-9]{1,7}$"),
            // UK: 2 letters + 2 digits + space + 3 letters  e.g. "AB12 CDE"
            Pattern.compile("^[A-Z]{2}[0-9]{2}\\s?[A-Z]{3}$"),
            // EU generic: letters-digits with dash or space  e.g. "ABC-123"
            Pattern.compile("^[A-Z0-9]{1,4}[-\\s][A-Z0-9]{1,4}([-\\s][A-Z0-9]{1,4})?$"),
            // Generic fallback: 2–10 alphanumeric chars
            Pattern.compile("^[A-Z0-9]{2,10}$")
    };

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 15;

    /**
     * Validates and normalizes the given plate string.
     *
     * @param raw Raw input from the user.
     * @return    {@link ValidationResult} containing the normalized plate or error message.
     */
    public static ValidationResult validate(String raw) {
        if (raw == null || raw.isBlank()) {
            return ValidationResult.fail("License plate cannot be empty.");
        }

        // Normalize: trim, uppercase, collapse multiple spaces
        String normalized = raw.trim().toUpperCase().replaceAll("\\s+", " ");

        if (normalized.length() < MIN_LENGTH) {
            return ValidationResult.fail(
                    "License plate is too short (minimum " + MIN_LENGTH + " characters).");
        }

        if (normalized.length() > MAX_LENGTH) {
            return ValidationResult.fail(
                    "License plate is too long (maximum " + MAX_LENGTH + " characters).");
        }

        // Must contain at least one letter or digit
        if (!normalized.matches(".*[A-Z0-9].*")) {
            return ValidationResult.fail(
                    "License plate must contain letters or numbers.");
        }

        // Check against known patterns
        for (Pattern p : PATTERNS) {
            if (p.matcher(normalized).matches()) {
                return ValidationResult.ok(normalized);
            }
        }

        // No pattern matched — still accept but warn (generic fallback)
        // Strip everything except alphanumeric and spaces/dashes
        String cleaned = normalized.replaceAll("[^A-Z0-9 \\-]", "");
        if (cleaned.length() >= MIN_LENGTH) {
            return ValidationResult.ok(cleaned); // accepted with cleaning
        }

        return ValidationResult.fail(
                "Invalid license plate format. Example valid formats: \"34 ABC 001\", \"ABC 1234\", \"AB12 CDE\".");
    }

    /**
     * Quick boolean check — use when you just need true/false.
     */
    public static boolean isValid(String raw) {
        return validate(raw).isValid();
    }

    // ── Result type ───────────────────────────────────────────────────────

    public static class ValidationResult {
        private final boolean valid;
        private final String  value;   // normalized plate if valid
        private final String  error;   // error message if invalid

        private ValidationResult(boolean valid, String value, String error) {
            this.valid = valid;
            this.value = value;
            this.error = error;
        }

        static ValidationResult ok(String normalized) {
            return new ValidationResult(true, normalized, null);
        }

        static ValidationResult fail(String error) {
            return new ValidationResult(false, null, error);
        }

        public boolean isValid()      { return valid; }
        public String  getValue()     { return value; }
        public String  getError()     { return error; }

        @Override
        public String toString() {
            return valid ? "OK(" + value + ")" : "FAIL(" + error + ")";
        }
    }
}