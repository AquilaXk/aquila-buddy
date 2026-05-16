package com.aquilabuddy.adapter.out.persistence.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 프로필 persistence adapter가 사용하는 Spring Data repository입니다.
 */
public interface UserProfileJpaRepository extends JpaRepository<UserProfileJpaEntity, Long> {

    /**
     * GitHub login이 일치하는 공개 프로필을 조회합니다.
     */
    Optional<UserProfileJpaEntity> findByLogin(String login);
}
