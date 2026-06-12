package com.albumorganizer.service.enhancement;

/**
 * A prompt with a short display title and the full prompt body.
 * id < 0 → built-in default (cannot be deleted).
 * id > 0 → user-created.
 */
public record NamedPrompt(int id, String title, String prompt) {

    public boolean isDefault() { return id < 0; }

    @Override public String toString() { return title; }
}
