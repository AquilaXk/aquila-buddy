package com.aquilabuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Aquila Buddy 백엔드 애플리케이션의 Spring Boot 진입점입니다.
 */
@SpringBootApplication
public class AquilaBuddyApplication {

    /**
     * 내장 서버와 Spring application context를 시작합니다.
     */
    public static void main(String[] args) {
        SpringApplication.run(AquilaBuddyApplication.class, args);
    }
}
