package dev.mygh.cli;

import dev.mygh.core.Json;
import dev.mygh.model.ReviewComment;
import dev.mygh.model.ReviewThread;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Command(name = "threads", mixinStandardHelpOptions = true,
        description = "List review threads of a PR (threadId, resolved/outdated, path:line, comments).")
public final class PrThreadsCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<number>", description = "PR number")
    int number;

    @ArgGroup(exclusive = true)
    Filter filter = new Filter();

    @Option(names = "--json", description = "Output as JSON")
    boolean json;

    static final class Filter {
        @Option(names = "--all", description = "Show all threads (default)")
        boolean all;
        @Option(names = "--unresolved", description = "Show only unresolved threads")
        boolean unresolved;
    }

    @Override
    public Integer call() {
        List<ReviewThread> threads = client().listReviewThreads(repo(), number);
        if (filter.unresolved) {
            threads = threads.stream().filter(t -> !t.resolved()).toList();
        }

        if (json) {
            out().println(Json.writePretty(toJson(threads)));
            return 0;
        }

        if (threads.isEmpty()) {
            out().println(filter.unresolved ? "No unresolved review threads." : "No review threads.");
            return 0;
        }

        int idx = 0;
        for (ReviewThread t : threads) {
            idx++;
            String status = t.resolved() ? "resolved" : "unresolved";
            if (t.outdated()) {
                status += ", outdated";
            }
            out().printf("#%d  [%s]  %s%n", idx, status, t.location());
            out().printf("    threadId: %s%n", t.id());
            for (ReviewComment c : t.comments()) {
                out().printf("    - %s: %s%n", c.author(), oneLine(c.body()));
            }
            out().println();
        }
        return 0;
    }

    private static List<Map<String, Object>> toJson(List<ReviewThread> threads) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ReviewThread t : threads) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.id());
            m.put("resolved", t.resolved());
            m.put("outdated", t.outdated());
            m.put("location", t.location());
            List<Map<String, Object>> comments = new ArrayList<>();
            for (ReviewComment c : t.comments()) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("id", c.id());
                cm.put("databaseId", c.databaseId());
                cm.put("author", c.author());
                cm.put("body", c.body());
                cm.put("path", c.path());
                cm.put("line", c.line());
                cm.put("originalLine", c.originalLine());
                cm.put("createdAt", c.createdAt());
                cm.put("url", c.url());
                comments.add(cm);
            }
            m.put("comments", comments);
            list.add(m);
        }
        return list;
    }

    private static String oneLine(String body) {
        if (body == null) {
            return "";
        }
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() > 120 ? flat.substring(0, 120) + "…" : flat;
    }
}
