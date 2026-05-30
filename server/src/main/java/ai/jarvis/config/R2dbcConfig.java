package ai.jarvis.config;

import ai.jarvis.chat.message.MessageRole;
import ai.jarvis.user.UserRole;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.core.convert.converter.Converter;

import java.util.Arrays;

@Configuration
@EnableR2dbcAuditing
@EnableR2dbcRepositories(basePackages = "ai.jarvis")
public class R2dbcConfig {
    // Spring Boot auto-configures R2DBC from application.yml
    // @EnableR2dbcAuditing → enables @CreatedDate @LastModifiedDate
    // @EnableR2dbcRepositories → scans all packages for repositories

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return R2dbcCustomConversions.of(
                PostgresDialect.INSTANCE,
                Arrays.asList(
                        new UserRoleReadConverter(),
                        new UserRoleWriteConverter(),
                        new MessageRoleReadConverter(),
                        new MessageRoleWriteConverter()
                )
        );
    }

    // ── UserRole converters ───────────────────────

    // Read: String from DB → UserRole enum
    @ReadingConverter
    public static class UserRoleReadConverter
            implements Converter<String, UserRole> {
        @Override
        public UserRole convert(String source) {
            return UserRole.valueOf(source.toUpperCase());
        }
    }

    // Write: UserRole enum → String to DB
    @WritingConverter
    public static class UserRoleWriteConverter
            implements Converter<UserRole, String> {
        @Override
        public String convert(UserRole source) {
            return source.name();
        }
    }

    // ── MessageRole converters ────────────────────

    @ReadingConverter
    public static class MessageRoleReadConverter
    implements Converter<String, MessageRole> {
        @Override
        public MessageRole convert(String source) {
            return MessageRole.valueOf(source.toUpperCase());
        }
    }

    @WritingConverter
    public static class MessageRoleWriteConverter
    implements Converter<MessageRole, String> {
        @Override
        public String convert(MessageRole source) {
            return source.name();
        }
    }
}