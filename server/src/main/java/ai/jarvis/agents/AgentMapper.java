package ai.jarvis.agents;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for Agent entities.
 *
 * Converts Agent + AgentStep to their
 * response DTOs for REST API output.
 *
 * toResponse(Agent):
 * Produces AgentResponse with steps=null.
 * Used for list endpoint — no step loading needed.
 * steps must be explicitly ignored because
 * AgentResponse has a steps field but Agent does not.
 *
 * toStepResponse(AgentStep):
 * Produces AgentStepResponse from a single step.
 * AgentStep.input is automatically excluded because
 * AgentStepResponse does NOT have an input field.
 * MapStruct only maps fields that exist in BOTH
 * source and target — no @Mapping needed to exclude.
 */
@Mapper(componentModel =
        MappingConstants.ComponentModel.SPRING)
public interface AgentMapper {

    /**
     * Convert Agent to response without steps.
     *
     * @Mapping(target = "steps") required because
     * AgentResponse.steps exists but Agent.steps does not.
     * MapStruct would fail without this ignore.
     *
     * @param agent the agent entity
     * @return AgentResponse with steps=null
     */
    @Mapping(target = "steps", ignore = true)
    AgentResponse toResponse(Agent agent);

    /**
     * Convert AgentStep to step response.
     *
     * AgentStep.input is NOT mapped because
     * AgentStepResponse has no "input" field.
     * MapStruct skips source fields with no
     * matching target field automatically.
     *
     * No @Mapping annotations needed here —
     * all field names match between source and target.
     *
     * @param step the agent step entity
     * @return AgentStepResponse
     */
    AgentStepResponse toStepResponse(AgentStep step);
}