package com.aquilabuddy.global.auth.refresh;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenSessionJpaRepository extends JpaRepository<RefreshTokenSessionJpaEntity, String> {

    List<RefreshTokenSessionJpaEntity> findAllByFamilyId(String familyId);
}
