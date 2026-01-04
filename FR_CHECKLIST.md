# FR Checklist

Legend:
- [x] met
- [~] partial
- [ ] not met
- [?] unknown

## 6.1 Training and checking
- [x] FR-01 Show RU sentence one at a time.
- [x] FR-02 Accept answer via text and voice input (STT).
- [x] FR-03 Check only after explicit "Check" action (voice submits after recognition).
- [x] FR-04 Multiple accepted translations per card.
- [x] FR-05 Hint after 3 incorrect attempts.
- [x] FR-06 Track correct/incorrect counts and active time.
- [x] FR-07 Compute speed (stars/min) from correct and active time.
- [x] FR-08 Modes: lesson / all sequential / all mixed.
- [x] FR-09 Pause excludes pause time from active time.
- [x] FR-10 Success/error sounds on correct/incorrect.
- [x] FR-11 On correct answer in voice mode, auto-advance and re-trigger voice.
- [x] FR-12 After 3 incorrect attempts, show hint and wait for user action; mic/manual input stays available.
- [x] FR-13 After 3 incorrect attempts, incorrect-attempts counter resets.

## 6.2 Languages
- [x] FR-20 Support at least two target languages: English and Italian.
- [x] FR-21 Selection of active target language.
- [x] FR-22 Add new target language by name and import lessons to it.
- [x] FR-23 Lessons stored separately per target language.

## 6.3 Lesson management (service mode)
- [x] FR-30 Gear icon opens service mode.
- [x] FR-31 Import lesson from CSV.
- [x] FR-32 Delete a single lesson.
- [x] FR-33 Bulk delete all lessons for selected language.
- [x] FR-34 Reset/reload lesson set (clear + import).
- [x] FR-35 Create an empty lesson without CSV.
- [x] FR-36 If imported lesson title matches existing, replace old lesson.
- [x] FR-37 Entering settings pauses the session.
- [x] FR-38 Return to main training screen in ready-to-input mode (voice/text active).
- [x] FR-38 If there are no lessons/cards, block text and voice input with "No cards" message.

## 6.4 Progress persistence
- [x] FR-40 Save progress and stats locally.
- [x] FR-41 Restore last state after restart.