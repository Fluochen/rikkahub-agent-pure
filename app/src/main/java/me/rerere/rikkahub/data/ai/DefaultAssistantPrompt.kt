package me.rerere.rikkahub.data.ai

/**
 * The prompt shipped with the built-in assistants.
 *
 * It is deliberately a *personality* template, not an operating manual: who the assistant is, and
 * how it likes to answer. Everything mechanical — what this client holds, tool discipline, file
 * placement, verification, memory, missions, what can actually be perceived — is applied by the
 * app (`SystemInjections`) and read on demand from `moxw_guide`, so a working install needs no
 * prompt work and this text does not grow when a capability ships.
 *
 * That split is the point of the layer: the user's prompt is where personality, expertise and
 * project conventions go; it is no longer where the app's behaviour has to be re-explained.
 *
 * Template placeholders are resolved by the app's template layer (`{{char}}`, `{{model_name}}`,
 * `{{cur_date}}`, …); they keep the prompt readable in a fresh install.
 */
object DefaultAssistantPrompt {

    /**
     * The generic template given to every built-in assistant. Short on purpose: it composes with
     * the injected packs instead of competing with them, and a user who rewrites it loses nothing
     * the app was doing for them.
     */
    val SKELETON: String = """
        You are {{char}}, running on the user's Android device with {{model_name}}.

        ## Who you are
        Be a specific assistant rather than a generic one: an area of expertise, opinions you are
        willing to state plainly, and a way of talking the user recognises. Edit this section
        freely — it is the part only you can write.

        ## How you answer
        - Reply in the user's language unless asked otherwise.
        - Lead with the result. Keep a simple answer short; expand only when the work needs it.
        - Plain words, Markdown when structure helps, LaTeX for maths.
        - No restating the question, no apologies, no closing offers.
        - Say what you verified, what you inferred, and what failed.

        ## Preferences
        - Language, tone, formatting and project conventions stated here or by the user win over
          your own defaults.
        - Handle: {{user}} | Date: {{cur_date}} | Locale: {{locale}} | Timezone: {{timezone}}
        - Device: {{device_info}} ({{system_version}})

        How this app works — its tools, files, missions, memory and limits — is applied by the
        app itself. Keep this prompt for who you are and how you prefer to work.
    """.trimIndent()

    /**
     * The skeleton shipped between the `#393` defaults work and this one. It carried a
     * "How to work" section that duplicated the injected packs; installs that picked it up are
     * still untouched defaults, so it upgrades like [LEGACY_DEFAULT] does.
     */
    val PREVIOUS_SKELETON: String = """
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
     * The prompt the built-in assistant shipped with before `#393`. Existing installs may still
     * carry this string, so the one-shot upgrade compares normalized text and replaces it — a
     * prompt the user wrote themselves never matches and is left alone.
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

    /** Every prompt string this app shipped as a default, oldest first. */
    val SHIPPED_DEFAULTS: List<String> = listOf(LEGACY_DEFAULT, PREVIOUS_SKELETON)

    /** True when [prompt] is blank or is still one of the defaults this app shipped. */
    fun isUntouched(prompt: String): Boolean {
        if (prompt.isBlank()) return true
        val normalized = normalize(prompt)
        return SHIPPED_DEFAULTS.any { normalize(it) == normalized }
    }

    /**
     * Returns [SKELETON] for an untouched built-in prompt and [prompt] unchanged otherwise.
     * Whitespace-insensitive so an install that merely reformatted a shipped default still
     * upgrades.
     */
    fun upgrade(prompt: String): String =
        if (isUntouched(prompt)) SKELETON else prompt

    private fun normalize(text: String): String =
        text.replace(Regex("\\s+"), " ").trim()
}
