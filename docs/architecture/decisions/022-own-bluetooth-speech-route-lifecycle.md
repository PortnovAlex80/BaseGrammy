# 022. Own the Bluetooth speech route across recognizer lifecycles

- **Status:** Accepted and implemented; hardware smoke remains under G5
- **Date:** 2026-09-29
- **Decision-maker:** architecture review under ADR-009
- **Scope:** external `RecognizerIntent`, offline ASR and Bluetooth mic routing

## Context and choice

Four external-recognizer launch sites start Bluetooth setup in a detached coroutine,
launch the OS recognizer immediately and never call their threaded cleanup callback.
Offline ASR calls cleanup only after normal transcription. A synchronous router stop
can race with an in-flight suspend start. Turning Bluetooth off changes the setting
before `stopBluetoothMicIfNeeded` checks it, so an active route may survive. Rapid
offline restart can let an old job clear the new job reference.

| Driver | Weight |
|---|---:|
| Correct cleanup under cancellation and races | 40% |
| One clear effect owner | 25% |
| Testability without Bluetooth hardware | 20% |
| Migration cost and reversibility | 15% |

| Option | Correctness | Ownership | Testability | Cost | Weighted / 5 |
|---|---:|---:|---:|---:|---:|
| A - audio-owned tokenized route lease, UI owns ActivityResult | 5 | 5 | 4 | 2 | 4.35 |
| B - UI-only per-launch helper and existing start/stop callbacks | 3 | 3 | 4 | 4 | 3.35 |
| C - call the dead cleanup callback on every result | 2 | 2 | 2 | 5 | 2.45 |

Choose A for the next implementation packet. `AudioCoordinator` (or a small
`shared/audio` owner it composes) must serialize route acquisition/release and
issue one idempotent handle per recording. It must reject or serialize overlapping
recognition; the bounded default is **Busy**, without attempting to preempt an OS
recognizer. External UI launchers await acquisition, launch the intent, and close
the handle on success, cancellation, launch failure or disposal. Offline ASR
closes its handle and cancels its state collector in `finally`. The audio owner
also revokes the route when the setting is disabled and at release. The existing
user-facing fallback to the device microphone remains when Bluetooth routing
fails; cancellation must not be swallowed as an ordinary failure.

## Pre-mortem and decision journal

Red-team failures: a result arrives before setup completes and a late start
reopens the route; a previous result closes a newer recording's route; a canceled
ASR job leaves the collector or Bluetooth active; setting-off skips cleanup;
the old ASR job erases a newer job reference; UI disposal cancels its scope before
asynchronous cleanup runs. Tests must use a delayed fake router to cover each
ordering and verify that exactly the current owner can release the route. UI
tests should dispatch both successful and canceled ActivityResult callbacks.
Hardware-quality Bluetooth behavior is outside unit-test evidence and remains
for the final device gate.

B is smaller but leaves route ownership split across four screens and cannot
by itself serialize shared router effects. C fixes only the ordinary result path
and fails the delayed-start case.

## Implementation evidence

`SpeechRouteOwner` owns one route lease and returns Busy while a recording is
preparing or active. `AudioCoordinator` uses it for offline ASR and exposes the
same acquisition to the four external recognizer launchers. The UI's
`SpeechRecognitionAttempt` waits for the lease and closes it before processing
every result, on launch failure, or when its composable leaves composition.
The old setup/cleanup callback pair has no production caller. Router failure
and cancellation clean partial SCO state; disabling Bluetooth and releasing
the coordinator revoke the active route. Offline ASR closes its lease and
state collector in `finally`, and a restarted job waits for its predecessor.

Route-owner tests cover exclusivity, stale callbacks, delayed setup, settings
disable, cancellation, shutdown and fallback. UI-attempt tests cover result,
cancellation, launch failure, Busy and disposal during setup. The regular
lesson, vocabulary and daily click suites pass; debug assembly, all 631 unit
tests and Android-test source compilation pass. A device-level Bluetooth route
check is still required by G5.
