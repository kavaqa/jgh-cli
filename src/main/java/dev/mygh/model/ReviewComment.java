package dev.mygh.model;

/** A single inline review comment inside a review thread. */
public record ReviewComment(
        String id,
        Long databaseId,
        String author,
        String body,
        String path,
        Integer line,
        Integer originalLine,
        String createdAt,
        String url) {
}
