package com.mccal.folio

import android.content.Context
import android.os.Build
import java.net.URLEncoder

/**
 * Help › Report a Bug: GitHub's bug form with the details a report needs already filled in. It only opens the
 * browser; nothing is sent until you submit the form yourself.
 */
internal object BugReport {
    const val NEW_ISSUE = "https://github.com/McCal-Codes/folio/issues/new"

    fun url(version: String?, manufacturer: String, model: String, androidRelease: String, sdk: Int, screen: String? = null, note: String? = null): String {
        fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
        val phone = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        return "$NEW_ISSUE?template=bug_report.yml&version=${enc(version ?: "")}&phone=${enc(phone.trim())}" +
            "&android=${enc("$androidRelease (API $sdk)")}" +
            ((screen?.let { "Screen: $it\n" } ?: "") + (note?.let { "Test: $it\n" } ?: "")).takeIf { it.isNotEmpty() }?.let { "&diagnostics=${enc("$it\n")}" }.orEmpty()
    }

    fun url(context: Context, note: String? = null): String = url(
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull(),
        Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, Build.VERSION.SDK_INT, Diagnostics.screenSummary(context), note)
}
