package dev.mygh.model;

/** A pull request as returned by the REST API (only fields we use). */
public record PullRequest(
        int number,
        String title,
        String state,
        boolean draft,
        String headRef,
        String baseRef,
        String author,
        String htmlUrl,
        String body) {
}
