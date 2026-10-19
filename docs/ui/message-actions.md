# Message actions: quieter reading surface

## Scope

This pass changes the native Android chat message actions, not the theme, provider
configuration, agent execution or approval policy.

- Inline actions: Copy, Regenerate, More. Each has an explicit 48dp touch target,
  an accessibility label and a long-press tooltip. Icons remain visually small.
- Speech and translation move into More; no settings or translation capabilities
  are removed. Speech still applies the quoted-text / outside-brackets preferences.
- More uses labelled 56dp-minimum list rows and a scrollable body. Metadata appears
  first; deletion is separated from non-destructive actions. Labels may wrap.
- Translation-dialog state belongs to the message, outside the dismissed menu.
- Branch arrows use real disabled semantics at their boundaries. A stale selection
  displays the last branch, matching ChatMessage's existing content fallback.
- User-message regeneration still requires confirmation; assistant regeneration
  remains one tap. Existing deletion callbacks and confirmations are unchanged.

## Verification

Added Compose instrumentation tests in:

- `ChatMessageActionsTest`: three primary actions, touch sizes, More callback,
  regeneration confirmation, branch navigation/boundaries/stale and empty nodes.
- `ChatMessageActionsSheetTest`: translation callback ordering, scroll-to-delete,
  disabled speech / active playback stop, quoted speech preference and user-only
  action filtering.

Suggested commands on a configured Android development machine:

```sh
./gradlew :app:compileMoxwDebugKotlin :app:testMoxwDebugUnitTest
./gradlew :app:connectedMoxwDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=me.rerere.rikkahub.ui.components.message.ChatMessageActionsTest,me.rerere.rikkahub.ui.components.message.ChatMessageActionsSheetTest
```

At implementation time the sandbox had no Java executable / JAVA_HOME, so Gradle
could not start. Tests have **not** been executed. Kotlin tree-sitter parsing and
`git diff --check` succeeded; these do not replace compilation or device testing.

## Device review before release

- [ ] Light, dark, AMOLED and dynamic-color themes.
- [ ] Small display, landscape, large fonts and RTL: actions wrap without overlap;
      the More menu scrolls to Delete and multi-line labels remain readable.
- [ ] TalkBack: action labels, disabled branch arrows and disabled speech.
- [ ] Copy, retry, edit, share, fork, favorite, text selection and WebView preview.
- [ ] More → Translate: sheet closes, picker stays open, selection and clearing work.
- [ ] More → Speech: respects both reading preferences; reopen More → Stop.
- [ ] First, middle and last branches; streaming completion and message removal.
