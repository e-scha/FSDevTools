package com.espirit.moddev.shared;

public class StringUtils {

    /**
     * Useless default constructor
     */
    public StringUtils() {
    }

    /**
     * @param string String to check
     * @return true if the String is null or empty, false otherwise
     */
    public static boolean isNullOrEmpty(final String string) {
        return string == null || isEmpty(string);
    }

    /**
     * @param string String to check
     * @return true if the String is empty, false otherwise
     */
    public static boolean isEmpty(final String string) {
        return string.trim().isEmpty();
    }
}
