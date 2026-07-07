package dev.mygh.cli;

import dev.mygh.core.Json;
import dev.mygh.model.PullRequest;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Command(name = "list", mixinStandardHelpOptions = true,
        description = "List pull requests.")
public final class PrListCommand extends BaseCommand {

    @Option(names = "--state", defaultValue = "open",
            description = "Filter by state: ${COMPLETION-CANDIDATES}")
    State state;

    @Option(names = "--limit", defaultValue = "30", description = "Maximum number of PRs")
    int limit;

    @Option(names = "--json", description = "Output as JSON")
    boolean json;

    enum State {open, closed, all}

    @Override
    public Integer call() {
        List<PullRequest> prs = client().listPullRequests(repo(), state.name(), limit);
        if (json) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (PullRequest pr : prs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("number", pr.number());
                m.put("title", pr.title());
                m.put("state", pr.state());
                m.put("draft", pr.draft());
                m.put("headRef", pr.headRef());
                m.put("baseRef", pr.baseRef());
                m.put("author", pr.author());
                m.put("url", pr.htmlUrl());
                list.add(m);
            }
            out().println(Json.writePretty(list));
            return 0;
        }
        if (prs.isEmpty()) {
            out().println("No pull requests found.");
            return 0;
        }
        for (PullRequest pr : prs) {
            out().printf("#%-5d  %-8s  %s  (%s → %s)%n",
                    pr.number(),
                    pr.draft() ? "draft" : pr.state(),
                    pr.title(),
                    pr.headRef(),
                    pr.baseRef());
        }
        return 0;
    }
}
