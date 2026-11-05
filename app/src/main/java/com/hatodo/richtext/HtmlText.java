package com.hatodo.richtext;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Rich text with the markup taken out, for indexing.
 *
 * <p>This is not {@link HtmlSanitizer}'s private tag-stripping, and the
 * difference matters. That one answers "did anything survive sanitizing", where
 * a rough answer is fine. This one produces the text a full-text index is built
 * from, where being rough is a bug: a member searching for "sugar" has to find
 * a recipe that lists it as a bullet.
 *
 * <p>Two things it must get right, and the reason it exists at all rather than
 * being a regexp inside a generated column (docs/adr/0033):
 *
 * <ul>
 *   <li>An element boundary is a <b>word</b> boundary. Replacing tags with the
 *       empty string turns {@code <li>milk</li><li>sugar</li>} into the single
 *       word "milksugar", which is one lexeme matching nothing anybody will
 *       type.
 *   <li>Entities are text. {@code caf&eacute;} indexed literally contributes
 *       the lexeme "eacute", and the word the member would search for is not in
 *       the index at all.
 * </ul>
 */
@Component
public class HtmlText {

    /** A tag, however malformed - the input has already been sanitized. */
    private static final Pattern TAG = Pattern.compile("<[^>]*>");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** A numeric character reference, decimal or hexadecimal. */
    private static final Pattern NUMERIC_ENTITY =
            Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);");

    /**
     * @return the plain text, or null when nothing is left - so "nothing to
     *         index" has one representation, as {@code sanitize} does
     */
    public String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        // A space, not the empty string. Everything downstream depends on it.
        String text = TAG.matcher(html).replaceAll(" ");
        text = decodeEntities(text);
        text = WHITESPACE.matcher(text).replaceAll(" ").trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * The handful of named entities the sanitizer's own output can contain,
     * plus any numeric reference.
     *
     * <p>Deliberately not a general HTML entity table: the input is always our
     * own sanitized output, so the set is small and closed. {@code &amp;} is
     * decoded last, or {@code &amp;lt;} would turn into a tag-looking
     * fragment on the way through.
     */
    private static String decodeEntities(String text) {
        String decoded = NUMERIC_ENTITY.matcher(text).replaceAll(match -> {
            String digits = match.group(1);
            try {
                int codePoint = digits.startsWith("x") || digits.startsWith("X")
                        ? Integer.parseInt(digits.substring(1), 16)
                        : Integer.parseInt(digits);
                return Character.isValidCodePoint(codePoint)
                        ? java.util.regex.Matcher.quoteReplacement(
                                new String(Character.toChars(codePoint)))
                        : " ";
            } catch (NumberFormatException e) {
                // A reference too long to be a code point is not text worth
                // indexing; it is also not worth failing a write over.
                return " ";
            }
        });
        return decoded
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }
}
