package com.alexpo.grammermate.ui.navigation

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.alexpo.grammermate.R
import com.alexpo.grammermate.ui.TrainingViewModel
import com.alexpo.grammermate.ui.dialogs.DialogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * THE shared "user tapped daily practice" handler (Phase 4, item 4.2 — was
 * duplicated verbatim at both HOME call sites).
 *
 * Resumable session → open the resume dialog; otherwise start a fresh daily
 * practice (off-main) with the loading overlay, then navigate to
 * DAILY_PRACTICE or toast on failure.
 */
@Composable
internal fun rememberOnDailyPractice(
    vm: TrainingViewModel,
    getDialogs: () -> DialogState,
    setDialogs: (DialogState) -> Unit,
    onNavigate: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val dailyScope = rememberCoroutineScope()
    return remember(vm, context, dailyScope, onNavigate) {
        {
            val level = vm.getProgressLessonLevel()
            Log.d("GrammarMate", "DailyPractice: user clicked daily practice, level=$level")
            if (vm.daily.hasResumableDailySession()) {
                setDialogs(DialogState.DailyResume(level))
            } else {
                setDialogs(DialogState.DailyLoading)
                dailyScope.launch {
                    try {
                        val started = withContext(Dispatchers.IO) {
                            vm.startDailyPractice(level)
                        }
                        setDialogs(DialogState.None)
                        if (started) {
                            onNavigate(Routes.DAILY_PRACTICE)
                        } else {
                            toast(context)
                        }
                    } catch (e: Exception) {
                        setDialogs(DialogState.None)
                        toast(context)
                    }
                }
            }
        }
    }
}

private fun toast(context: Context) {
    Toast.makeText(context, context.getString(R.string.dialog_daily_loading), Toast.LENGTH_SHORT).show()
}
