/*
 * Copyright (c) 2026, ETPhone12
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import java.awt.Color;
import lombok.Value;

/** What the painter needs from the config, read once per config change instead of every frame. */
@Value
class BarStyle
{
	// Explicitly public: @Value would otherwise make these private.
	/** Leaves room for HIT / MISS beside the 3-tick boxes and the pop-out window's buttons. */
	public static final int MIN_BAR_WIDTH = 160;
	public static final int MAX_BAR_WIDTH = 800;
	public static final int DEFAULT_BAR_WIDTH = 220;
	public static final int MIN_BAR_HEIGHT = 8;
	public static final int MAX_BAR_HEIGHT = 60;
	public static final int DEFAULT_BAR_HEIGHT = 18;

	// The default colors are the game's own interface colors.
	/** The orange of the game's interface titles. */
	public static final Color DEFAULT_BAR_COLOR = new Color(255, 152, 31);
	public static final Color DEFAULT_HIT_COLOR = new Color(32, 210, 32);
	public static final Color DEFAULT_MISS_COLOR = new Color(230, 25, 25);
	public static final Color DEFAULT_ZONE_COLOR = new Color(32, 210, 32, 80);
	/** The brown of the game's interface panels, slightly see-through over the game. */
	public static final Color DEFAULT_BACKGROUND_COLOR = new Color(62, 53, 41, 230);

	int barWidth;
	int barHeight;
	boolean showStats;
	boolean showLastMarks;
	TickTrackerConfig.MarkStyle markStyle;
	Color barColor;
	Color hitColor;
	Color missColor;
	Color zoneColor;
	Color backgroundColor;

	static BarStyle from(TickTrackerConfig config)
	{
		return new BarStyle(
			clamp(config.barWidth(), MIN_BAR_WIDTH, MAX_BAR_WIDTH),
			clamp(config.barHeight(), MIN_BAR_HEIGHT, MAX_BAR_HEIGHT),
			config.showStats(),
			config.showLastMarks(),
			config.markStyle(),
			config.barColor(),
			config.hitColor(),
			config.missColor(),
			config.zoneColor(),
			config.backgroundColor());
	}

	static BarStyle defaults()
	{
		return new BarStyle(
			DEFAULT_BAR_WIDTH,
			DEFAULT_BAR_HEIGHT,
			true,
			true,
			TickTrackerConfig.MarkStyle.X_AND_NOW_LINE,
			DEFAULT_BAR_COLOR,
			DEFAULT_HIT_COLOR,
			DEFAULT_MISS_COLOR,
			DEFAULT_ZONE_COLOR,
			DEFAULT_BACKGROUND_COLOR);
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
