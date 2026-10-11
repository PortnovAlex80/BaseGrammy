# 034. Scope bg-vocab audio banks to the corpus, not the pack

- **Status:** Accepted
- **Date:** 2026-10-11
- **Scope:** `BgVocabAudioResolver`, `DeckPlayer.defaultPlayWord`, `BackgroundVocabScreen.hasOpusClip`

## Context and decision

The pre-rendered Opus bank (and the legacy `.wav` bank) was looked up strictly under `drills/{activePackId}/bg_vocab/audio/`, while the bank is installed for exactly one pack (`ITALIAN_SHORT`). Device evidence (2026-10-11): the bank's 8 800 clips sit installed, yet the listener played all-TTS for every user state except `activePackId == ITALIAN_SHORT` — including `activePackId == ITALIAN_IMPERIAL` (manifest declares no `backgroundVocab` at all) and `activePackId == null` (Home screen; the lookup was skipped entirely via `packId?.let`).

The banks are properties of the **shared background-vocab corpus**, not of a lesson pack: the 12 000-word `bg_vocab_12000.csv` deck is byte-identical (md5) in the Express pack's zip and the bundled asset, ranks are corpus-global, and clip filenames carry the corpus language (`it_r7_f0.opus`). A pack-scoped lookup therefore cannot be correct — it silently discards installed quality audio whenever another pack (or no pack) is active.

Decision: `BgVocabAudioResolver` resolves each clip across an ordered bank list — the active pack's own `drills/{packId}/bg_vocab/audio` first, then every other installed sibling bank in ascending pack-id order. `packId` becomes nullable: `null` skips only the active-pack entry and still resolves siblings, so the bundled deck (no pack context) keeps the bank. `DeckPlayer.defaultPlayWord` and the per-card Opus indicator now resolve audio for any packId instead of short-circuiting on null. Cross-language reuse is impossible by construction (the requested filename embeds the language prefix), and banks are enumerated from disk per lookup, so an import while the listener runs is picked up on the next word.

## Pre-mortem and challenge

Wrong-clip risk: ranks from a *different* corpus resolving against this corpus's bank. Today there is a single corpus (byte-identical decks, verified), so the risk is latent, not live; the resolver KDoc and this ADR require a corpus discriminator in the bank layout before any non-shared deck may reuse it. Determinism: sibling order is fixed by pack-id sort, and the active pack always wins, so re-imports of one bank cannot reorder resolution for another. Cost: one `drills/` directory listing per speak item (≈10 per word) — negligible next to audio I/O; not caching is deliberate so live imports are seen. Delivery is unchanged: `SoundPackManager` still installs into the active pack's directory and `installedClipCount`/registry stay pack-scoped for download decisions; the known placeholder URL and missing Imperial registry entry are a separate delivery gap, not playback.

## Consequences and decision journal

New resolver tests pin: sibling fallback with an empty active bank, resolution with `packId == null`, active-pack precedence, deterministic sibling order, the same fallback for legacy `.wav` names, and null when no bank exists anywhere. `DeckPlayerAudioSegmentTest` replicas were updated to the new decision expression; the old `nullPackId_forcesAllText` contract was inverted deliberately (`nullPackId_resolvesFromSiblingBanks`) — that inversion is the fix. If a future pack ships its own corpus, add the discriminator first, then extend the resolver tests with mixed-corpus cases before shipping.
