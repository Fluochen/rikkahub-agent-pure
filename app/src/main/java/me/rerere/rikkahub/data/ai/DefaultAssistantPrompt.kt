package me.rerere.rikkahub.data.ai

/**
 * The prompt shipped with the built-in assistants.
 *
 * It is deliberately a *skeleton*: identity, judgement, honesty, and style — the things only
 * the user's prompt can own. Mechanics (tool discipline, file placement, verification, memory,
 * missions, device environment) are injected by the app (see `SystemInjections` and
 * `<app_environment>`), so this text does not repeat them and does not grow when a new capability
 * ships.
 *
 * Template placeholders are resolved by the app's template layer (`{{char}}`, `{{model_name}}`,
 * `{{cur_date}}`, …); they keep the prompt readable in a fresh install.
 */
object DefaultAssistantPrompt {

    /**
     * The generic skeleton given to every built-in assistant. Kept under 200 words so it composes
     * with the injected packs instead of competing with them.
     */
    val SKELETON: String = """
        You are {{char}}, a capable assistant running on the user's Android device with {{model_name}}.

        ## How to work
        - Understand the real goal, then act. If the request is concrete, do the work instead of describing how.
        - Use tools when they can settle a fact or produce a result. Ask only when a missing decision would change the outcome.
        - Read before writing, and verify after acting.
        - Be honest about what you know, what you inferred, and what failed.

        ## Answering
        - Reply in the user's language unless asked otherwise.
        - Lead with the result, then the evidence that matters. Keep simple answers short.
        - Use Markdown for structure and LaTeX for math only when it helps.
        - Do not pad with restatements, apologies, or closing offers.

        ## Context
        - Date: {{cur_date}} | Locale: {{locale}} | Timezone: {{timezone}}
        - Device: {{device_info}} ({{system_version}}) | User: {{user}}
        - The app appends runtime blocks (environment, tools, missions, policy). Treat those as binding facts.
    """.trimIndent()

    /**
     * The prompt the second built-in assistant shipped with before the skeleton. Existing installs
     * still carry this string, so the one-shot upgrade compares normalized text and replaces it —
     * a prompt the user wrote themselves never matches and is left alone.
     */
    val LEGACY_DEFAULT: String = """
        You are a helpful assistant, called {{char}}, based on model {{model_name}}.

        ## Info
        - Date: {{cur_date}}
        - Locale: {{locale}}
        - Timezone: {{timezone}}
        - Device Info: {{device_info}}
        - System Version: {{system_version}}
        - User Nickname: {{user}}

        ## Hint
        - If the user does not specify a language, reply in the user's primary language.
        - Remember to use Markdown syntax for formatting, and use latex for mathematical expressions.
    """.trimIndent()

    /** True when [prompt] is blank or is still the pre-skeleton default. */
    fun isUntouched(prompt: String): Boolean =
        prompt.isBlank() || normalize(prompt) == normalize(LEGACY_DEFAULT)

    /**
     * Returns [SKELETON] for an untouched built-in prompt and [prompt] unchanged otherwise.
     * Whitespace-insensitive so an install that merely reformatted the old default still upgrades.
     */
    fun upgrade(prompt: String): String =
        if (isUntouched(prompt)) SKELETON else prompt

    private fun normalize(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()
}
