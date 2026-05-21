package com.alexpo.grammermate.data

/**
 * Version constants for app migration tracking.
 *
 * Each TASK that requires data migration increments the version number.
 * When app launches, AppRoot.checkAndMigrate() compares last version
 * and runs migrations if lastVersion < current version.
 *
 * Version numbering: TASK-NN = version NN (e.g., TASK-080 = version 80)
 */
object AppVersions {
    /** Initial version (0) - no migrations run yet */
    const val INITIAL = 0

    /** TASK-080: State isolation - migrate daily cursor from global to pack-scoped */
    const val VERSION_080_STATE_ISOLATION = 80

    /** TASK-081: Lesson progress isolation - migrate lesson progress from global to pack-scoped */
    const val VERSION_081_LESSON_PROGRESS_ISOLATION = 81
}
