package ai.jarvis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    @Bean("jarvisObjectMapper")    // ← named, NOT @Primary
    public ObjectMapper jarvisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}