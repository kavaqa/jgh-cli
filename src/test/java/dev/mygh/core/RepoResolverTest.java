package dev.mygh.core;

import dev.mygh.core.RepoResolver.Repo;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoResolverTest {

    @Test
    void parsesSshRemote() {
        Optional<Repo> repo = RepoResolver.parseRemoteUrl("git@github.com:octocat/Hello-World.git");
        assertTrue(repo.isPresent());
        assertEquals("octocat", repo.get().owner());
        assertEquals("Hello-World", repo.get().name());
    }

    @Test
    void parsesSshRemoteWithoutGitSuffix() {
        Optional<Repo> repo = RepoResolver.parseRemoteUrl("git@github.com:octocat/Hello-World");
        assertTrue(repo.isPresent());
        assertEquals("octocat", repo.get().owner());
        assertEquals("Hello-World", repo.get().name());
    }

    @Test
    void parsesHttpsRemote() {
        Optional<Repo> repo = RepoResolver.parseRemoteUrl("https://github.com/octocat/Hello-World.git");
        assertTrue(repo.isPresent());
        assertEquals("octocat", repo.get().owner());
        assertEquals("Hello-World", repo.get().name());
    }

    @Test
    void parsesHttpsRemoteWithoutGitSuffix() {
        Optional<Repo> repo = RepoResolver.parseRemoteUrl("https://github.com/octocat/Hello-World");
        assertTrue(repo.isPresent());
        assertEquals("octocat", repo.get().owner());
        assertEquals("Hello-World", repo.get().name());
    }

    @Test
    void parsesGhesHttpsRemote() {
        Optional<Repo> repo = RepoResolver.parseRemoteUrl("https://ghe.example.com/team/service.git");
        assertTrue(repo.isPresent());
        assertEquals("team", repo.get().owner());
        assertEquals("service", repo.get().name());
    }

    @Test
    void parsesSshSchemeRemote() {
        Optional<Repo> repo = RepoResolver.parseRemoteUrl("ssh://git@github.com/octocat/Hello-World.git");
        assertTrue(repo.isPresent());
        assertEquals("octocat", repo.get().owner());
        assertEquals("Hello-World", repo.get().name());
    }

    @Test
    void rejectsGarbage() {
        assertTrue(RepoResolver.parseRemoteUrl("not a url").isEmpty());
        assertTrue(RepoResolver.parseRemoteUrl(null).isEmpty());
    }

    @Test
    void parsesOwnerName() {
        Optional<Repo> repo = RepoResolver.parseOwnerName("octocat/Hello-World");
        assertTrue(repo.isPresent());
        assertEquals("octocat", repo.get().owner());
        assertEquals("Hello-World", repo.get().name());
    }

    @Test
    void rejectsBadOwnerName() {
        assertTrue(RepoResolver.parseOwnerName("noslash").isEmpty());
        assertTrue(RepoResolver.parseOwnerName("a/b/c").isEmpty());
        assertTrue(RepoResolver.parseOwnerName("/leading").isEmpty());
        assertTrue(RepoResolver.parseOwnerName("trailing/").isEmpty());
    }

    @Test
    void explicitOverrideWins() {
        Config config = Config.fromEnv(name -> null);
        RepoResolver resolver = new RepoResolver(config);
        Repo repo = resolver.resolve("me/myrepo");
        assertEquals("me", repo.owner());
        assertEquals("myrepo", repo.name());
    }

    @Test
    void ghRepoEnvUsedWhenNoOverride() {
        Config config = Config.fromEnv(name -> "GH_REPO".equals(name) ? "org/proj" : null);
        RepoResolver resolver = new RepoResolver(config);
        Repo repo = resolver.resolve(null);
        assertEquals("org", repo.owner());
        assertEquals("proj", repo.name());
    }
}
