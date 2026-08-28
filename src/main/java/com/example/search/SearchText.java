package com.example.search;

import java.util.Locale;
import java.util.Map;

public final class SearchText {
    private SearchText() {}
    // Exactly the same transliteration as archive_normalize_title in V10.
    // Broader Java-only accent folding would make stored names and query names disagree.
    private static final String FROM = "İIıÇçĞğÖöŞşÜüÁáÀàÂâÄäÉéÈèÊêËëÍíÌìÎîÏïÓóÒòÔôÚúÙùÛûÑñ";
    private static final String TO = "iiiCcGgOoSsUuAaAaAaAaEeEeEeEeIiIiIiIiOoOoOoUuUuUuNn";
    public static String normalize(String text) {
        if (text == null) return "";
        StringBuilder translated = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int mapped = FROM.indexOf(ch);
            translated.append(mapped < 0 ? ch : TO.charAt(mapped));
        }
        return translated.toString().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    private static final Map<String, String> GENRES = Map.ofEntries(
            Map.entry("bilim kurgu", "Sci-Fi"), Map.entry("sci fi", "Sci-Fi"),
            Map.entry("science fiction", "Sci-Fi"), Map.entry("savas", "War"),
            Map.entry("war", "War"), Map.entry("aksiyon", "Action"), Map.entry("action", "Action"),
            Map.entry("macera", "Adventure"), Map.entry("adventure", "Adventure"),
            Map.entry("komedi", "Comedy"), Map.entry("comedy", "Comedy"),
            Map.entry("korku", "Horror"), Map.entry("horror", "Horror"),
            Map.entry("gerilim", "Thriller"), Map.entry("thriller", "Thriller"),
            Map.entry("dram", "Drama"), Map.entry("drama", "Drama"),
            Map.entry("animasyon", "Animation"), Map.entry("animation", "Animation"),
            Map.entry("belgesel", "Documentary"), Map.entry("documentary", "Documentary"),
            Map.entry("suc", "Crime"), Map.entry("crime", "Crime"),
            Map.entry("romantik", "Romance"), Map.entry("romance", "Romance"),
            Map.entry("gizem", "Mystery"), Map.entry("mystery", "Mystery"),
            Map.entry("fantastik", "Fantasy"), Map.entry("fantasy", "Fantasy"));
    public static String genre(String text) { return GENRES.get(normalize(text)); }
    public static String genreFilter(String text) {
        String alias = genre(text);
        return alias == null ? text : alias;
    }
}
