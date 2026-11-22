package com.aquilabuddy.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgresRlsSessionBinderTest {

    @Test
    void bindsTransactionLocalUserSettingOnPostgresDatabase() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PostgresRlsSessionBinder binder = new PostgresRlsSessionBinder(jdbcTemplate, true);

        binder.bindCurrentUser(10001L);

        verify(jdbcTemplate).queryForObject(
            "SELECT set_config('app.current_user_id', ?, true)",
            String.class,
            "10001"
        );
    }

    @Test
    void skipsSetConfigOnNonPostgresDatabases() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PostgresRlsSessionBinder binder = new PostgresRlsSessionBinder(jdbcTemplate, false);

        binder.bindCurrentUser(10001L);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsNonPositiveUserIdFailClosed() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PostgresRlsSessionBinder binder = new PostgresRlsSessionBinder(jdbcTemplate, true);

        assertThatThrownBy(() -> binder.bindCurrentUser(0L))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> binder.bindCurrentUser(-5L))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
