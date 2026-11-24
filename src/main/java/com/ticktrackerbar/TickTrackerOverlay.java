/*
 * Copyright (c) 2026, Tick Tracker Bar contributors
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The tick bar drawn inside the game window. Like every RuneLite overlay it can be moved with Alt + drag,
 * and its menu ("Reset stats" and "Pop out") shows on Shift + right-click. RuneLite only draws overlays
 * while you're logged in.
 */
class TickTrackerOverlay extends Overlay
{
	private static final String TARGET = "Tick tracker";

	private final TickTrackerPlugin plugin;
	private final TickTrackerConfig config;
	private final TickBarPainter painter = new TickBarPainter();

	@Inject
	TickTrackerOverlay(TickTrackerPlugin plugin, TickTrackerConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset stats", TARGET, e -> plugin.resetStats());
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Pop out", TARGET,
			e -> plugin.setDisplayMode(TickTrackerConfig.DisplayMode.POP_OUT));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.displayMode().isShowOverlay())
		{
			return null;
		}

		TickSnapshot snapshot = plugin.getTracker().snapshot(System.nanoTime());
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			return painter.paint(g, snapshot, plugin.getStyle(), 0, false);
		}
		finally
		{
			g.dispose();
		}
	}
}
