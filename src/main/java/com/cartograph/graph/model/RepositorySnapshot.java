package com.cartograph.graph.model;

import java.util.List;

/** Fetched source files and metadata for one immutable repository commit. */
public record RepositorySnapshot(
        String repository, String commitSha, List<SourceFile> files, List<GraphWarning> warnings, int filesSeen) {
    public RepositorySnapshot(String repository, String commitSha, List<SourceFile> files) {
        this(repository, commitSha, files, List.of(), files.size());
    }

    public RepositorySnapshot {
        files = List.copyOf(files);
        warnings = List.copyOf(warnings);
        if (filesSeen < files.size()) throw new IllegalArgumentException("filesSeen cannot be less than parsed files");
    }
}
