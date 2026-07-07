package dev.mygh.cli;

import dev.mygh.core.Json;
import dev.mygh.model.PullRequest;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.LinkedHashMap;
import java.util.Map;

@Command(name = "view", mixinStandardHelpOptions = true,
        description = "View a pull request.")
public final class PrViewCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<number>", description = "PR number")
    int number;

    @Option(names = "--json", description = "Output as JSON")
    boolean json;

    @Override
    public Integer call() {
        PullRequest pr = client().viewPullRequest(repo(), number);
        if (json) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("number", pr.number());
            m.put("title", pr.title());
            m.put("state", pr.state());
            m.put("draft", pr.draft());
            m.put("headRef", pr.headRef());
            m.put("baseRef", pr.baseRef());
            m.put("author", pr.author());
            m.put("url", pr.htmlUrl());
            m.put("body", pr.body());
            out().println(Json.writePretty(m));
            return 0;
        }
        out().printf("#%d  %s%n", pr.number(), pr.title());
        out().printf("State:  %s%s%n", pr.state(), pr.draft() ? " (draft)" : "");
        out().printf("Author: %s%n", pr.author());
        out().printf("Branch: %s → %s%n", pr.headRef(), pr.baseRef());
        out().printf("URL:    %s%n", pr.htmlUrl());
        if (pr.body() != null && !pr.body().isBlank()) {
            out().println();
            out().println(pr.body());
        }
        return 0;
    }
}
