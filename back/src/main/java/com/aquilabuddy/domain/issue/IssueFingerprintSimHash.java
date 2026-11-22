package com.aquilabuddy.domain.issue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 1-pass ASCII 폴딩 + 64비트 FNV-1a 토큰 해시 기반 Charikar SimHash 근접 중복 판정기입니다.
 *
 * <p>정규 표현식 분할이나 중간 문자열 할당 없이 이슈 제목(가중치 2)과 요약(가중치 1)을 단일 패스로
 * 스캔해 64비트 지문을 생성하고, {@link Long#bitCount(long)} 해밍 거리로 중복 후보를 억제합니다.
 */
public final class IssueFingerprintSimHash {

    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static final int MAX_SCAN_CHARS = 8_192;
    private static final int MAX_HAMMING_DISTANCE = 4;
    private static final int MIN_TOKENS_FOR_NEAR_DEDUP = 10;
    private static final int MIN_CHARS_FOR_NEAR_DEDUP = 64;

    private IssueFingerprintSimHash() {
    }

    /**
     * 제목과 요약을 단일 패스로 토큰화해 64비트 SimHash 지문을 계산합니다.
     */
    public static long computeFingerprint(String title, String summary) {
        return compute(title, summary).fingerprint();
    }

    static Fingerprint compute(String title, String summary) {
        int[] bitWeights = new int[64];
        ScanStats titleStats = accumulateTokens(title, 2, bitWeights);
        ScanStats summaryStats = accumulateTokens(summary, 1, bitWeights);

        long fingerprint = 0L;
        for (int bit = 0; bit < 64; bit++) {
            if (bitWeights[bit] > 0) {
                fingerprint |= (1L << bit);
            }
        }
        return new Fingerprint(
            fingerprint,
            titleStats.tokenCount() + summaryStats.tokenCount(),
            titleStats.scannedChars() + summaryStats.scannedChars()
        );
    }

    /**
     * 두 64비트 SimHash 지문 사이의 해밍 거리(서로 다른 비트 수)를 반환합니다.
     */
    public static int hammingDistance(long left, long right) {
        return Long.bitCount(left ^ right);
    }

    /**
     * 동일 저장소·이슈 번호 중복은 항상 제거하고, 충분한 길이를 가진 이슈에 한해 SimHash 근접 중복을 억제합니다.
     */
    public static List<RecommendedIssue> deduplicateCandidates(List<RecommendedIssue> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (candidates.size() == 1) {
            RecommendedIssue only = candidates.getFirst();
            return only == null ? List.of() : List.of(only);
        }

        Set<RepoIssueKey> seenKeys = HashSet.newHashSet(candidates.size());
        long[] seenFingerprints = new long[candidates.size()];
        int seenFingerprintCount = 0;
        List<RecommendedIssue> deduplicated = new ArrayList<>(candidates.size());

        for (RecommendedIssue candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            RepoIssueKey key = RepoIssueKey.of(candidate);
            if (!seenKeys.add(key)) {
                continue;
            }

            Fingerprint fp = compute(candidate.title(), candidate.summary());
            if (fp.eligibleForNearDedup() && isNearDuplicate(fp.fingerprint(), seenFingerprints, seenFingerprintCount)) {
                continue;
            }
            if (fp.eligibleForNearDedup()) {
                seenFingerprints[seenFingerprintCount++] = fp.fingerprint();
            }
            deduplicated.add(candidate);
        }
        return List.copyOf(deduplicated);
    }

    private static boolean isNearDuplicate(long candidateFingerprint, long[] seen, int count) {
        for (int index = 0; index < count; index++) {
            if (Long.bitCount(candidateFingerprint ^ seen[index]) <= MAX_HAMMING_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private static ScanStats accumulateTokens(String text, int weight, int[] bitWeights) {
        if (text == null || text.isEmpty()) {
            return new ScanStats(0, 0);
        }

        int limit = Math.min(text.length(), MAX_SCAN_CHARS);
        long tokenHash = FNV_OFFSET_BASIS;
        int tokenLength = 0;
        int tokenCount = 0;
        int scannedTokenChars = 0;

        for (int index = 0; index < limit; index++) {
            char ch = foldTokenChar(text.charAt(index));
            if (ch != 0) {
                tokenHash ^= ch;
                tokenHash *= FNV_PRIME;
                tokenLength++;
            } else if (tokenLength > 0) {
                if (tokenLength >= 2) {
                    applyTokenHash(tokenHash, weight, bitWeights);
                    tokenCount++;
                    scannedTokenChars += tokenLength;
                }
                tokenHash = FNV_OFFSET_BASIS;
                tokenLength = 0;
            }
        }

        if (tokenLength >= 2) {
            applyTokenHash(tokenHash, weight, bitWeights);
            tokenCount++;
            scannedTokenChars += tokenLength;
        }
        return new ScanStats(tokenCount, scannedTokenChars);
    }

    private static void applyTokenHash(long hash, int weight, int[] bitWeights) {
        long cursor = hash;
        for (int bit = 0; bit < 64; bit++, cursor >>>= 1) {
            // 분기 없는 +weight / -weight 누적
            int bitSet = (int) (cursor & 1L);
            bitWeights[bit] += ((bitSet << 1) - 1) * weight;
        }
    }

    private static char foldTokenChar(char ch) {
        if (ch >= 'A' && ch <= 'Z') {
            return (char) (ch + ('a' - 'A'));
        }
        if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
            return ch;
        }
        if ((ch >= '\uAC00' && ch <= '\uD7A3') || (ch >= '\u3131' && ch <= '\u318E')) {
            return ch;
        }
        return 0;
    }

    record Fingerprint(long fingerprint, int tokenCount, int scannedChars) {
        boolean eligibleForNearDedup() {
            return tokenCount >= MIN_TOKENS_FOR_NEAR_DEDUP && scannedChars >= MIN_CHARS_FOR_NEAR_DEDUP;
        }
    }

    private record ScanStats(int tokenCount, int scannedChars) {
    }

    private record RepoIssueKey(String repoFullName, long issueNumber) {
        static RepoIssueKey of(RecommendedIssue issue) {
            String repo = issue.repoFullName() == null
                ? ""
                : issue.repoFullName().strip().toLowerCase(Locale.ROOT);
            return new RepoIssueKey(repo, issue.issueNumber());
        }
    }
}
