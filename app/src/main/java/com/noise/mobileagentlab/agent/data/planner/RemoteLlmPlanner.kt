package com.noise.mobileagentlab.agent.data.planner

import com.noise.mobileagentlab.agent.domain.json.Json
import com.noise.mobileagentlab.agent.domain.json.Json.string
import com.noise.mobileagentlab.agent.domain.json.JsonValue
import com.noise.mobileagentlab.agent.domain.model.FailureReason
import com.noise.mobileagentlab.agent.domain.planner.AgentPlanner
import com.noise.mobileagentlab.agent.domain.planner.PlannerOutcome
import com.noise.mobileagentlab.agent.domain.planner.PlanningRequest
import com.noise.mobileagentlab.agent.domain.planner.ProposalParseResult
import com.noise.mobileagentlab.agent.domain.planner.ProposalSchema
import com.noise.mobileagentlab.agent.domain.verify.Expectations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Phase 11 — user-configured, provider-neutral LLM planner.
 *
 * Talks to any OpenAI-compatible chat-completions endpoint with
 * [java.net.HttpURLConnection] (no SDK dependency). The model only ever returns
 * a JSON proposal which must pass [ProposalSchema]; execution still requires
 * local validation, so a misbehaving model cannot escape the safety boundary.
 *
 * Opt-in: disabled until the user fills in endpoint + model. Exactly what is
 * sent is documented in `docs/safety.md` (task text, compact UI state, action
 * history — never raw accessibility trees, credentials or screenshots).
 */
class RemoteLlmPlanner(
    private val configProvider: () -> LlmConfig,
) : AgentPlanner {

    override val id: String = ID
    override val displayName: String = "Remote LLM planner (OpenAI-compatible)"

    override suspend fun plan(request: PlanningRequest): PlannerOutcome =
        withContext(Dispatchers.IO) {
            val config = configProvider()
            if (!config.isConfigured) {
                return@withContext PlannerOutcome.Failed(
                    reason = FailureReason.PLANNER_ERROR,
                    detail = "LLM planner is not configured (endpoint/model missing)",
                )
            }

            val payload = chatPayload(config, buildPrompt(request))
            val responseBody = try {
                post(config, payload)
            } catch (e: Exception) {
                return@withContext PlannerOutcome.Failed(
                    reason = FailureReason.PLANNER_ERROR,
                    detail = "LLM request failed: ${e.javaClass.simpleName}",
                )
            }

            val content = extractContent(responseBody)
                ?: return@withContext PlannerOutcome.Failed(
                    reason = FailureReason.PARSE_ERROR,
                    detail = "LLM response did not contain a message content",
                )

            when (val parsed = ProposalSchema.parse(content)) {
                is ProposalParseResult.Ok -> {
                    val proposal = parsed.proposal
                    val action = proposal.action
                    PlannerOutcome.Proposal(
                        action = action,
                        expectation = proposal.expectation
                            ?: Expectations.forAction(action, action.expectedLabel()),
                        reason = proposal.reason.ifBlank { "llm proposal" },
                        goalIndex = request.nextGoalIndex,
                        completesGoal = true,
                    )
                }

                is ProposalParseResult.Invalid -> PlannerOutcome.Failed(
                    reason = FailureReason.PARSE_ERROR,
                    detail = "invalid model output: ${parsed.detail}",
                )
            }
        }

    private fun buildPrompt(request: PlanningRequest): String = buildString {
        append("Task: ").append(request.task.title).append('\n')
        append("Goal ").append(request.nextGoalIndex + 1)
        append('/').append(request.task.goals.size).append(": ")
        append(
            request.task.goals.getOrNull(request.nextGoalIndex)?.describe
                ?: "(all goals executed)",
        )
        append('\n')
        append("Rules: propose exactly one typed action for the CURRENT screen below.\n")
        append(ProposalSchema.PROMPT_INSTRUCTIONS)
        append('\n')
        append("Screen state:\n")
        append(request.state.render())
        if (request.history.isNotEmpty()) {
            append("\nPrevious steps:\n")
            for (step in request.history.takeLast(8)) {
                append('#').append(step.stepIndex).append(' ')
                append(step.action).append(" -> ")
                append(if (step.success) "verified" else "failed")
                append(": ").append(step.verification).append('\n')
            }
        }
    }

    private fun chatPayload(config: LlmConfig, prompt: String): String = Json.write(
        JsonValue.Obj(
            linkedMapOf(
                "model" to JsonValue.Str(config.model),
                "temperature" to JsonValue.Num(config.temperature),
                "max_tokens" to JsonValue.Num(300.0),
                "messages" to JsonValue.Arr(
                    listOf(
                        JsonValue.Obj(
                            linkedMapOf(
                                "role" to JsonValue.Str("system"),
                                "content" to JsonValue.Str(SYSTEM_PROMPT),
                            ),
                        ),
                        JsonValue.Obj(
                            linkedMapOf(
                                "role" to JsonValue.Str("user"),
                                "content" to JsonValue.Str(prompt),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun post(config: LlmConfig, body: String): String {
        val connection = URL(config.endpointUrl).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = config.timeoutMs
            connection.readTimeout = config.timeoutMs
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            if (config.apiKey.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            connection.outputStream.use { out ->
                out.write(body.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun extractContent(responseBody: String): String? {
        val root = runCatching { Json.parse(responseBody) }.getOrNull() as? JsonValue.Obj
            ?: return null
        // OpenAI-compatible: choices[0].message.content
        val choices = root["choices"] as? JsonValue.Arr
        val first = choices?.items?.firstOrNull() as? JsonValue.Obj
        val message = first?.get("message") as? JsonValue.Obj
        message?.string("content")?.takeIf { it.isNotBlank() }?.let { return it }
        // generic fallbacks
        root.string("content")?.takeIf { it.isNotBlank() }?.let { return it }
        root.string("output_text")?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun com.noise.mobileagentlab.agent.domain.action.AgentAction.expectedLabel(): String? =
        when (this) {
            is com.noise.mobileagentlab.agent.domain.action.AgentAction.Click -> expectedLabel
            is com.noise.mobileagentlab.agent.domain.action.AgentAction.TypeText -> expectedLabel
            is com.noise.mobileagentlab.agent.domain.action.AgentAction.LongClick -> expectedLabel
            else -> null
        }

    companion object {
        const val ID = "llm"

        private const val SYSTEM_PROMPT =
            "You are an Android UI agent operating a single allowlisted demo app. " +
                "You may only propose typed actions using the given JSON schema. " +
                "Never produce code, shell commands or intents."
    }
}

/** Phase 11 — user supplied connection settings (stored only in memory). */
data class LlmConfig(
    val endpointUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val temperature: Double = 0.0,
    val timeoutMs: Int = 20_000,
) {
    val isConfigured: Boolean
        get() = endpointUrl.startsWith("http") && model.isNotBlank()
}
