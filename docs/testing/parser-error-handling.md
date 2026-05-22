# Parser Error Handling Test Procedure

## Overview
This document describes how to test the parser error handling functionality for GrammarMate (BaseGrammy). The parser now collects and reports errors instead of silently failing or throwing exceptions.

## Test Files

### Malformed CSV Test File
Location: `test_malformed.csv` (project root)

This file contains intentional errors to test error handling:
- Empty lines (multiple consecutive)
- Line without semicolon separator
- Empty Russian part
- Wrong number of columns (extra columns)
- Special characters (quotes, apostrophes)
- Multiple correct answers (using `+` separator)

## Test Procedure

### 1. Build and Install APK
```cmd
java -cp "gradle/wrapper/gradle-wrapper.jar;gradle/wrapper/gradle-wrapper-shared.jar;gradle/wrapper/gradle-cli.jar" org.gradle.wrapper.GradleWrapperMain assembleDebug
```

Install the resulting APK on your device/emulator.

### 2. Import Malformed File

#### Steps:
1. Launch GrammarMate app
2. Navigate to Settings → Import Lesson Pack
3. Select `test_malformed.csv` from file picker
4. **Expected Result**: Warning dialog should appear showing:
   - Error count
   - Line numbers with errors
   - Error severity levels (WARNING/ERROR/CRITICAL)
   - "Import Partial" and "Cancel" buttons

### 3. Verify Warning Dialog

#### Expected Elements:
- **Title**: "Import Completed with Errors" or similar
- **Error Summary**: "Import completed with N error(s):"
- **Error List**: Each error showing:
  - Format: `Line X [SEVERITY]: message`
  - File name if imported as part of a pack
  - Specific error message (e.g., "Expected 2 columns, got 1")

#### Error Detection:
- Line 4: No separator → ERROR
- Line 6: Empty Russian part → ERROR  
- Line 9: Extra columns → ERROR
- Lines 3, 5, 8: Should parse correctly

### 4. Test "Import Partial" Button

1. Click "Import Partial" button
2. **Expected Result**:
   - Valid cards are imported (Lines 3, 5, 8, 10, 11, 12)
   - Navigation returns to lesson list
   - Lesson appears with imported cards
   - No crash or unhandled exception

### 5. Test Well-Formed File (No Errors)

#### Create a well-formed CSV file:
```csv
Well-Formed Lesson
Здравствуйте;Hello
Привет;Hi+Hi there
Доброе утро;Good morning
```

#### Steps:
1. Import this well-formed file
2. **Expected Result**:
   - No warning dialog
   - Direct import to lesson list
   - All 3 cards available for practice

### 6. Verify Logging Output

#### Enable Logcat Filtering:
```cmd
adb logcat | grep "PackImporter"
```

#### Expected Log Messages:
```
PackImporter: Importing pack pack_id with N error(s)
lessons_ru.csv:Line 4 [ERROR]: Expected 2 columns (RU, answers), got 1 column(s): This line has no semicolon separator
lessons_ru.csv:Line 6 [ERROR]: Expected non-empty RU and answers, RU='', answers='Empty Russian part'
lessons_ru.csv:Line 9 [ERROR]: Expected 2 columns (RU, answers), got 3 column(s): Another line with wrong number of columns
```

#### Log Format Requirements:
- **Format**: `{filename}:Line {lineNumber} [{severity}]: {message}`
- **Severities**: WARNING, ERROR, CRITICAL
- **File context**: PackImporter adds filename prefix to all errors

### 7. Test Pack Import with Errors

#### Create a Test Pack Structure:
```
test_pack/
├── manifest.yaml
└── lessons/
    ├── en_lesson1.csv  (well-formed)
    ├── en_lesson2.csv  (malformed)
    └── en_lesson3.csv  (empty file)
```

#### Steps:
1. Create a ZIP file with the above structure
2. Import the pack through UI
3. **Expected Result**:
   - Warning dialog shows aggregated errors from all files
   - File names are included in error messages
   - Well-formed lesson (lesson1) is fully imported
   - Malformed lesson (lesson2) shows partial import
   - Empty lesson (lesson3) shows CRITICAL error

## Expected Error Types

### MalformedLine Error
- **Severity**: ERROR
- **When**: Line doesn't match expected format
- **Example**: Wrong column count, missing separator

### EmptyFile Error
- **Severity**: CRITICAL
- **When**: File is empty or contains no parseable content
- **Example**: 0-byte file, file with only empty lines

### InvalidFormat Error
- **Severity**: ERROR
- **When**: Data format is invalid
- **Example**: Invalid data type, corrupted content

### WithFileContext Error
- **Severity**: Inherits from wrapped error
- **When**: PackImporter adds file context to errors
- **Example**: "en_lesson2.csv:Line 5 [ERROR]: ..."

## Success Criteria

✅ **All tests pass when:**
- Malformed files trigger warning dialog (not silent failure)
- Error count and line numbers displayed correctly
- Severity levels shown appropriately
- "Import Partial" imports valid data
- Well-formed files import without warnings
- Logcat contains structured error messages
- No crashes or unhandled exceptions
- File names included in error messages for pack imports

## Regression Testing

After any parser changes, verify:
1. Existing well-formed lesson packs still import
2. Error dialog still appears for malformed files
3. Partial import functionality works
4. Logging format remains consistent
5. No new crashes in import flow

## Troubleshooting

### Dialog Not Appearing
- Check `TrainingViewModel.importFromUri()` for error collection
- Verify `showParseWarning` state is set to true
- Check `TrainingScreen` for dialog rendering logic

### Errors Not Logged
- Ensure Logcat is filtered for "PackImporter" tag
- Check Android log level (may need to set to VERBOSE)
- Verify `Log.e()` calls in `PackImporter.kt`

### Import Partial Not Working
- Check that `parseResult.data` is not null
- Verify partial data is properly extracted from `ParseResult`
- Ensure UI handles partial data correctly

## Notes

- Error handling is **not** a replacement for validation
- Critical errors still prevent import (no data)
- Users can choose to accept partial imports
- File context helps identify which file has errors in packs
- Line numbers are 1-based (first line = line 1)
