package com.aquilabuddy.application.port.out.user;

/**
 * [Outgoing Port] 현재 요청의 GitHub 사용자 id를 application 바깥 인증 컨텍스트에서 읽어오는 계약입니다.
 */
public interface CurrentUserGithubIdPort {

    /**
     * 현재 요청 사용자 GitHub id를 반환합니다.
     */
    long currentGithubId();
}
