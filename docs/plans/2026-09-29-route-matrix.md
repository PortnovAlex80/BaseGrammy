# G5 route matrix

Scope: the 14 constants in [Routes.kt](../../app/src/main/java/com/alexpo/grammermate/ui/navigation/Routes.kt). A route is a device `PASS` only when the current revision opens it through a production control, observes a route-specific element, and leaves through its intended Back/Exit path. The [production-entry journey](../../app/src/androidTest/java/com/alexpo/grammermate/ProductionRouteJourneySmokeTest.kt) covers the ten user-reachable routes through `MainActivity` and the bundled Italian chapter pack. It does not inject a `NavController` or call a destination directly.

| Route | Production entry and route-specific observation | Return assertion | Status |
|---|---|---|---|
| `HOME` | Italian language and “Full Course Italian from Alex Po” pack tile → “Grammar Story Roadmap” | Child routes return to the roadmap | `UNTESTED` — device run pending |
| `CHAPTER_LESSONS` | First lesson-bearing chapter “Continue” or “Review” → “lessons completed” and A01 tile | Back → roadmap | `UNTESTED` |
| `LESSON` | A01 tile → “Start Lesson”, “Continue Lesson”, or completed lesson card | Back → chapter lessons | `UNTESTED` |
| `TRAINING` | Lesson start, or Repeat → Easy when completed, reveals `show_answer_button` | System Back → “End session?” → Exit → lesson | `UNTESTED` |
| `STORY_READER` | “Read Story” → “Copy story to clipboard” | “Back to Roadmap” → roadmap | `UNTESTED` |
| `LADDER` | Roadmap Settings → “Show ladder” → “Interval Ladder” | System Back → roadmap | `UNTESTED` |
| `BACKGROUND_VOCAB` | Roadmap background-listening button → “Next word” control | Back → roadmap | `UNTESTED` |
| `VERB_DRILL` | “Verb Practice” → sort checkbox or saved-session card | Back → roadmap | `UNTESTED` |
| `VOCAB_DRILL` | “Flashcards” → “Due:” count or empty-deck message | Back → roadmap | `UNTESTED` |
| `AUX_DRILL` | “Lead-in” → auxiliary-verb selector | Back → roadmap | `UNTESTED` |
| `DAILY_PRACTICE` | Classic Home tile has `enabled = false`; roadmap button shows an unavailable toast and does not navigate ([HomeScreen.kt](../../app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt), [GrammarStoryRoadmapScreen.kt](../../app/src/main/java/com/alexpo/grammermate/ui/screens/GrammarStoryRoadmapScreen.kt)) | No live route entry | `BLOCKED_BY_DESIGN` |
| `STORY` | Route exists, but no production `navigate(Routes.STORY)` caller; the reader has no quiz entry ([GrammarMateApp.kt](../../app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt)) | No live route entry | `UNTESTED` — no production entry to exercise |
| `ELITE` | Compatibility destination immediately redirects to HOME; no production entry caller ([GrammarMateApp.kt](../../app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt)) | Redirect only | `UNREACHABLE_LEGACY` |
| `VOCAB` | Compatibility destination immediately redirects to HOME; no production entry caller ([GrammarMateApp.kt](../../app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt)) | Redirect only | `UNREACHABLE_LEGACY` |

Device evidence is pending. `Medium_Phone_API_36.1` is running as `emulator-5554` with `sys.boot_completed=1` on 2026-09-30. Run `:app:connectedDebugAndroidTest --no-parallel --max-workers=1` after the final code revision, retain the JUnit XML and device log, then replace each reachable `UNTESTED` status with its actual `PASS` or `FAIL` result. Compilation alone never changes a row to `PASS`.

The `STORY` and `DAILY_PRACTICE` rows do not count as passed user journeys. Boss battle is a mode inside `TRAINING`, outside this 14-route inventory; its separate B1 fixture remains required by the architecture closure contract.
