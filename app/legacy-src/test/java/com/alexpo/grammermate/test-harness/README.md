# Test Harness Infrastructure

In-memory fake implementations for scenario testing. No file I/O, no Android dependencies.

## Fake Implementations

| Fake Class | Real Interface | Purpose |
|-----------|----------------|---------|
| `FakeTrainingStateAccess` | `TrainingStateAccess` | Mutable state holder for tests, wraps `TrainingUiState` in `MutableStateFlow` |
| `FakeMasteryStore` | `MasteryStore` | In-memory mastery tracking (card shows, encounters, lesson completion) |
| `FakeVerbDrillStore` | `VerbDrillStore` | In-memory verb drill progress and card storage |
| `FakeProgressStore` | `ProgressStore` | In-memory training session progress |
| `FakeWordMasteryStore` | `WordMasteryStore` | In-memory vocab drill mastery (Anki-style SRS) |

## Usage Example

```kotlin
class MyScenarioTest {
    @Test
    fun testSomeScenario() {
        // Arrange
        val fakeStateAccess = FakeTrainingStateAccess()
        val fakeMasteryStore = FakeMasteryStore()
        val fakeProgressStore = FakeProgressStore()

        // Act
        fakeMasteryStore.recordCardShow("lesson1", "it", "card1")
        val mastery = fakeMasteryStore.get("lesson1", "it")

        // Assert
        assertEquals(1, mastery?.uniqueCardShows)
        assertTrue(mastery?.shownCardIds?.contains("card1") == true)
    }
}
```

## Design Principles

1. **No Android dependencies** - All fakes work in pure JVM tests
2. **No file I/O** - All data stored in memory maps
3. **Simplified logic** - No debouncing, no coroutines, no mutex locking
4. **Test helpers** - Each fake has helper methods for common test operations
