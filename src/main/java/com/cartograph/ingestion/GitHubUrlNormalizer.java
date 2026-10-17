package com.cartograph.ingestion;

import com.cartograph.graph.model.RepositoryRef;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Converts a public GitHub repository URL into the graph model's repository reference. */
public final class GitHubUrlNormalizer {
    public RepositoryRef normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRepositoryUrlException("Repository URL is required");
        }
        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new InvalidRepositoryUrlException("Malformed repository URL", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"github.com".equalsIgnoreCase(uri.getHost())
                || uri.getPort() != -1
                || uri.getUserInfo() != null) {
            throw new InvalidRepositoryUrlException("URL must use HTTPS and github.com");
        }

        String rawPath = uri.getRawPath();
        if (rawPath == null || !rawPath.startsWith("/") || rawPath.contains("\\")) {
            throw new InvalidRepositoryUrlException("URL does not contain a repository path");
        }
        String[] segments = rawPath.substring(1).split("/", -1);
        int trailingEmptySegments = 0;
        while (trailingEmptySegments < segments.length
                && segments[segments.length - 1 - trailingEmptySegments].isEmpty()) {
            trailingEmptySegments++;
        }
        if (trailingEmptySegments > 1) {
            throw new InvalidRepositoryUrlException("URL contains multiple trailing slashes");
        }
        if (trailingEmptySegments == 1) {
            segments = java.util.Arrays.copyOf(segments, segments.length - 1);
        }
        if (segments.length < 2 || segments[0].isEmpty() || segments[1].isEmpty()) {
            throw new InvalidRepositoryUrlException("URL must contain owner and repository");
        }
        String owner = decodeSegment(segments[0]);
        String repository = decodeSegment(segments[1]);
        if (segments.length == 2) {
            return new RepositoryRef(normalizeName(owner), normalizeRepository(repository), null);
        }
        if (segments.length < 4 || !"tree".equalsIgnoreCase(decodeSegment(segments[2]))) {
            throw new InvalidRepositoryUrlException("Only repository and /tree/{ref} URLs are supported");
        }
        StringBuilder rawRef = new StringBuilder(segments[3]);
        for (int index = 4; index < segments.length; index++) {
            rawRef.append('/').append(segments[index]);
        }
        String ref = decode(rawRef.toString());
        if (ref.isBlank() || hasTraversalSegment(ref)) {
            throw new InvalidRepositoryUrlException("Repository ref is unsafe");
        }
        return new RepositoryRef(normalizeName(owner), normalizeRepository(repository), ref);
    }

    private static String normalizeName(String value) {
        if (value.isBlank()
                || hasTraversalSegment(value)
                || value.indexOf('/') >= 0
                || !value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new InvalidRepositoryUrlException("Repository name is unsafe");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String normalizeRepository(String value) {
        String normalized = normalizeName(value);
        normalized = normalized.endsWith(".git") ? normalized.substring(0, normalized.length() - 4) : normalized;
        if (normalized.isBlank() || normalized.equals(".") || normalized.equals(".git"))
            throw new InvalidRepositoryUrlException("Repository name is unsafe");
        return normalized;
    }

    private static boolean hasTraversalSegment(String value) {
        for (String segment : value.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..") || segment.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static String decodeSegment(String value) {
        String decoded = decode(value);
        if (decoded.indexOf('/') >= 0 || decoded.indexOf('\\') >= 0) {
            throw new InvalidRepositoryUrlException("Encoded path separator is not allowed here");
        }
        return decoded;
    }

    private static String decode(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '%') {
                result.append(character);
                continue;
            }
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            while (index < value.length() && value.charAt(index) == '%') {
                if (index + 2 >= value.length()) {
                    throw new InvalidRepositoryUrlException("Malformed URL escape");
                }
                int high = Character.digit(value.charAt(index + 1), 16);
                int low = Character.digit(value.charAt(index + 2), 16);
                if (high < 0 || low < 0) throw new InvalidRepositoryUrlException("Malformed URL escape");
                bytes.write((high << 4) | low);
                index += 3;
            }
            result.append(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            index--;
        }
        return result.toString();
    }
}
