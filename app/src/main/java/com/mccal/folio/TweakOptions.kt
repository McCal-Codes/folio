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

    /** A switch. Stored as [ON] or [OFF]. */
    data class Toggle(override val id: String, @StringRes override val label: Int, val on: Boolean) : TweakOption {
        override val default get() = if (on) TweakOptions.ON else TweakOptions.OFF
    }

    /** One of a few named choices, each a stored value and the words for it. */
    data class Choice(
        override val id: String, @StringRes override val label: Int,
        val choices: List<Pair<String, Int>>, override val default: String,
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
