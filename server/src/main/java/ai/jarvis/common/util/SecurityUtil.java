package ai.jarvis.common.util;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

public class SecurityUtil {

    public static Mono<UUID> getUserId(Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(Authentication::getPrincipal)
                .cast(String.class)
                .map(UUID::fromString)
                .onErrorMap(IllegalArgumentException.class,
                        ex -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject", ex));
    }
}
