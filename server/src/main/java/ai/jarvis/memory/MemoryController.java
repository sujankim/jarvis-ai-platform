package ai.jarvis.memory;

import java.util.List;
import java.util.UUID;

import ai.jarvis.common.model.ApiResponse;
import ai.jarvis.common.model.ErrorResponse;
import ai.jarvis.common.model.ErrorResponseJsonExample;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/memories")
@SecurityRequirement(name = "Bearer Auth")
@Tag(name = "Memory", description = "Memory management")
public class MemoryController {

    public static final String CONFLICT_MESSAGE = "Memory with the specified content already exists";

    private final MemoryService memoryService;
    private final MemoryMapper memoryMapper;

    @Operation(summary = "List all user's memories")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved a list of memories"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid or no token provided",
                    content = @Content(schema = @Schema(hidden = true))
            )
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<List<MemoryResponse>>> listMemories(@Parameter(hidden = true) Mono<Authentication> authenticationMono) {
        return getUserId(authenticationMono)
                .flatMap(userId -> this.memoryService.getAll(userId).collectList())
                .map(memories -> memories.stream().map(this.memoryMapper::toResponse).toList())
                .map(ApiResponse::ok);
    }

    @Operation(summary = "Get total number of memory records")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved memory count"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid or no token provided",
                    content = @Content(schema = @Schema(hidden = true))
            )
    })
    @GetMapping(value = "/count", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<MemoryCountResponse>> count(@Parameter(hidden = true) Mono<Authentication> authenticationMono) {
        return getUserId(authenticationMono)
                .flatMap(this.memoryService::count)
                .map(MemoryCountResponse::new)
                .map(ApiResponse::ok);
    }

    @Operation(summary = "Create new memory instance")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Successfully created a memory record"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Memory request body is invalid",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ErrorResponseJsonExample.VALIDATION_ERROR_BLANK_STRING_EXAMPLE)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid or no token provided",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "Memory with the specified content already exists",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ErrorResponseJsonExample.CONFLICTING_MEMORY)
                    )
            )
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiResponse<MemoryResponse>> create(@Valid @RequestBody MemoryRequest memoryRequest, @Parameter(hidden = true) Mono<Authentication> authenticationMono) {
        return getUserId(authenticationMono)
                .flatMap(userId -> this.memoryService.saveManual(userId, memoryRequest))
                .map(this.memoryMapper::toResponse)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, CONFLICT_MESSAGE)))
                .map(ApiResponse::ok);
    }

    @Operation(summary = "Delete memory instance by id")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "Successfully removed a memory record"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid memory id",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ErrorResponseJsonExample.VALIDATION_ERROR_INVALID_MEMORY_ID)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid or no token provided",
                    content = @Content(schema = @Schema(hidden = true))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Memory was not found by given id",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ErrorResponseJsonExample.NOT_FOUND_BY_MEMORY_ID)
                    )
            )
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping(value = "/{memoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> deleteById(@PathVariable UUID memoryId, @Parameter(hidden = true) Mono<Authentication> authenticationMono) {
        return getUserId(authenticationMono)
                .flatMap(userId -> this.memoryService.delete(memoryId, userId));
    }

    @Operation(summary = "Delete all memories related to user")
    @ApiResponses( value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "Successfully removed all memory records"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Invalid or no token provided",
                    content = @Content(schema = @Schema(hidden = true))
            )
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> deleteAll(@Parameter(hidden = true) Mono<Authentication> authenticationMono) {
        return getUserId(authenticationMono)
                .flatMap(this.memoryService::deleteAll);
    }

    private Mono<UUID> getUserId(Mono<Authentication> authenticationMono) {
        return authenticationMono
                .map(Authentication::getPrincipal)
                .cast(String.class)
                .map(UUID::fromString);
    }
}
