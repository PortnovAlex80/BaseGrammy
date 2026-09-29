# G5 route matrix

Scope: the 14 constants in [Routes.kt](../../app/src/main/java/com/alexpo/grammermate/ui/navigation/Routes.kt). A route is a device `PASS` only when the current revision opens it through a production control, observes a route-specific element, and leaves through its intended Back/Exit path. The [production-entry journey](../../app/src/androidTest/java/com/alexpo/grammermate/ProductionRouteJourneySmokeTest.kt) covers the ten user-reachable routes through `MainActivity` and the bundled Italian chapter pack. It does not inject a `NavController` or call a destination directly.

| Route | Production entry and route-specific observation | Return assertion | Status |
|---|---|---|---|
| `HOME` | Italian language and “Full Course Italian from Alex Po” pack tile → “Grammar Story Roadmap” | Child routes return to the roadmap | `PASS` |
| `CHAPTER_LESSONS` | First lesson-bearing chapter “Continue” or “Review” → “lessons completed” and A01 tile | Back → roadmap | `PASS` |
| `LESSON` | A01 tile → “Start Lesson”, “Continue Lesson”, or completed lesson card | Back → chapter lessons | `PASS` |
| `TRAINING` | Lesson start, or Repeat → Easy when completed, reveals `show_answer_button` | System Back → “End session?” → Exit → lesson | `PASS` |
| `STORY_READER` | “Read Story” → “Copy story to clipboard” | “Back to Roadmap” → roadmap | `PASS` |
| `LADDER` | Roadmap Settings → “Show ladder” → “Interval Ladder” | System Back → roadmap | `PASS` |
| `BACKGROUND_VOCAB` | Roadmap background-listening button → “Next word” control | Back → roadmap | `PASS` |
| `VERB_DRILL` | “Verb Practice” → sort checkbox or saved-session card | Back → roadmap | `PASS` |
| `VOCAB_DRILL` | “Flashcards” → “Due:” count or empty-deck message | Back → roadmap | `PASS` |
| `AUX_DRILL` | “Lead-in” → auxiliary-verb selector | Back → roadmap | `PASS` |
| `DAILY_PRACTICE` | Classic Home tile has `enabled = false`; roadmap button shows an unavailable toast and does not navigate ([HomeScreen.kt](../../app/src/main/java/com/alexpo/grammermate/ui/screens/HomeScreen.kt), [GrammarStoryRoadmapScreen.kt](../../app/src/main/java/com/alexpo/grammermate/ui/screens/GrammarStoryRoadmapScreen.kt)) | No live route entry | `BLOCKED_BY_DESIGN` |
| `STORY` | Route exists, but no production `navigate(Routes.STORY)` caller; the reader has no quiz entry ([GrammarMateApp.kt](../../app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt)) | No live route entry | `UNTESTED` — no production entry to exercise |
| `ELITE` | Compatibility destination has no production entry caller; [GrammarMateApp.kt lines 460–461](../../app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L460-L461) immediately navigate to HOME with inclusive `popUpTo(ELITE)` | Redirect only; static verification, not a user journey | `UNREACHABLE_LEGACY` |
| `VOCAB` | Compatibility destination has no production entry caller; [GrammarMateApp.kt lines 465–466](../../app/src/main/java/com/alexpo/grammermate/ui/GrammarMateApp.kt#L465-L466) immediately navigate to HOME with inclusive `popUpTo(VOCAB)` | Redirect only; static verification, not a user journey | `UNREACHABLE_LEGACY` |

Device evidence: `:app:connectedDebugAndroidTest --no-parallel --max-workers=1` on `emulator-5554` (`Medium_Phone_API_36.1`, API 36), 2026-09-30, `BUILD SUCCESSFUL`, JUnit 2 tests / 0 failures / 0 errors / 0 skipped. The XML is at `app/build/outputs/androidTest-results/connected/debug/TEST-Medium_Phone_API_36.1(AVD) - 16-_app-.xml`; the route test's device log is at `app/build/outputs/androidTest-results/connected/debug/Medium_Phone_API_36.1(AVD) - 16/logcat-com.alexpo.grammermate.ProductionRouteJourneySmokeTest-productionEntriesRenderAndReturnThroughBack.txt`. The ten `PASS` rows share the single passing production-entry journey and its explicit per-route Back assertions.

The `STORY` and `DAILY_PRACTICE` rows do not count as passed user journeys. Boss battle is a mode inside `TRAINING`, outside this 14-route inventory; its separate B1 fixture remains required by the architecture closure contract.
