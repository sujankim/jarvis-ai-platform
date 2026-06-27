package ai.jarvis.agents;

/**
 * Type of a single step in the ReACT agent loop.
 *
 * ReACT = Reason + Act pattern:
 *
 * THINK:   AI reasons about what to do next.
 *          "I need to check the weather in London"
 *          Input: the prompt given to AI
 *          Output: AI's reasoning text
 *
 * ACT:     AI calls a specific tool.
 *          "Calling WeatherTool with London"
 *          Input: tool input parameters
 *          Output: null (result comes in OBSERVE)
 *
 * OBSERVE: Tool execution result recorded.
 *          "WeatherTool returned: 22°C, Sunny"
 *          Input: null
 *          Output: the raw tool result
 *
 * FINAL:   Agent synthesizes final answer.
 *          "The weather in London is 22°C and sunny."
 *          Input: null
 *          Output: the final answer shown to user
 */
public enum AgentStepType {
    THINK,
    ACT,
    OBSERVE,
    FINAL
}