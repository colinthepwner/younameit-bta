package net.colin.younameit.item;

import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.lang.I18n;

/**
 * Memoises a generated item's display name.
 *
 * <p>{@code getTranslatedName} is called every frame for the held-item label and again for every
 * tooltip, and building the name means a translation lookup plus string concatenation. That is
 * wasted work thousands of times a second once an inventory screen full of these is open.
 *
 * <p>The cache is keyed on the current {@link net.minecraft.core.lang.Language} object, so
 * switching language in the options menu still refreshes the names rather than pinning whatever
 * was resolved first.
 */
public final class YniNameCache {

    private String value;
    private Object languageToken;

    public String get(MaterialSet set, String piece) {
        Object token = currentLanguage();
        String cached = value;
        if (cached != null && languageToken == token) return cached;

        String built = YniGear.displayName(set, piece);
        value = built;
        languageToken = token;
        return built;
    }

    private static Object currentLanguage() {
        try {
            return I18n.getInstance().getCurrentLanguage();
        } catch (Throwable ignored) {
            // Before I18n is up, fall back to a constant so the name is still cached.
            return YniNameCache.class;
        }
    }
}
