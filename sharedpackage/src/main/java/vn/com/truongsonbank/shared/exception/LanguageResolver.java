package vn.com.truongsonbank.shared.exception;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

class LanguageResolver {
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("vi");

    Locale resolve(HttpServletRequest request) {
        if (request == null) {
            return DEFAULT_LOCALE;
        }

        String language = request.getHeader("X-Language");
        if (language != null && !language.isBlank()) {
            return Locale.forLanguageTag(language);
        }

        Locale locale = request.getLocale();
        return locale == null ? DEFAULT_LOCALE : locale;
    }
}
