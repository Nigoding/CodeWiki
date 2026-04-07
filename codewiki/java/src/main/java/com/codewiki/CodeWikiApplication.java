package com.codewiki;

import com.codewiki.config.AgentConcurrencyProperties;
import com.codewiki.config.AgentProperties;
import com.codewiki.config.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
        AgentProperties.class,
        LlmProperties.class,
        AgentConcurrencyProperties.class
})
public class CodeWikiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeWikiApplication.class, args);
    }
}
