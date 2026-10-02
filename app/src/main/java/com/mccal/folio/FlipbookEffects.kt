package com.mccal.folio

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Flipbook's effects, on its own page: the built-in ones, then the ones Market packages added. Flipbook is their host,
 * the way jailbreak Cylinder is for its scripts. Choosing one turns Flipbook on; the Enabled switch above turns it off.
 */
@Composable
internal fun FlipbookEffects(state: LauncherState, onEffect: (PageEffect) -> Unit, onPackaged: (String) -> Unit) {
    // The same rule Home draws by (pageEffectSpec), so the checkmark can't disagree with the page turning.
    val packaged = state.activePackagedEffect
    SettingsCard(stringResource(R.string.page_effects)) {
        // The card draws the line between its rows itself.
        for (effect in PageEffect.CHOICES) {
            IosCheckRow(stringResource(effect.label), packaged == null && state.pageEffect == effect, { onEffect(effect) },
                "page-effect-${effect.name.lowercase()}")
        }
        CardNote(stringResource(R.string.home_pages_turn_in_3d_as_you_swipe_off_w))
    }
    if (state.packagedEffects.isNotEmpty()) SettingsCard(stringResource(R.string.from_packages)) {
        for (effect in state.packagedEffects) {
            IosCheckRow(effect.name, packaged?.id == effect.id, { onPackaged(effect.id) }, "page-effect-package-${effect.id}")
        }
    }
}
