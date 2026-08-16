package net.colin.younameit.item;

import net.colin.younameit.set.MaterialSet;
import net.minecraft.core.lang.I18n;

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

            return YniNameCache.class;
        }
    }
}
