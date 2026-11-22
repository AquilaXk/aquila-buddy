package com.aquilabuddy.domain.embedding;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/**
 * embedding provider 응답과 pgvector 질의 사이에서 공유하는 벡터 값 객체입니다.
 *
 * <p>사전 계산된 역노름($1/\|\mathbf{v}\|_2$)과 4-way 루프 언롤링 {@code Math.fma} 내적 계산을 적용해
 * 핫 경로의 박싱·제곱근 연산을 제거했습니다.
 */
public final class EmbeddingVector {

    private static final int MAX_DIMENSIONS = 8_192;

    private final double[] rawValues;
    private final double inverseNorm;
    private List<Double> cachedValues;

    public EmbeddingVector(List<Double> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty.");
        }
        int size = values.size();
        if (size > MAX_DIMENSIONS) {
            throw new IllegalArgumentException("values dimension exceeds limit: " + MAX_DIMENSIONS);
        }

        double[] packed = new double[size];
        double sumOfSquares = 0.0;
        for (int index = 0; index < size; index++) {
            Double element = values.get(index);
            if (element == null || !Double.isFinite(element)) {
                throw new IllegalArgumentException("values must contain only finite numbers.");
            }
            double v = element;
            packed[index] = v;
            sumOfSquares = Math.fma(v, v, sumOfSquares);
        }
        double norm = Math.sqrt(sumOfSquares);
        if (norm == 0.0 || !Double.isFinite(norm)) {
            throw new IllegalArgumentException("values must not be a zero vector.");
        }
        this.rawValues = packed;
        this.inverseNorm = 1.0 / norm;
    }

    public EmbeddingVector(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty.");
        }
        if (values.length > MAX_DIMENSIONS) {
            throw new IllegalArgumentException("values dimension exceeds limit: " + MAX_DIMENSIONS);
        }

        double[] packed = new double[values.length];
        double sumOfSquares = 0.0;
        for (int index = 0; index < values.length; index++) {
            double v = values[index];
            if (!Double.isFinite(v)) {
                throw new IllegalArgumentException("values must contain only finite numbers.");
            }
            packed[index] = v;
            sumOfSquares = Math.fma(v, v, sumOfSquares);
        }
        double norm = Math.sqrt(sumOfSquares);
        if (norm == 0.0 || !Double.isFinite(norm)) {
            throw new IllegalArgumentException("values must not be a zero vector.");
        }
        this.rawValues = packed;
        this.inverseNorm = 1.0 / norm;
    }

    public List<Double> values() {
        List<Double> view = cachedValues;
        if (view == null) {
            view = Collections.unmodifiableList(new ImmutableDoubleArrayList(rawValues));
            cachedValues = view;
        }
        return view;
    }

    public int dimension() {
        return rawValues.length;
    }

    /**
     * 4-way 루프 언롤링과 {@link Math#fma(double, double, double)}로 두 벡터의 코사인 유사도를 계산합니다.
     */
    public double cosineSimilarity(EmbeddingVector other) {
        Objects.requireNonNull(other, "other must not be null.");
        double[] otherRaw = other.rawValues;
        int size = rawValues.length;
        if (size != otherRaw.length) {
            throw new IllegalArgumentException("Embedding vector dimension must match.");
        }

        double acc0 = 0.0;
        double acc1 = 0.0;
        double acc2 = 0.0;
        double acc3 = 0.0;
        int index = 0;
        int limit = size & ~3;
        for (; index < limit; index += 4) {
            acc0 = Math.fma(rawValues[index], otherRaw[index], acc0);
            acc1 = Math.fma(rawValues[index + 1], otherRaw[index + 1], acc1);
            acc2 = Math.fma(rawValues[index + 2], otherRaw[index + 2], acc2);
            acc3 = Math.fma(rawValues[index + 3], otherRaw[index + 3], acc3);
        }
        double dot = (acc0 + acc1) + (acc2 + acc3);
        for (; index < size; index++) {
            dot = Math.fma(rawValues[index], otherRaw[index], dot);
        }

        double cosine = dot * inverseNorm * other.inverseNorm;
        return Math.max(-1.0, Math.min(1.0, cosine));
    }

    public String toPgvectorLiteral() {
        int size = rawValues.length;
        StringBuilder builder = new StringBuilder(size * 12);
        builder.append('[');
        for (int index = 0; index < size; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(rawValues[index]);
        }
        builder.append(']');
        return builder.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EmbeddingVector that)) {
            return false;
        }
        return Arrays.equals(rawValues, that.rawValues);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(rawValues);
    }

    @Override
    public String toString() {
        return "EmbeddingVector[dimension=" + rawValues.length + "]";
    }

    private static final class ImmutableDoubleArrayList extends AbstractList<Double> implements RandomAccess {

        private final double[] backing;

        private ImmutableDoubleArrayList(double[] backing) {
            this.backing = backing;
        }

        @Override
        public Double get(int index) {
            return backing[index];
        }

        @Override
        public int size() {
            return backing.length;
        }
    }
}
