package cl.camodev.utiles.number;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for converting substrings matched by a regular expression into {@link Integer} values.
 */
public final class NumberConverters {

    private NumberConverters() {}

    /**
     * Extracts an integer from the provided input using the supplied regular expression pattern.
     * The pattern is expected to contain at least one capturing group that corresponds to the integer value.
     * If the input does not match the pattern or if the captured group cannot be parsed into an integer,
     * this method returns {@code null}.
     *
     * @param input   the string to extract an integer from, may be null
     * @param pattern the compiled regular expression pattern with at least one capturing group, must not be null
     * @return an {@link Integer} containing the parsed value, or {@code null} if parsing is not possible
     */
    public static Integer regexToInt(String input, Pattern pattern) {
        Objects.requireNonNull(pattern, "pattern");
        if (input == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(input.trim());
        if (!matcher.matches()) {
            return null;
        }
        // Use first capturing group if available; otherwise use the entire match
        String numberStr = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
        try {
            return Integer.parseInt(numberStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extracts the first number from a fraction format "n/n" (e.g., "3/10" returns 3).
     * Supports various formats like "3/10", "5 / 7", "12/100", etc.
     *
     * @param input the string containing a fraction format, may be null
     * @return the first number (numerator) or {@code null} if parsing fails
     */
    public static Integer fractionToFirstInt(String input) {
        if (input == null) {
            return null;
        }
        Pattern pattern = Pattern.compile(".*?(\\d+)\\s*/\\s*\\d+.*");
        Matcher matcher = pattern.matcher(input.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extracts the second number from a fraction format "n/n" (e.g., "3/10" returns 10).
     * Supports various formats like "3/10", "5 / 7", "12/100", etc.
     *
     * @param input the string containing a fraction format, may be null
     * @return the second number (denominator) or {@code null} if parsing fails
     */
    public static Integer fractionToSecondInt(String input) {
        if (input == null) {
            return null;
        }
        Pattern pattern = Pattern.compile(".*?\\d+\\s*/\\s*(\\d+).*");
        Matcher matcher = pattern.matcher(input.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Extracts a specific number from a fraction format "n/n" based on the position.
     *
     * @param input the string containing a fraction format, may be null
     * @param position the position to extract: 1 for first number (numerator), 2 for second number (denominator)
     * @return the requested number or {@code null} if parsing fails or position is invalid
     */
    public static Integer fractionToInt(String input, int position) {
        if (position == 1) {
            return fractionToFirstInt(input);
        } else if (position == 2) {
            return fractionToSecondInt(input);
        }
        return null;
    }
}