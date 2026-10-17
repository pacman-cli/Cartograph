package com.cartograph.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cartograph.graph.model.RepositoryRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GitHubUrlNormalizerTest {
    private final GitHubUrlNormalizer normalizer = new GitHubUrlNormalizer();

    @ParameterizedTest(name = "[{index}] {0} -> {1}/{2} ref={3}")
    @CsvSource({
        // plain repository URLs, case and host handling
        "'https://github.com/octocat/hello-world', octocat, hello-world, -",
        "'https://github.com/octocat/Hello-World', octocat, hello-world, -",
        "'https://GITHUB.com/octocat/hello-world', octocat, hello-world, -",
        "'https://github.com/OCTOCAT/Hello-World', octocat, hello-world, -",
        // .git suffix is stripped from the repository segment
        "'https://github.com/octocat/hello-world.git', octocat, hello-world, -",
        "'https://github.com/octocat/hello-world.git/', octocat, hello-world, -",
        // a single trailing slash is tolerated
        "'https://github.com/octocat/hello-world/', octocat, hello-world, -",
        // query strings and fragments are dropped
        "'https://github.com/octocat/hello-world?tab=readme', octocat, hello-world, -",
        "'https://github.com/octocat/Hello-World/?tab=readme#content', octocat, hello-world, -",
        // /tree/{ref} URLs, including nested refs with slashes
        "'https://github.com/octocat/hello-world/tree/main', octocat, hello-world, main",
        "'https://github.com/octocat/hello-world/tree/release/v1.2', octocat, hello-world, release/v1.2",
        "'https://github.com/octocat/hello-world/tree/main/', octocat, hello-world, main",
        // percent-encoded owner segments decode
        "'https://github.com/oct%6Fcat/hello-world', octocat, hello-world, -"
    })
    void normalizesSupportedUrls(String input, String owner, String repository, String ref) {
        String expectedRef = ref.equals("-") ? null : ref;
        assertEquals(new RepositoryRef(owner, repository, expectedRef), normalizer.normalize(input));
    }

    @ParameterizedTest(name = "rejects: {0}")
    @ValueSource(
            strings = {
                // wrong scheme or host
                "http://github.com/octocat/hello-world",
                "ftp://github.com/octocat/hello-world",
                "https://www.github.com/octocat/hello-world",
                "https://gist.github.com/octocat/hello-world",
                // explicit port or user info
                "https://github.com:443/octocat/hello-world",
                "https://user@github.com/octocat/hello-world",
                // missing or extra path segments
                "https://github.com/octocat",
                "https://github.com//hello-world",
                "https://github.com/octocat/hello-world/blob/main/README.md",
                // traversal and encoded traversal
                "https://github.com/octocat/hello-world/tree/main/../../secret",
                "https://github.com/octocat/hello-world/tree/%2e%2e/secret",
                "https://github.com/octocat/hello-world/tree/main%2F..%2Fsecret",
                "https://github.com/octocat/hello-world/tree/main//x",
                // unsafe repository names
                "https://github.com/octocat/.git",
                "https://github.com/octocat/.git.git",
                // empty or malformed tree refs
                "https://github.com/octocat/hello-world/tree/"
            })
    void rejectsUnsupportedUrls(String input) {
        assertThrows(InvalidRepositoryUrlException.class, () -> normalizer.normalize(input), input);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsBlankInput(String input) {
        assertThrows(InvalidRepositoryUrlException.class, () -> normalizer.normalize(input));
    }

    @Test
    void rejectsMalformedUrls() {
        assertThrows(
                InvalidRepositoryUrlException.class,
                () -> normalizer.normalize("https://github.com/octocat/hello%2world"));
    }
}
