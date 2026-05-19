package at.koopro.wizardsandbeasts.util;

public final class TextUtils {

    private TextUtils() {}

    public static String abbreviate(String text, int maxLength) {
        if (text == null || maxLength <= 0) {
            return "";
        }
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
