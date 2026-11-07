package com.mccal.folio

import androidx.annotation.StringRes

/**
 * One setting a tweak offers on its own page in Settings › Tweaks, beyond its switch and the screens it runs on.
 * Values are stored as short strings under the tweak's id (`LauncherState.tweakOptions`), so a tweak that has never
 * been touched stores nothing and every default is the behavior the tweak had before it had options.
 */
internal sealed interface TweakOption {
    val id: String
    @get:StringRes val label: Int
    val default: String
    @get:StringRes val section: Int? get() = null

    /** A switch. Stored as [ON] or [OFF]. */
    data class Toggle(
        override val id: String,
        @StringRes override val label: Int,
        val on: Boolean,
        @StringRes override val section: Int? = null,
    ) : TweakOption {
        override val default get() = if (on) TweakOptions.ON else TweakOptions.OFF
    }

    /** One of a few named choices, each a stored value and the words for it. */
    data class Choice(
        override val id: String,
        @StringRes override val label: Int,
        val choices: List<Pair<String, Int>>,
        override val default: String,
        @StringRes override val section: Int? = null,
    ) : TweakOption
}

/**
 * The options each tweak offers, and how to read and change them. Pure, so the rules are tested without a phone:
 * a stored value that is not a real choice is ignored, and a value equal to the default is not stored at all, which
 * makes "reset" and "never touched" the same thing.
 */
internal object TweakOptions {
    const val ON = "on"
    const val OFF = "off"

    private val table: Map<String, List<TweakOption>> = mapOf(
        // Harbor's swell: how far the icon under your finger grows (the old fixed value is Standard), and the tick
        // you feel as the finger crosses from one app to the next.
        "dockMagnify" to listOf(
            TweakOption.Choice("amount", R.string.tweak_opt_amount, listOf(
                "subtle" to R.string.tweak_opt_subtle, "standard" to R.string.standard, "strong" to R.string.tweak_opt_strong), "standard"),
            TweakOption.Toggle("tick", R.string.tweak_opt_tick, on = true),
        ),
        // ColorFlow's two places: the Now Playing card, and the sound bars in the island. Both on is what it always did.
        "tintMedia" to listOf(
            TweakOption.Toggle("card", R.string.tweak_opt_music_card, on = true),
            TweakOption.Toggle("island", R.string.tweak_opt_island_bars, on = true),
        ),
        // Palette: notification card colors, app shortcut menu, and dynamic island tinting.
        "tintNotifications" to listOf(
            TweakOption.Toggle("appMenu", R.string.tweak_opt_app_menu, on = true),
            TweakOption.Toggle("island", R.string.tweak_opt_island_tint, on = true),
        ),
        // StandBy 2: clock weight, player card style, Up Next, timers, notification colors, night mode, and night brightness.
        STANDBY2_ID to listOf(
            TweakOption.Choice("clock", R.string.standby2_clock, listOf(
                "thin" to R.string.standby2_clock_thin,
                "bold" to R.string.standby2_clock_bold,
                "analog" to R.string.standby2_clock_analog,
                "numerals" to R.string.standby2_clock_numerals,
                "flip" to R.string.standby2_clock_flip,
                "world" to R.string.standby2_clock_world,
                "solar" to R.string.standby2_clock_solar,
                "words" to R.string.standby2_clock_words,
                "stack" to R.string.standby2_clock_stack,
                "neon" to R.string.standby2_clock_neon,
                "nixie" to R.string.standby2_clock_nixie,
                "minimal" to R.string.standby2_clock_minimal,
            ), "thin", section = R.string.look),
            TweakOption.Choice("clockColor", R.string.standby2_clock_color, listOf(
                "white" to R.string.standby2_color_white,
                "amber" to R.string.standby2_color_amber,
                "cyan" to R.string.standby2_color_blue,
                "coral" to R.string.standby2_color_coral,
                "mint" to R.string.standby2_color_mint,
                "purple" to R.string.standby2_color_purple,
            ), "white", section = R.string.look),
            TweakOption.Toggle("ambientLamp", R.string.standby2_ambient_lamp, on = true, section = R.string.look),
            TweakOption.Toggle("steady", R.string.standby2_steady, on = true, section = R.string.look),
            TweakOption.Choice("player", R.string.standby2_now_playing, listOf(
                "card" to R.string.standby2_card, "compact" to R.string.standby2_compact, "off" to R.string.off), "card", section = R.string.look),
            TweakOption.Toggle("upNext", R.string.standby2_up_next, on = true, section = R.string.standby2_cards),
            TweakOption.Toggle("timers", R.string.standby2_timers, on = true, section = R.string.standby2_cards),
            TweakOption.Choice("notif", R.string.standby2_glow_dots, listOf(
                "colors" to R.string.standby2_colors, "count" to R.string.standby2_count_only, "off" to R.string.off), "colors", section = R.string.standby2_cards),
            TweakOption.Choice("nightMode", R.string.standby2_night_mode, listOf(
                "schedule" to R.string.standby2_night_schedule, "room" to R.string.standby2_night_room, "never" to R.string.standby2_night_never), "schedule", section = R.string.standby2_night_mode),
            TweakOption.Choice("nightColor", R.string.standby2_night_color, listOf(
                "red" to R.string.standby2_color_red, "amber" to R.string.standby2_color_amber, "blue" to R.string.standby2_color_blue), "red", section = R.string.standby2_night_mode),
            TweakOption.Choice("nightBrightness", R.string.standby2_night_brightness, listOf(
                "0" to R.string.off, "20" to R.string.standby2_bright_20, "40" to R.string.standby2_bright_40,
                "60" to R.string.standby2_bright_60, "80" to R.string.standby2_bright_80, "100" to R.string.standby2_bright_100), "60", section = R.string.standby2_night_mode),
            TweakOption.Toggle("sunrise", R.string.standby2_sunrise, on = true, section = R.string.standby2_night_mode),
            TweakOption.Toggle("offCharger", R.string.standby2_off_charger, on = false, section = R.string.battery),
        ),
        DUET_ID to listOf(
            TweakOption.Choice("caseFit", R.string.case_fit_edge_clearance, listOf(
                "none" to R.string.case_fit_none, "compact" to R.string.case_fit_compact, "generous" to R.string.case_fit_generous), "none", section = R.string.case_fit_section),
            TweakOption.Toggle("tabletop", R.string.tabletop_controls, on = false, section = R.string.tabletop_controls),
        ),
    )

    fun of(tweakId: String): List<TweakOption> = table[tweakId].orEmpty()

    private fun option(tweakId: String, optionId: String) = of(tweakId).firstOrNull { it.id == optionId }

    /** What the option is now: the stored value if it is a real one, else its default. Unknown options read as "". */
    fun value(options: Map<String, Map<String, String>>, tweakId: String, optionId: String): String {
        val option = option(tweakId, optionId) ?: return ""
        val stored = options[tweakId]?.get(optionId) ?: return option.default
        return if (isValid(option, stored)) stored else option.default
    }

    fun on(options: Map<String, Map<String, String>>, tweakId: String, optionId: String) = value(options, tweakId, optionId) == ON

    /** [options] with one value changed. An unknown option or value changes nothing; the default is stored as nothing. */
    fun set(options: Map<String, Map<String, String>>, tweakId: String, optionId: String, value: String): Map<String, Map<String, String>> {
        val option = option(tweakId, optionId) ?: return options
        if (!isValid(option, value)) return options
        val mine = options[tweakId].orEmpty().let { if (value == option.default) it - optionId else it + (optionId to value) }
        return if (mine.isEmpty()) options - tweakId else options + (tweakId to mine)
    }

    /** [options] with everything for [tweakId] swapped for [values] (what a package restore puts back), kept to what this build knows. */
    fun replace(options: Map<String, Map<String, String>>, tweakId: String, values: Map<String, String>): Map<String, Map<String, String>> =
        cleaned(options - tweakId + (tweakId to values))

    /** Only what this build knows: a save from a newer Folio may name an option or a value that is not here. */
    fun cleaned(options: Map<String, Map<String, String>>): Map<String, Map<String, String>> =
        options.mapValues { (tweak, values) -> values.filter { (id, value) -> option(tweak, id)?.let { isValid(it, value) } == true } }
            .filterValues { it.isNotEmpty() }

    private fun isValid(option: TweakOption, value: String) = when (option) {
        is TweakOption.Toggle -> value == ON || value == OFF
        is TweakOption.Choice -> option.choices.any { it.first == value }
    }

    /** Harborline's swell at the peak: 1.0 plus this, at the icon under the finger. Standard is the value it always had. */
    fun magnifyAmount(options: Map<String, Map<String, String>>): Float = when (value(options, "dockMagnify", "amount")) {
        "subtle" -> .2f
        "strong" -> .55f
        else -> .38f
    }
}
