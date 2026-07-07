package dev.mygh.cli;

import dev.mygh.core.Json;
import dev.mygh.model.PullRequest;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.LinkedHashMap;
import java.util.Map;

@Command(name = "create", mixinStandardHelpOptions = true,
        description = "Create a pull request.")
public final class PrCreateCommand extends BaseCommand {

    @Option(names = "--title", required = true, description = "PR title")
    String title;

    @Option(names = "--body", description = "PR body", defaultValue = "")
    String body;

    @Option(names = "--base", required = true, description = "Base branch")
    String base;

    @Option(names = "--head", required = true, description = "Head branch")
    String head;

    @Option(names = "--draft", description = "Create as a draft PR")
    boolean draft;

    @Option(names = "--json", description = "Output the created PR as JSON")
    boolean json;

    @Override
    public Integer call() {
        PullRequest pr = client().createPullRequest(repo(), title, head, base, body, draft);
        if (json) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("number", pr.number());
            m.put("url", pr.htmlUrl());
            m.put("title", pr.title());
            m.put("state", pr.state());
            m.put("draft", pr.draft());
            out().println(Json.writePretty(m));
        } else {
            out().println(pr.htmlUrl());
        }
        return 0;
    }
}
