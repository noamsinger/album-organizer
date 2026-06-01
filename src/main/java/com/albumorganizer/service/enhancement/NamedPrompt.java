package com.albumorganizer.service.enhancement;

/**
 * A user-saved prompt with a short display title and the full prompt body.
 */
public record NamedPrompt(String title, String prompt) {

    /** Serialise to a single string for INI storage. */
    public String serialize() {
        return title.replace("::", "__COLON__") + "::" + prompt.replace("::", "__COLON__");
    }

    /** Deserialise from the stored string. Returns null if the string is malformed. */
    public static NamedPrompt deserialize(String raw) {
        int sep = raw.indexOf("::");
        if (sep < 0) return null;
        String t = raw.substring(0, sep).replace("__COLON__", "::");
        String p = raw.substring(sep + 2).replace("__COLON__", "::");
        if (t.isBlank() || p.isBlank()) return null;
        return new NamedPrompt(t, p);
    }

    @Override public String toString() { return title; }
}
