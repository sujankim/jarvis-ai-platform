package ai.jarvis.config;

import ai.jarvis.agents.AgentStatus;
import ai.jarvis.agents.AgentStepStatus;
import ai.jarvis.agents.AgentStepType;
import ai.jarvis.chat.message.MessageRole;
import ai.jarvis.memory.MemoryType;
import ai.jarvis.rag.DocumentFileType;
import ai.jarvis.rag.DocumentStatus;
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
                        new MessageRoleWriteConverter(),
                        new DocumentStatusReadConverter(),
                        new DocumentStatusWriteConverter(),
                        new DocumentFileTypeReadConverter(),
                        new DocumentFileTypeWriteConverter()
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

    // ── MemoryType converters ──────────────────────────

    @ReadingConverter
    public static class MemoryTypeReadConverter
            implements Converter<String, MemoryType> {
        @Override
        public MemoryType convert(String source) {
            return MemoryType.valueOf(
                    source.toUpperCase());
        }
    }

    @WritingConverter
    public static class MemoryTypeWriteConverter
            implements Converter<MemoryType, String> {
        @Override
        public String convert(MemoryType source) {
            return source.name();
        }
    }

    // ── DocumentType converters ──────────────────────────

    @ReadingConverter
    public static class DocumentStatusReadConverter
            implements Converter<String, DocumentStatus> {
        @Override
        public DocumentStatus convert(String source) {
            return DocumentStatus.valueOf(
                    source.toUpperCase());
        }
    }

    @WritingConverter
    public static class DocumentStatusWriteConverter
            implements Converter<DocumentStatus, String> {
        @Override
        public String convert(DocumentStatus source) {
            return source.name();
        }
    }

    @ReadingConverter
    public static class DocumentFileTypeReadConverter
            implements Converter<String, DocumentFileType> {
        @Override
        public DocumentFileType convert(String source) {
            return DocumentFileType.valueOf(
                    source.toUpperCase());
        }
    }

    @WritingConverter
    public static class DocumentFileTypeWriteConverter
            implements Converter<DocumentFileType, String> {
        @Override
        public String convert(DocumentFileType source) {
            return source.name();
        }
    }

    // ── AgentStatus (Phase 6 NEW) ─────────────────

    /**
     * WHY needed:
     * DB stores VARCHAR "PENDING", "RUNNING" etc.
     * R2DBC cannot auto-convert to AgentStatus enum.
     * This converter does it explicitly.
     */
    @ReadingConverter
    public static class AgentStatusReadConverter
            implements Converter<String, AgentStatus> {
        @Override
        public AgentStatus convert(String source) {
            return AgentStatus.valueOf(source.toUpperCase());
        }
    }

    @WritingConverter
    public static class AgentStatusWriteConverter
            implements Converter<AgentStatus, String> {
        @Override
        public String convert(AgentStatus source) {
            return source.name();
        }
    }

    // ── AgentStepType (Phase 6 NEW) ───────────────

    @ReadingConverter
    public static class AgentStepTypeReadConverter
            implements Converter<String, AgentStepType> {
        @Override
        public AgentStepType convert(String source) {
            return AgentStepType.valueOf(source.toUpperCase());
        }
    }

    @WritingConverter
    public static class AgentStepTypeWriteConverter
            implements Converter<AgentStepType, String> {
        @Override
        public String convert(AgentStepType source) {
            return source.name();
        }
    }

    // ── AgentStepStatus (Phase 6 NEW) ────────────

    @ReadingConverter
    public static class AgentStepStatusReadConverter
            implements Converter<String, AgentStepStatus> {
        @Override
        public AgentStepStatus convert(String source) {
            return AgentStepStatus.valueOf(source.toUpperCase());
        }
    }

    @WritingConverter
    public static class AgentStepStatusWriteConverter
            implements Converter<AgentStepStatus, String> {
        @Override
        public String convert(AgentStepStatus source) {
            return source.name();
        }
    }
}
