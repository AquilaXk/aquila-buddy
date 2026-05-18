package com.aquilabuddy.domain.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * 이슈 가이드 생성 전에 함께 전달할 저장소 코드 맥락입니다.
 */
public record RepositoryCodeContext(
    String repository,
    String branch,
    List<RepositoryCodeContextFile> files
) {

    private static final RepositoryCodeContext EMPTY = new RepositoryCodeContext("", "", List.of());

    public RepositoryCodeContext {
        repository = repository == null ? "" : repository.strip();
        branch = branch == null ? "" : branch.strip();
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static RepositoryCodeContext empty() {
        return EMPTY;
    }

    public static RepositoryCodeContext fromFiles(
        String repository,
        String branch,
        List<RepositoryCodeContextFile> files,
        int maxFiles,
        int maxContentChars
    ) {
        if (files == null || files.isEmpty() || maxFiles <= 0) {
            return empty();
        }

        List<RepositoryCodeContextFile> limitedFiles = files.stream()
            .limit(maxFiles)
            .map(file -> file.truncateTo(maxContentChars))
            .toList();
        return new RepositoryCodeContext(repository, branch, limitedFiles);
    }

    public boolean hasFiles() {
        return !files.isEmpty();
    }

    public List<String> promptSummaryLines() {
        if (!hasFiles()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        lines.add("Repository code context: " + repository + "@" + branch);
        for (RepositoryCodeContextFile file : files) {
            lines.add("File: " + file.path());
            lines.add("Content:");
            lines.add(file.content());
            if (file.truncated()) {
                lines.add("Truncated: true");
            }
        }
        return List.copyOf(lines);
    }
}
