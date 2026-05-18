package com.aquilabuddy.global.ai;

import com.aquilabuddy.adapter.out.langfuse.LangfuseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI provider 설정 바인딩을 application context에 등록합니다.
 */
@Configuration
@EnableConfigurationProperties({
    AiProperties.class,
    OpenAiCompatibleProperties.class,
    VertexAiProperties.class,
    LangfuseProperties.class
})
public class AiConfig {
}
