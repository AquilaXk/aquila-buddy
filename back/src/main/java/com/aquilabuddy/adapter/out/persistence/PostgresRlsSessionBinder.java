package com.aquilabuddy.adapter.out.persistence;

import java.sql.Connection;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL 트랜잭션에 현재 인증 사용자 ID를 세션 GUC(`app.current_user_id`)로 바인딩합니다.
 */
@Component
public class PostgresRlsSessionBinder {

    private static final String SET_CONFIG_SQL = "SELECT set_config('app.current_user_id', ?, true)";

    private final JdbcTemplate jdbcTemplate;
    private volatile Boolean postgresDatabase;

    @Autowired
    public PostgresRlsSessionBinder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null.");
    }

    PostgresRlsSessionBinder(JdbcTemplate jdbcTemplate, boolean postgresDatabase) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null.");
        this.postgresDatabase = postgresDatabase;
    }

    public void bindCurrentUser(long userGithubId) {
        if (userGithubId <= 0) {
            throw new IllegalArgumentException("userGithubId must be positive.");
        }
        if (!isPostgresDatabase()) {
            return;
        }
        jdbcTemplate.queryForObject(SET_CONFIG_SQL, String.class, String.valueOf(userGithubId));
    }

    private boolean isPostgresDatabase() {
        Boolean cached = postgresDatabase;
        if (cached != null) {
            return cached;
        }
        boolean detected = Boolean.TRUE.equals(
            jdbcTemplate.execute((Connection connection) ->
                "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()))
        );
        postgresDatabase = detected;
        return detected;
    }
}
