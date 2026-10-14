package com.mccal.folio

import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle

/** Persisted app identity. Personal-profile IDs intentionally retain the legacy component string. */
data class ProfileAppIdentity(val component: String, val userSerial: Long?)

/**
 * The profiles Folio lists apps from. Every kind but [PERSONAL] is "not personal", which is what `isWork` on an
 * [AppEntry] and `work` in the app cache and layout backups have always meant; [AppProfile.isWork] is the Work profile
 * alone, the one behind the App Library's Work switch.
 */
enum class ProfileKind {
    PERSONAL,
    /** A managed (work) profile. */
    WORK,
    /** A clone profile, where Android keeps a second copy of an app (Samsung's Dual Messenger, "App cloning"). */
    CLONE,
    /** Not listed: Private Space, and any type Folio doesn't know yet, since showing it could expose a hidden space. */
    SKIP,
}

/** Android's user types (UserManager.USER_TYPE_*), as text so the mapping is the same on every API level. */
const val USER_TYPE_FULL_PREFIX = "android.os.usertype.full."
const val USER_TYPE_PROFILE_MANAGED = "android.os.usertype.profile.MANAGED"
const val USER_TYPE_PROFILE_CLONE = "android.os.usertype.profile.CLONE"
const val USER_TYPE_PROFILE_PRIVATE = "android.os.usertype.profile.PRIVATE"

/** What kind of profile a user type is. Unknown and missing types are skipped rather than guessed at. */
fun profileKind(userType: String?): ProfileKind = when {
    userType == null -> ProfileKind.SKIP
    userType.startsWith(USER_TYPE_FULL_PREFIX) -> ProfileKind.PERSONAL
    userType == USER_TYPE_PROFILE_MANAGED -> ProfileKind.WORK
    userType == USER_TYPE_PROFILE_CLONE -> ProfileKind.CLONE
    else -> ProfileKind.SKIP
}

/**
 * Whether an app from a profile of [kind] is listed. A clone profile lists only what the person cloned: Android can put
 * preinstalled system apps in it too ([android.content.pm.ApplicationInfo.FLAG_SYSTEM], or
 * [android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP] once updated), and those would double the App Library.
 * Personal and Work list everything, as before.
 */
fun listedInProfile(kind: ProfileKind, applicationFlags: Int): Boolean =
    kind != ProfileKind.CLONE || (applicationFlags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or
        android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) == 0

/** How the app cache and layout backups write a kind next to `work`: only a clone says so, so older files read the same. */
fun storedProfileKind(kind: ProfileKind): String? = if (kind == ProfileKind.CLONE) "clone" else null

/** The kind back from [storedProfileKind] and the `work` flag beside it. */
fun storedProfileKind(stored: String?, notPersonal: Boolean): ProfileKind = when {
    !notPersonal -> ProfileKind.PERSONAL
    stored == "clone" -> ProfileKind.CLONE
    else -> ProfileKind.WORK
}

data class AppProfile(
    val userSerial: Long,
    val label: String,
    val isPersonal: Boolean,
    val isWork: Boolean,
    val quiet: Boolean,
    val unlocked: Boolean,
    val available: Boolean,
    val kind: ProfileKind = if (isPersonal) ProfileKind.PERSONAL else if (isWork) ProfileKind.WORK else ProfileKind.CLONE,
)

private const val PROFILE_APP_PREFIX = "duo-profile:v1:"

fun profileAppId(component: String, userSerial: Long, personalSerial: Long): String {
    require(component.isNotBlank())
    require(userSerial >= 0)
    return if (userSerial == personalSerial) component else "$PROFILE_APP_PREFIX$userSerial:$component"
}

fun parseProfileAppId(id: String): ProfileAppIdentity? {
    if (!id.startsWith(PROFILE_APP_PREFIX)) return id.takeIf(String::isNotBlank)?.let { ProfileAppIdentity(it, null) }
    val separator = id.indexOf(':', PROFILE_APP_PREFIX.length)
    if (separator <= PROFILE_APP_PREFIX.length || separator == id.lastIndex) return null
    val serialText = id.substring(PROFILE_APP_PREFIX.length, separator)
    if (serialText.any { !it.isDigit() }) return null
    val serial = serialText.toLongOrNull()?.takeIf { it >= 0 } ?: return null
    return ProfileAppIdentity(id.substring(separator + 1), serial)
}

/** A successful associated-profile enumeration is authoritative even when a profile is quiet or locked. */
fun removedAssociatedProfileSerials(cachedWorkSerials: Set<Long>, associatedSerials: Set<Long>): Set<Long> =
    cachedWorkSerials - associatedSerials

/**
 * The profiles that cached apps came from and Android no longer lists, work and clone alike: turning Dual Messenger
 * off removes its profile, and its apps go from Home the way a removed work profile's do.
 */
fun removedProfileSerials(cached: List<AppEntry>, associatedSerials: Set<Long>): Set<Long> =
    removedAssociatedProfileSerials(cached.filter { it.kind != ProfileKind.PERSONAL }.mapTo(mutableSetOf(), AppEntry::userSerial),
        associatedSerials)

fun removedAppIds(
    savedIds: Collection<String>,
    availableIds: Set<String>,
    authoritativeProfiles: Set<Long>,
    temporarilyUnavailable: Set<Pair<Long, String>>,
    confirmedRemoved: Set<Pair<Long, String>>,
    personalSerial: Long,
    definitivelyRemovedProfiles: Set<Long> = emptySet(),
): Set<String> = savedIds.filterTo(mutableSetOf()) { id ->
    if (isReservedFolderId(id)) return@filterTo false
    val identity = parseProfileAppId(id) ?: return@filterTo false
    val serial = identity.userSerial ?: personalSerial
    val packageName = identity.component.substringBefore('/').takeIf(String::isNotBlank) ?: return@filterTo false
    serial in definitivelyRemovedProfiles || serial to packageName in confirmedRemoved ||
        (serial in authoritativeProfiles && id !in availableIds && serial to packageName !in temporarilyUnavailable)
}

/**
 * The kind of a profile Folio isn't running in. API 35 names its user type exactly; earlier LauncherApps exposed only
 * associated managed profiles, so there every one is Work, as before. A profile claiming to be a full user is skipped.
 */
fun launcherProfileKind(launcherApps: LauncherApps, user: UserHandle): ProfileKind =
    if (Build.VERSION.SDK_INT < 35) ProfileKind.WORK
    else runCatching { profileKind(launcherApps.getLauncherUserInfo(user)?.userType) }.getOrDefault(ProfileKind.SKIP)
        .takeIf { it != ProfileKind.PERSONAL } ?: ProfileKind.SKIP

/** A work profile Folio can use (widgets restored into it, for one). */
fun isSupportedWorkProfile(launcherApps: LauncherApps, user: UserHandle): Boolean =
    launcherProfileKind(launcherApps, user) == ProfileKind.WORK
