package dev.mygh.cli;

import dev.mygh.Main;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates --body / --body-file argument handling on the paths that fail
 * before any network call (usage errors -> exit 2).
 */
class BodyInputTest {

    private static int run(String... args) {
        return new CommandLine(new Main()).execute(args);
    }

    @Test
    void commentWithoutBodyIsUsageError() {
        assertEquals(2, run("pr", "comment", "5", "--repo", "octocat/hello"));
    }

    @Test
    void replyWithoutBodyIsUsageError() {
        assertEquals(2, run("pr", "reply", "5", "--repo", "octocat/hello", "--thread", "T1"));
    }

    @Test
    void bodyAndBodyFileTogetherIsUsageError() {
        assertEquals(2, run("pr", "comment", "5", "--repo", "octocat/hello",
                "--body", "x", "--body-file", "y.txt"));
    }
}
