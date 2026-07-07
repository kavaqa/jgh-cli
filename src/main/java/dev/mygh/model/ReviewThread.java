package dev.mygh.model;

import java.util.List;

/** A review thread (GraphQL node) grouping inline comments with a resolved status. */
public record ReviewThread(
        String id,
        boolean resolved,
        boolean outdated,
        List<ReviewComment> comments) {

    /** Location "path:line" derived from the first comment, or "-" if unknown. */
    public String location() {
        if (comments.isEmpty()) {
            return "-";
        }
        ReviewComment first = comments.get(0);
        if (first.path() == null) {
            return "-";
        }
        Integer line = first.line() != null ? first.line() : first.originalLine();
        return line != null ? first.path() + ":" + line : first.path();
    }
}
