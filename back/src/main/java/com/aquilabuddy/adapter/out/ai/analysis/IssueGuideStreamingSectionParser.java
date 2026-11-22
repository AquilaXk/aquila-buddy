package com.aquilabuddy.adapter.out.ai.analysis;

import com.aquilabuddy.domain.analysis.IssueGuideStreamChunk;
import java.util.function.Consumer;

/**
 * LLM 토큰 스트림에서 GUIDELINE / PSEUDO_CODE / SIDE_EFFECTS 섹션 경계를
 * 마커 prefix holdback 방식으로 실시간 분리해 SSE 청크로 전달합니다.
 */
final class IssueGuideStreamingSectionParser {

    private static final String GUIDELINE_MARKER = "GUIDELINE:";
    private static final String PSEUDO_CODE_MARKER = "PSEUDO_CODE:";
    private static final String SIDE_EFFECTS_MARKER = "SIDE_EFFECTS:";

    private enum Section {
        NONE,
        GUIDELINE,
        PSEUDO_CODE,
        SIDE_EFFECTS
    }

    private final Consumer<IssueGuideStreamChunk> consumer;
    private final StringBuilder pending = new StringBuilder();
    private Section section = Section.NONE;
    private boolean trimLeadingWhitespace;

    IssueGuideStreamingSectionParser(Consumer<IssueGuideStreamChunk> consumer) {
        this.consumer = consumer;
    }

    void accept(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        pending.append(delta);
        drain(false);
    }

    void finish() {
        drain(true);
    }

    private void drain(boolean endOfStream) {
        while (!pending.isEmpty()) {
            if (trimLeadingWhitespace) {
                consumeLeadingWhitespace();
                if (pending.isEmpty()) {
                    return;
                }
            }

            switch (section) {
                case NONE -> {
                    int idx = pending.indexOf(GUIDELINE_MARKER);
                    if (idx < 0) {
                        if (endOfStream) {
                            pending.setLength(0);
                            return;
                        }
                        trimPrefixKeepSuffix(longestMarkerPrefixSuffix(pending, GUIDELINE_MARKER));
                        return;
                    }
                    pending.delete(0, idx + GUIDELINE_MARKER.length());
                    section = Section.GUIDELINE;
                    trimLeadingWhitespace = true;
                }
                case GUIDELINE -> {
                    int idx = pending.indexOf(PSEUDO_CODE_MARKER);
                    if (idx >= 0) {
                        emitRange(Section.GUIDELINE, 0, trimTrailingNewlines(pending, idx));
                        pending.delete(0, idx + PSEUDO_CODE_MARKER.length());
                        section = Section.PSEUDO_CODE;
                        trimLeadingWhitespace = true;
                        continue;
                    }
                    int holdback = endOfStream ? 0 : holdbackWithTrailingNewlines(pending, PSEUDO_CODE_MARKER);
                    int flushLength = pending.length() - holdback;
                    if (flushLength <= 0) {
                        return;
                    }
                    emitRange(Section.GUIDELINE, 0, flushLength);
                    pending.delete(0, flushLength);
                    if (holdback > 0) {
                        return;
                    }
                }
                case PSEUDO_CODE -> {
                    int idx = pending.indexOf(SIDE_EFFECTS_MARKER);
                    if (idx >= 0) {
                        emitRange(Section.PSEUDO_CODE, 0, trimTrailingNewlines(pending, idx));
                        pending.delete(0, idx + SIDE_EFFECTS_MARKER.length());
                        section = Section.SIDE_EFFECTS;
                        trimLeadingWhitespace = true;
                        continue;
                    }
                    int holdback = endOfStream ? 0 : holdbackWithTrailingNewlines(pending, SIDE_EFFECTS_MARKER);
                    int flushLength = pending.length() - holdback;
                    if (flushLength <= 0) {
                        return;
                    }
                    emitRange(Section.PSEUDO_CODE, 0, flushLength);
                    pending.delete(0, flushLength);
                    if (holdback > 0) {
                        return;
                    }
                }
                case SIDE_EFFECTS -> {
                    String chunk = pending.toString();
                    pending.setLength(0);
                    if (!chunk.isEmpty()) {
                        consumer.accept(IssueGuideStreamChunk.sideEffectsChunk(chunk));
                    }
                    return;
                }
            }
        }
    }

    private void emitRange(Section targetSection, int start, int end) {
        if (end <= start) {
            return;
        }
        String text = pending.substring(start, end);
        if (text.isEmpty()) {
            return;
        }
        if (targetSection == Section.GUIDELINE) {
            consumer.accept(IssueGuideStreamChunk.guideline(text));
        } else if (targetSection == Section.PSEUDO_CODE) {
            consumer.accept(IssueGuideStreamChunk.pseudoCode(text));
        }
    }

    private void consumeLeadingWhitespace() {
        int index = 0;
        int length = pending.length();
        while (index < length) {
            char ch = pending.charAt(index);
            if (ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n') {
                index++;
            } else {
                trimLeadingWhitespace = false;
                break;
            }
        }
        if (index > 0) {
            pending.delete(0, index);
        }
    }

    private void trimPrefixKeepSuffix(int keepLength) {
        int removeCount = pending.length() - keepLength;
        if (removeCount > 0) {
            pending.delete(0, removeCount);
        }
    }

    private static int trimTrailingNewlines(CharSequence buffer, int end) {
        int cursor = end;
        while (cursor > 0) {
            char ch = buffer.charAt(cursor - 1);
            if (ch == '\r' || ch == '\n') {
                cursor--;
            } else {
                break;
            }
        }
        return cursor;
    }

    private static int holdbackWithTrailingNewlines(CharSequence buffer, String marker) {
        int prefixHoldback = longestMarkerPrefixSuffix(buffer, marker);
        int cursor = buffer.length() - prefixHoldback;
        while (cursor > 0) {
            char ch = buffer.charAt(cursor - 1);
            if (ch == '\r' || ch == '\n') {
                cursor--;
            } else {
                break;
            }
        }
        return buffer.length() - cursor;
    }

    private static int longestMarkerPrefixSuffix(CharSequence buffer, String marker) {
        int maxPossible = Math.min(buffer.length(), marker.length() - 1);
        for (int len = maxPossible; len >= 1; len--) {
            boolean match = true;
            int offset = buffer.length() - len;
            for (int i = 0; i < len; i++) {
                if (buffer.charAt(offset + i) != marker.charAt(i)) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return len;
            }
        }
        return 0;
    }
}
