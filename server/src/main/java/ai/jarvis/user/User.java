package ai.jarvis.user;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("users")
public record User(

        @Id
        UUID id,

        String username,

        String email,

        @Column("password_hash")
        String passwordHash,

        @Column("display_name")
        String displayName,

        UserRole role,

        @Column("is_active")
        boolean active,

        @CreatedDate
        @Column("created_at")
        Instant createdAt,

        @LastModifiedDate
        @Column("updated_at")
        Instant updatedAt,

        @Column("last_login_at")
        Instant lastLoginAt

) {
        // Convenience factory — creates a new user with defaults
        public static User create(
                UUID id,
                String username,
                String email,
                String passwordHash,
                String displayName,
                UserRole role
        ) {
                return new User(
                        id,
                        username,
                        email,
                        passwordHash,
                        displayName,
                        role,
                        true,
                        Instant.now(),
                        Instant.now(),
                        null
                );
        }

}