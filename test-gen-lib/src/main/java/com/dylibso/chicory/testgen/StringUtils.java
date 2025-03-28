package com.dylibso.chicory.testgen;

public final class StringUtils {

    private StringUtils() {}

    public static String capitalize(String in) {
        if (in.isEmpty()) {
            return in;
        }
        return Character.toUpperCase(in.charAt(0)) + in.substring(1);
    }

    public static String escapedCamelCase(String in) {
        var sb = new StringBuilder();
        var capitalize = true;
        for (var i = 0; i < in.length(); i++) {
            var character = in.charAt(i);

            if (Character.isDigit(character)) {
                sb.append(character);
            } else if (Character.isLetter(character)) {
                if (capitalize) {
                    sb.append(Character.toUpperCase(character));
                    capitalize = false;
                } else {
                    sb.append(character);
                }
            } else {
                capitalize = true;
            }
        }

        return sb.toString();
    }
}
