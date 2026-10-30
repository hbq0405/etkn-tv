package xyz.etkn.tv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Merges a library's Genres and Tags facet lists into one type list; genre wins a tie. */
final class LibraryFilters {
    static final class Entry {
        final String label;
        final String kind;
        Entry(String label, String kind) { this.label = label; this.kind = kind; }
    }
    static List<Entry> mergeTypes(JSONObject facets) {
        Map<String, Entry> merged = new LinkedHashMap<>();
        for (String kind : new String[]{"Genres", "Tags"}) {
            JSONArray values = facets == null ? null : facets.optJSONArray(kind);
            if (values == null) continue;
            for (int i = 0; i < values.length(); i++) {
                String label = values.optString(i).trim();
                if (label.isEmpty()) continue;
                merged.putIfAbsent(label.toLowerCase(Locale.ROOT), new Entry(label, kind));
            }
        }
        List<Entry> result = new ArrayList<>(merged.values());
        result.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }
}
