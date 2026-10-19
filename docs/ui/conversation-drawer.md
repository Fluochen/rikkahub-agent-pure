# Conversation drawer refresh

The left-side conversation drawer (opened by swiping right) retains the existing
frosted background, assistant/folder scoping, paging, date groups, pinning and
management callbacks. The right-side workbench is not changed.

## Changes

- Separate the identity/history row from a labelled search entry and New Chat.
  Search continues to open the existing message search screen; it is not an
  editable field or a new search backend. New Chat uses the existing navigation path.
- Allow up to 320dp of drawer width, constrained by the available window.
- Use real header/list/footer layout space instead of measuring overlay heights.
  For heights below 560dp or font scales above 1.3, put both control areas into
  the same lazy list so neither can consume the entire conversation viewport.
- Allow horizontal scrolling of footer shortcuts in narrow windows. Preserve
  the assistant picker, history, backup/update reminders and all existing tools.
- Give titles up to two lines, with timestamp/pin/running information beneath.
  Use a solid selected container and matching foreground; expose selection and
  running state to accessibility. Running status is textual rather than dot-only.
- Add a 48dp More button to each conversation while keeping long-press menus.
  Both open the same menu and use the same rename/pin/move/delete callbacks.
- Make folder chips at least 48dp high, bound long names to 200dp, key folders by
  ID, expose selected filter state, and distinguish Add Folder from filter tabs.
- Separate initial loading, errors with Retry and successful empty results.
  Keep cached rows visible on refresh errors and expose append loading/retry.
- Account for the optional header and paging placeholders when locating the
  current conversation for automatic scrolling.

## Tests

Added 12 Compose instrumentation cases across `ConversationDrawerTest` and
`ConversationDrawerPagingTest`: primary actions, touch targets, selected/running
states, More/long-press callbacks, title fallback, folder sizing/selection, paging
status UI, current-item scrolling with a header, and footer reachability in a
240dp viewport.

Suggested validation on an Android development machine:

```sh
./gradlew :app:compileMoxwDebugKotlin :app:compileMoxwDebugAndroidTestKotlin
./gradlew :app:connectedMoxwDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=me.rerere.rikkahub.ui.pages.chat.ConversationDrawerTest,me.rerere.rikkahub.ui.pages.chat.ConversationDrawerPagingTest
```

The sandbox has no Java executable / JAVA_HOME, so Gradle could not start.
Compilation and instrumentation tests have **not** run. Kotlin syntax parsing,
string-resource checks, XML parsing and `git diff --check` are static checks only.

## Device acceptance checklist

- [ ] Portrait, landscape, split-screen and large fonts: all controls can be reached
      and no rows are hidden underneath fixed control islands.
- [ ] Light/dark/AMOLED/dynamic colour: selected text and timestamps remain legible
      over the frosted background.
- [ ] Search, New Chat, History, avatar/nickname editing and assistant switching.
- [ ] Long Chinese/English/RTL titles and folder names; many folders/shortcuts.
- [ ] Tap a row vs More vs long-press: correct conversation and management target.
- [ ] Pin/unpin, rename, move to assistant/folder, delete and protected archive folder.
- [ ] Empty folder, slow loading, failed initial load, refresh/append retry, cached rows.
- [ ] Current conversation auto-scroll, restoring scroll position and rotation.
- [ ] TalkBack selection/running announcements and 48dp control touch areas.
