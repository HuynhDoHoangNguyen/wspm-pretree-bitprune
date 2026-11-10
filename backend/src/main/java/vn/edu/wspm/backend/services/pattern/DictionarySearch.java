package vn.edu.wspm.backend.services.pattern;

import java.text.Normalizer;
import java.util.Locale;

/** Literal matching: no regex, SQL wildcard or cross-field concatenation. */
public final class DictionarySearch {
    private DictionarySearch() { }
    public static String normalize(String value) {
        return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('\u0110','d').replace('\u0111','d')
                .toLowerCase(Locale.ROOT).replaceAll("(?U)\\s+"," ").strip();
    }
    public static boolean matches(String query,String name,String sku) {
        return normalize(name).contains(query) || normalize(sku).contains(query);
    }
}
