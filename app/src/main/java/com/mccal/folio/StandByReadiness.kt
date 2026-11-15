package com.mccal.folio

/** What StandBy asks of the phone before it comes on. Each is a yes or no the person can fix. */
internal enum class StandByRequirement { PLUGGED, UNPLUGGED, SIDEWAYS, STILL, HALF_OPEN, ON_COVER, TENT_POSE, BATTERY }

/** The ways StandBy can start, as the settings list them. Off the charger is StandBy 2's addition to the charging way. */
internal enum class StandByRoute { HALF_OPEN, CHARGING, OFF_CHARGER, TENT }

/** One route, the requirements it has and whether each holds right now. */
internal data class StandByReadiness(val route: StandByRoute, val checks: List<Pair<StandByRequirement, Boolean>>) {
    val ready: Boolean get() = checks.all { it.second }
    val missing: List<StandByRequirement> get() = checks.filter { !it.second }.map { it.first }
}

/** The battery level at which off-charger StandBy stops starting, the same line [standByWay] draws. */
internal const val STANDBY_OFF_CHARGER_MIN_BATTERY = 20

/**
 * For every route that is switched on, which requirements hold and which do not, so Settings can say what StandBy is
 * waiting for. It mirrors [standByWay] rule for rule (a test checks the two never disagree): a route is ready exactly
 * when [standByWay] would pick it.
 */
internal fun standByReadiness(ways: StandByWays, s: StandBySignals): List<StandByReadiness> = buildList {
    if (ways.halfOpen) add(StandByReadiness(StandByRoute.HALF_OPEN, listOf(StandByRequirement.HALF_OPEN to s.halfOpen)))
    if (ways.charging) add(StandByReadiness(StandByRoute.CHARGING, listOf(
        StandByRequirement.PLUGGED to s.plugged, StandByRequirement.SIDEWAYS to s.sideways, StandByRequirement.STILL to s.still)))
    if (ways.offCharger) add(StandByReadiness(StandByRoute.OFF_CHARGER, listOf(
        StandByRequirement.UNPLUGGED to !s.plugged, StandByRequirement.SIDEWAYS to s.sideways, StandByRequirement.STILL to s.still,
        StandByRequirement.BATTERY to (s.batteryLevel == null || s.batteryLevel >= STANDBY_OFF_CHARGER_MIN_BATTERY))))
    if (ways.tent) add(StandByReadiness(StandByRoute.TENT, listOf(
        StandByRequirement.ON_COVER to s.onCover, StandByRequirement.TENT_POSE to (s.tentState ?: s.hingeHalfway),
        StandByRequirement.SIDEWAYS to s.sideways, StandByRequirement.STILL to s.still)))
}

/**
 * The one thing to tell the person first: the requirement a route is closest to meeting, or null when a route is ready.
 * "Closest" is the route with the fewest missing requirements; ties go to the earlier route in [StandByRoute].
 */
internal fun standByNextStep(readiness: List<StandByReadiness>): StandByRequirement? {
    if (readiness.isEmpty() || readiness.any { it.ready }) return null
    return readiness.minByOrNull { it.missing.size }?.missing?.first()
}
