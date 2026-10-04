package dev.afkmod.client.gui;

import dev.afkmod.gui.TooltipText;

/** A tooltip to show this frame, anchored at a pointer or icon position. */
record TooltipRequest(TooltipText text, int anchorX, int anchorY) {
}
