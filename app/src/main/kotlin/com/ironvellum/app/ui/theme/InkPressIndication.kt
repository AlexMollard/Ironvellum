package com.ironvellum.app.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.launch

/** Press ink covers the control immediately and respects its existing shape clip. */
internal object InkPressIndication : IndicationNodeFactory {
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 0
    override fun create(interactionSource: InteractionSource): DelegatableNode = InkPressNode(interactionSource)

    private class InkPressNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
        private var highlighted by mutableStateOf(false)

        override fun onAttach() {
            coroutineScope.launch {
                val active = mutableSetOf<Interaction>()
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press, is FocusInteraction.Focus, is HoverInteraction.Enter -> active.add(interaction)
                        is PressInteraction.Release -> active.remove(interaction.press)
                        is PressInteraction.Cancel -> active.remove(interaction.press)
                        is FocusInteraction.Unfocus -> active.remove(interaction.focus)
                        is HoverInteraction.Exit -> active.remove(interaction.enter)
                    }
                    highlighted = active.isNotEmpty()
                }
            }
        }

        override fun onDetach() { highlighted = false }

        override fun ContentDrawScope.draw() {
            drawContent()
            if (highlighted) drawRect(IronvellumColors.Ink.copy(alpha = 0.12f))
        }
    }
}
