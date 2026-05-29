package ai.jarvis.security.auth;

import ai.jarvis.security.auth.request.LoginRequest;
import ai.jarvis.security.auth.request.RegisterRequest;
import ai.jarvis.security.auth.response.RegisterResponse;
import ai.jarvis.security.auth.response.TokenResponse;
import ai.jarvis.security.jwt.JwtService;
import ai.jarvis.user.User;
import ai.jarvis.user.UserMapper;
import ai.jarvis.user.UserRepository;
import ai.jarvis.user.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    // ── Register ──────────────────────────────────

    public Mono<RegisterResponse> register(
            RegisterRequest request) {

        return userRepository
                .existsByUsername(request.username())
                .flatMap(usernameTaken -> {
                    if (usernameTaken) {
                        return Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Username '" + request.username()
                                                + "' is already taken"
                                )
                        );
                    }
                    return userRepository
                            .existsByEmail(request.email());
                })
                .flatMap(emailTaken -> {
                    if (emailTaken) {
                        return Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Email is already registered"
                                )
                        );
                    }
                    return userRepository.count();
                })
                .flatMap(count -> {
                    UserRole role = count == 0
                            ? UserRole.ADMIN
                            : UserRole.USER;

                    User partial = userMapper.toEntity(request);

                    User user = User.create(
                            UUID.randomUUID(),
                            partial.username(),
                            partial.email(),
                            passwordEncoder.encode(
                                    request.password()),
                            partial.displayName(),
                            role
                    );

                    // ← USE insert() NOT save()
                    // insert() always does INSERT
                    // save() does UPDATE when ID is not null
                    return r2dbcEntityTemplate.insert(user);
                })
                .map(saved -> {
                    log.info(
                            "User registered: username={} role={}",
                            saved.username(), saved.role()
                    );
                    return userMapper.toRegisterResponse(saved);
                });
    }

    // ── Login ─────────────────────────────────────

    public Mono<TokenResponse> login(LoginRequest request) {

        return userRepository
                .findByUsername(request.username())
                .switchIfEmpty(Mono.error(
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "Invalid username or password"
                        )
                ))
                .flatMap(user -> {

                    log.debug(
                            "Found user: username={} active={} hasHash={}",
                            user.username(),
                            user.active(),
                            user.passwordHash() != null
                                    ? "YES (length="
                                      + user.passwordHash().length() + ")"
                                    : "NULL ← PROBLEM"
                    );

                    if (!user.active()) {
                        return Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Account is disabled"
                                )
                        );
                    }

                    if (!passwordEncoder.matches(
                            request.password(),
                            user.passwordHash())) {
                        log.warn("Failed login: username={}",
                                request.username());
                        return Mono.error(
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Invalid username or password"
                                )
                        );
                    }

                    log.info("User logged in: username={} role={}",
                            user.username(), user.role());

                    String accessToken =
                            jwtService.generateAccessToken(user);
                    String refreshToken =
                            jwtService.generateRefreshToken(user);

                    TokenResponse.UserInfo userInfo =
                            userMapper.toUserInfo(user);

                    return Mono.just(TokenResponse.of(
                            accessToken,
                            refreshToken,
                            jwtService.getAccessTokenExpirySeconds(),
                            userInfo
                    ));
                });
    }
}