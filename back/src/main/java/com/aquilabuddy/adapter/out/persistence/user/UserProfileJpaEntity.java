package com.aquilabuddy.adapter.out.persistence.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자 프로필과 추천 컨텍스트 목록을 보관하는 JPA 전용 모델입니다.
 */
@Entity
@Table(name = "user_profiles")
public class UserProfileJpaEntity {

    @Id
    @Column(name = "github_id", nullable = false)
    private Long githubId;

    @Column(name = "login", nullable = false, unique = true)
    private String login;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false)
    private String email;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_profile_primary_languages",
        joinColumns = @JoinColumn(name = "user_github_id")
    )
    @OrderColumn(name = "language_order")
    @Column(name = "language", nullable = false)
    private List<String> primaryLanguages = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_profile_topics",
        joinColumns = @JoinColumn(name = "user_github_id")
    )
    @OrderColumn(name = "topic_order")
    @Column(name = "topic", nullable = false)
    private List<String> topics = new ArrayList<>();

    @Column(name = "profile_ready", nullable = false)
    private boolean profileReady;

    @Column(name = "vector_updated_at", nullable = false, length = 40)
    private String vectorUpdatedAt;

    @Column(name = "profile_embedding", length = 12_000)
    private String profileEmbedding;

    protected UserProfileJpaEntity() {
    }

    UserProfileJpaEntity(
        Long githubId,
        String login,
        String name,
        String email,
        List<String> primaryLanguages,
        List<String> topics,
        boolean profileReady,
        String vectorUpdatedAt
    ) {
        this.githubId = githubId;
        this.login = login;
        this.name = name;
        this.email = email;
        this.primaryLanguages = new ArrayList<>(primaryLanguages);
        this.topics = new ArrayList<>(topics);
        this.profileReady = profileReady;
        this.vectorUpdatedAt = vectorUpdatedAt;
    }

    Long getGithubId() {
        return githubId;
    }

    String getLogin() {
        return login;
    }

    String getName() {
        return name;
    }

    String getEmail() {
        return email;
    }

    List<String> getPrimaryLanguages() {
        return List.copyOf(primaryLanguages);
    }

    List<String> getTopics() {
        return List.copyOf(topics);
    }

    boolean isProfileReady() {
        return profileReady;
    }

    String getVectorUpdatedAt() {
        return vectorUpdatedAt;
    }

    String getProfileEmbedding() {
        return profileEmbedding;
    }

    void updateProfile(String name, String email, List<String> primaryLanguages, List<String> topics) {
        this.name = name;
        this.email = email;
        this.primaryLanguages = new ArrayList<>(primaryLanguages);
        this.topics = new ArrayList<>(topics);
    }

    void updateIdentity(String login, String name, String email) {
        this.login = login;
        this.name = name;
        this.email = email;
    }

    void refreshVector(String vectorUpdatedAt) {
        this.profileReady = true;
        this.vectorUpdatedAt = vectorUpdatedAt;
    }

    void refreshVector(String vectorUpdatedAt, List<String> primaryLanguages, List<String> topics) {
        refreshVector(vectorUpdatedAt);
        this.primaryLanguages = new ArrayList<>(primaryLanguages);
        this.topics = new ArrayList<>(topics);
    }

    void updateProfileEmbedding(String profileEmbedding) {
        this.profileEmbedding = profileEmbedding;
    }
}
