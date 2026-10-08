package com.mccal.folio

/**
 * How Folio feels out of the box: its own iPhone-style shade and island, or Android's. A profile is only a named
 * bundle of settings that already exist, kept in this one place so setup, Settings and later profiles (Simple Mode)
 * read the same answer instead of each rebuilding it. [CUSTOM] is what any other combination is called; it is never
 * applied, only reported.
 */
enum class ExperienceProfile(@androidx.annotation.StringRes val label: Int, @androidx.annotation.StringRes val detail: Int) {
    FOLIO(R.string.profile_folio, R.string.profile_folio_detail),
    ANDROID_STYLE(R.string.profile_android_style, R.string.profile_android_style_detail),
    CUSTOM(R.string.profile_custom, R.string.profile_custom_detail);

    /** The settings this profile stands for; null for [CUSTOM], which is whatever the person has set. */
    internal val bundle: ProfileBundle? get() = when (this) {
        FOLIO -> ProfileBundle(island = true, folioPanels = true, swipeDownHome = "SPOTLIGHT")
        ANDROID_STYLE -> ProfileBundle(island = false, folioPanels = false, swipeDownHome = "NOTIFICATIONS")
        CUSTOM -> null
    }
}

internal data class ProfileBundle(val island: Boolean, val folioPanels: Boolean, val swipeDownHome: String)

/** [profile]'s settings applied; [ExperienceProfile.CUSTOM] changes nothing. Everything outside the bundle is left alone. */
internal fun LauncherState.withProfile(profile: ExperienceProfile): LauncherState = profile.bundle?.let {
    copy(island = it.island, folioPanels = it.folioPanels, swipeDownHome = it.swipeDownHome)
} ?: this

/** Which profile these settings are: the first whose bundle matches exactly, else [ExperienceProfile.CUSTOM]. */
internal fun LauncherState.profile(): ExperienceProfile {
    val now = ProfileBundle(island, folioPanels, swipeDownHome)
    return ExperienceProfile.entries.firstOrNull { it.bundle == now } ?: ExperienceProfile.CUSTOM
}
