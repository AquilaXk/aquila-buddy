package com.aquilabuddy.global.auth.refresh;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenSessionJpaRepository extends JpaRepository<RefreshTokenSessionJpaEntity, String> {

    List<RefreshTokenSessionJpaEntity> findAllByFamilyId(String familyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE RefreshTokenSessionJpaEntity s
        SET s.revokedAt = :revokedAt
        WHERE s.familyId = :familyId
          AND s.revokedAt IS NULL
        """)
    int revokeFamilySessions(@Param("familyId") String familyId, @Param("revokedAt") Instant revokedAt);
}
