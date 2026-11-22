package com.aquilabuddy.domain.issue;

import com.aquilabuddy.domain.user.UserProfile;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * pgvector 코사인 순위, 단일 패스 Okapi BM25 어휘 적합도, 신선도, 커뮤니티/미할당 신호를
 * Reciprocal Rank Fusion(RRF)으로 결합해 추천 이슈 순위를 산정합니다.
 */
public final class HybridIssueRrfScorer {

    private static final double RRF_K = 60.0;
    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;
    private static final double WEIGHT_VECTOR = 1.5;
    private static final double WEIGHT_BM25 = 1.0;
    private static final double WEIGHT_FRESHNESS = 0.6;
    private static final double WEIGHT_COMMUNITY = 0.7;
    private static final double WEIGHT_BASE_SCORE = 0.5;
    private static final int MAX_QUERY_TERMS = 32;
    private static final int MAX_DOC_SCAN_CHARS = 4096;
    private static final String STAR_PREFIX = "스타수: ";
    private static final String COMMIT_PREFIX = "최근 커밋: ";

    private HybridIssueRrfScorer() {
    }

    public static List<RecommendedIssue> rank(
        List<RecommendedIssue> candidates,
        List<RecommendedIssue> vectorRankedIssues,
        UserProfile profile,
        LocalDate today
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int count = candidates.size();
        Map<String, Integer> vectorRankById = indexRanks(vectorRankedIssues);
        Map<String, Double> vectorScoreById = indexScores(vectorRankedIssues);

        List<String> queryTerms = extractQueryTerms(profile);
        double[] bm25Scores = queryTerms.isEmpty()
            ? new double[count]
            : computeBm25Scores(candidates, queryTerms);
        int[] bm25Ranks = rankDescending(bm25Scores, true);

        double[] freshnessValues = new double[count];
        double[] communityValues = new double[count];
        double[] baseScores = new double[count];
        List<String> primaryLanguages = profile == null || profile.primaryLanguages() == null
            ? List.of()
            : profile.primaryLanguages();

        for (int index = 0; index < count; index++) {
            RecommendedIssue issue = candidates.get(index);
            freshnessValues[index] = freshnessSignal(issue, today);
            communityValues[index] = communitySignal(issue, primaryLanguages);
            baseScores[index] = Math.max(0.0, Math.min(1.0, issue.score()));
        }

        int[] freshnessRanks = rankDescending(freshnessValues, false);
        int[] communityRanks = rankDescending(communityValues, true);
        int[] baseRanks = rankDescending(baseScores, true);

        double maxPossibleRrf = (vectorRankById.isEmpty() ? 0.0 : WEIGHT_VECTOR / (RRF_K + 1.0) + 0.15)
            + (queryTerms.isEmpty() ? 0.0 : WEIGHT_BM25 / (RRF_K + 1.0))
            + WEIGHT_FRESHNESS / (RRF_K + 1.0)
            + WEIGHT_COMMUNITY / (RRF_K + 1.0)
            + WEIGHT_BASE_SCORE / (RRF_K + 1.0);
        if (maxPossibleRrf <= 0.0) {
            maxPossibleRrf = 1.0;
        }

        List<ScoredIssue> scored = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            RecommendedIssue issue = candidates.get(index);
            double rrf = 0.0;
            Integer vecRank = vectorRankById.get(issue.id());
            if (vecRank != null) {
                double cosineSim = Math.max(0.0, Math.min(1.0, vectorScoreById.getOrDefault(issue.id(), 0.0)));
                rrf += WEIGHT_VECTOR / (RRF_K + vecRank) + 0.15 * cosineSim;
            }
            if (bm25Ranks[index] > 0) {
                rrf += WEIGHT_BM25 / (RRF_K + bm25Ranks[index]);
            }
            if (freshnessRanks[index] > 0) {
                rrf += WEIGHT_FRESHNESS / (RRF_K + freshnessRanks[index]);
            }
            if (communityRanks[index] > 0) {
                rrf += WEIGHT_COMMUNITY / (RRF_K + communityRanks[index]);
            }
            if (baseRanks[index] > 0) {
                rrf += WEIGHT_BASE_SCORE / (RRF_K + baseRanks[index]);
            }

            String matchedLanguage = RecommendedIssueLanguageMatcher.findMatchingLanguage(issue, primaryLanguages);
            String baseReason = issue.reason() == null ? "" : issue.reason();
            String updatedReason = matchedLanguage != null && !baseReason.contains("주 사용 언어 일치")
                ? (baseReason.isBlank()
                    ? "[주 사용 언어 일치: " + matchedLanguage + "]"
                    : baseReason + " [주 사용 언어 일치: " + matchedLanguage + "]")
                : baseReason;

            double normalizedScore = Math.round(Math.min(1.0, rrf / maxPossibleRrf) * 10000.0) / 10000.0;
            RecommendedIssue updatedIssue = new RecommendedIssue(
                issue.id(),
                issue.repoFullName(),
                issue.issueNumber(),
                issue.title(),
                issue.summary(),
                normalizedScore,
                issue.labels(),
                issue.status(),
                updatedReason,
                issue.copyOutputVersion(),
                issue.bookmarked()
            );
            scored.add(new ScoredIssue(updatedIssue, rrf, issue.issueNumber()));
        }

        scored.sort((left, right) -> {
            int scoreCompare = Double.compare(right.rawScore(), left.rawScore());
            if (scoreCompare != 0) {
                return scoreCompare;
            }
            return Long.compare(left.issueNumber(), right.issueNumber());
        });

        return scored.stream()
            .map(ScoredIssue::issue)
            .toList();
    }

    static double[] computeBm25Scores(List<RecommendedIssue> candidates, List<String> queryTerms) {
        int docCount = candidates.size();
        int termCount = queryTerms.size();
        Map<String, Integer> termIndex = HashMap.newHashMap(termCount);
        for (int t = 0; t < termCount; t++) {
            termIndex.put(queryTerms.get(t), t);
        }

        int[][] tfMatrix = new int[docCount][termCount];
        int[] docLengths = new int[docCount];
        int[] docFreq = new int[termCount];
        long totalLength = 0;

        for (int doc = 0; doc < docCount; doc++) {
            RecommendedIssue issue = candidates.get(doc);
            int[] tf = tfMatrix[doc];
            int length = accumulateTermFrequencies(issue, termIndex, tf);
            docLengths[doc] = Math.max(1, length);
            totalLength += docLengths[doc];
            for (int t = 0; t < termCount; t++) {
                if (tf[t] > 0) {
                    docFreq[t]++;
                }
            }
        }

        double avgDocLen = totalLength / (double) Math.max(1, docCount);
        double[] idf = new double[termCount];
        for (int t = 0; t < termCount; t++) {
            int df = docFreq[t];
            if (df > 0) {
                idf[t] = Math.log((docCount - df + 0.5) / (df + 0.5) + 1.0);
            }
        }

        double[] scores = new double[docCount];
        for (int doc = 0; doc < docCount; doc++) {
            double score = 0.0;
            double norm = 1.0 - BM25_B + BM25_B * (docLengths[doc] / avgDocLen);
            int[] tf = tfMatrix[doc];
            for (int t = 0; t < termCount; t++) {
                if (tf[t] <= 0 || idf[t] <= 0.0) {
                    continue;
                }
                double freq = tf[t];
                score += idf[t] * (freq * (BM25_K1 + 1.0)) / (freq + BM25_K1 * norm);
            }
            scores[doc] = score;
        }
        return scores;
    }

    private static int accumulateTermFrequencies(
        RecommendedIssue issue,
        Map<String, Integer> termIndex,
        int[] tf
    ) {
        int totalTokens = 0;
        totalTokens += scanFieldTokens(issue.repoFullName(), 2, termIndex, tf);
        totalTokens += scanFieldTokens(issue.title(), 2, termIndex, tf);
        totalTokens += scanFieldTokens(issue.summary(), 1, termIndex, tf);
        if (issue.labels() != null) {
            for (String label : issue.labels()) {
                totalTokens += scanFieldTokens(label, 2, termIndex, tf);
            }
        }
        return totalTokens;
    }

    private static int scanFieldTokens(
        String text,
        int weight,
        Map<String, Integer> termIndex,
        int[] tf
    ) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int limit = Math.min(text.length(), MAX_DOC_SCAN_CHARS);
        int tokenCount = 0;
        StringBuilder current = new StringBuilder(32);

        for (int i = 0; i < limit; i++) {
            char ch = text.charAt(i);
            if (isWordChar(ch)) {
                current.append(foldAsciiLower(ch));
            } else if (!current.isEmpty()) {
                tokenCount++;
                Integer idx = termIndex.get(current.toString());
                if (idx != null) {
                    tf[idx] += weight;
                }
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            tokenCount++;
            Integer idx = termIndex.get(current.toString());
            if (idx != null) {
                tf[idx] += weight;
            }
        }
        return tokenCount;
    }

    private static List<String> extractQueryTerms(UserProfile profile) {
        if (profile == null) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        if (profile.primaryLanguages() != null) {
            for (String lang : profile.primaryLanguages()) {
                collectNormalizedTokens(lang, terms);
            }
        }
        if (profile.topics() != null) {
            for (String topic : profile.topics()) {
                collectNormalizedTokens(topic, terms);
            }
        }
        return List.copyOf(terms);
    }

    private static void collectNormalizedTokens(String text, List<String> terms) {
        if (text == null || text.isBlank() || terms.size() >= MAX_QUERY_TERMS) {
            return;
        }
        StringBuilder current = new StringBuilder(24);
        int limit = Math.min(text.length(), 128);
        for (int i = 0; i < limit; i++) {
            char ch = text.charAt(i);
            if (isWordChar(ch)) {
                current.append(foldAsciiLower(ch));
            } else if (!current.isEmpty()) {
                addQueryTerm(current.toString(), terms);
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            addQueryTerm(current.toString(), terms);
        }
    }

    private static void addQueryTerm(String token, List<String> terms) {
        if (token.length() >= 2 && terms.size() < MAX_QUERY_TERMS && !terms.contains(token)) {
            terms.add(token);
        }
    }

    private static boolean isWordChar(char ch) {
        return (ch >= 'a' && ch <= 'z')
            || (ch >= 'A' && ch <= 'Z')
            || (ch >= '0' && ch <= '9')
            || ch == '+'
            || ch == '#';
    }

    private static char foldAsciiLower(char ch) {
        return (ch >= 'A' && ch <= 'Z') ? (char) (ch + ('a' - 'A')) : ch;
    }

    static double freshnessSignal(RecommendedIssue issue, LocalDate today) {
        String reason = issue.reason();
        if (reason == null || reason.isBlank()) {
            return 0.3;
        }
        int idx = reason.indexOf(COMMIT_PREFIX);
        if (idx < 0) {
            return 0.3;
        }
        int start = idx + COMMIT_PREFIX.length();
        if (start + 10 > reason.length()) {
            return 0.3;
        }
        String datePart = reason.substring(start, start + 10);
        try {
            LocalDate commitDate = LocalDate.parse(datePart);
            LocalDate ref = today == null ? LocalDate.now() : today;
            long days = Math.max(0L, ChronoUnit.DAYS.between(commitDate, ref));
            if (days <= 7L) {
                return 1.0;
            }
            if (days <= 30L) {
                return 0.8;
            }
            if (days <= 90L) {
                return 0.5;
            }
            return 0.2;
        } catch (DateTimeParseException ignored) {
            return 0.3;
        }
    }

    static double communitySignal(RecommendedIssue issue, List<String> primaryLanguages) {
        double signal = 0.0;
        String reason = issue.reason() == null ? "" : issue.reason();
        int stars = parseStarCount(reason);
        if (stars > 0) {
            signal += Math.min(1.0, Math.log10(1.0 + stars) / 4.5);
        }
        if (reason.contains("미할당")) {
            signal += 0.25;
        }
        if (issue.labels() != null) {
            for (String label : issue.labels()) {
                if ("good first issue".equalsIgnoreCase(label) || "help wanted".equalsIgnoreCase(label)) {
                    signal += 0.15;
                    break;
                }
            }
        }
        if (RecommendedIssueLanguageMatcher.findMatchingLanguage(issue, primaryLanguages) != null) {
            signal += 0.30;
        }
        return signal;
    }

    static int parseStarCount(String reason) {
        int idx = reason.indexOf(STAR_PREFIX);
        if (idx < 0) {
            return 0;
        }
        int start = idx + STAR_PREFIX.length();
        int end = start;
        boolean kilo = false;
        while (end < reason.length()) {
            char ch = reason.charAt(end);
            if ((ch >= '0' && ch <= '9') || ch == '.') {
                end++;
            } else if (ch == 'k' || ch == 'K') {
                kilo = true;
                break;
            } else {
                break;
            }
        }
        if (end <= start) {
            return 0;
        }
        try {
            double num = Double.parseDouble(reason.substring(start, end));
            return (int) Math.round(kilo ? num * 1000.0 : num);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Map<String, Integer> indexRanks(List<RecommendedIssue> vectorRankedIssues) {
        if (vectorRankedIssues == null || vectorRankedIssues.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> ranks = HashMap.newHashMap(vectorRankedIssues.size());
        int rank = 1;
        for (RecommendedIssue issue : vectorRankedIssues) {
            if (issue != null && issue.id() != null && !ranks.containsKey(issue.id())) {
                ranks.put(issue.id(), rank++);
            }
        }
        return ranks;
    }

    private static Map<String, Double> indexScores(List<RecommendedIssue> vectorRankedIssues) {
        if (vectorRankedIssues == null || vectorRankedIssues.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> scores = HashMap.newHashMap(vectorRankedIssues.size());
        for (RecommendedIssue issue : vectorRankedIssues) {
            if (issue != null && issue.id() != null) {
                scores.putIfAbsent(issue.id(), issue.score());
            }
        }
        return scores;
    }

    private static int[] rankDescending(double[] values, boolean positiveOnly) {
        int n = values.length;
        Integer[] indices = new Integer[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> Double.compare(values[b], values[a]));
        int[] ranks = new int[n];
        int rank = 0;
        int position = 0;
        double prevValue = Double.NaN;
        boolean hasPrev = false;
        for (int idx : indices) {
            if (positiveOnly && values[idx] <= 0.0) {
                ranks[idx] = 0;
                continue;
            }
            position++;
            if (!hasPrev || Double.compare(values[idx], prevValue) != 0) {
                rank = position;
                prevValue = values[idx];
                hasPrev = true;
            }
            ranks[idx] = rank;
        }
        return ranks;
    }

    private record ScoredIssue(RecommendedIssue issue, double rawScore, long issueNumber) {
    }
}
