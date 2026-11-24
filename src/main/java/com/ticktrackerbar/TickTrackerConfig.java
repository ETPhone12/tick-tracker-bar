/*
 * Copyright (c) 2026, Tick Tracker Bar contributors
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(TickTrackerConfig.GROUP)
public interface TickTrackerConfig extends Config
{
	String GROUP = "ticktrackerbar";

	@Getter
	@RequiredArgsConstructor
	enum TickMode
	{
		ONE_TICK("1 tick", 1),
		TWO_TICKS("2 ticks", 2),
		THREE_TICKS("3 ticks", 3);

		private final String label;
		private final int ticks;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@RequiredArgsConstructor
	enum HitWindow
	{
		LAST_TICK("Last tick of cycle"),
		ANY_TICK("Any tick in cycle");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@RequiredArgsConstructor
	enum InputType
	{
		GAME_ACTIONS("Game actions"),
		ITEM_USE("Item use only"),
		MOUSE_CLICKS("Any mouse click");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Getter
	@RequiredArgsConstructor
	enum DisplayMode
	{
		OVERLAY("In-game overlay", true, false),
		POP_OUT("Pop-out window", false, true),
		BOTH("Both", true, true);

		private final String label;
		private final boolean showOverlay;
		private final boolean showWindow;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Getter
	@RequiredArgsConstructor
	enum MarkStyle
	{
		LINES("Lines", false, false),
		X("X", true, false),
		X_AND_NOW_LINE("X + now line", true, true);

		private final String label;
		/** Clicks are drawn as an X instead of a line. */
		private final boolean cross;
		/** A line shows where a click would land right now. */
		private final boolean nowLine;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigSection(
		name = "Tick bar",
		description = "How the tick cycle and hits work",
		position = 0
	)
	String tickSection = "tickSection";

	@ConfigSection(
		name = "Display",
		description = "Where and how the tracker is shown",
		position = 1
	)
	String displaySection = "displaySection";

	@ConfigSection(
		name = "Colors",
		description = "Colors of the bar and marks",
		position = 2,
		closedByDefault = true
	)
	String colorSection = "colorSection";

	@ConfigItem(
		keyName = "tickMode",
		name = "Tick cycle",
		description = "How many game ticks the bar takes to fill up before it resets.<br>"
			+ "1 tick for 1t actions and prayer flicking, 2 or 3 ticks for 2t / 3t skilling.",
		position = 0,
		section = tickSection
	)
	default TickMode tickMode()
	{
		return TickMode.ONE_TICK;
	}

	@ConfigItem(
		keyName = "hitWindow",
		name = "Hit window",
		description = "Last tick of cycle: click during the final tick before the bar resets (the green zone).<br>"
			+ "Any tick in cycle: one click anywhere before the bar resets counts.",
		position = 1,
		section = tickSection
	)
	default HitWindow hitWindow()
	{
		return HitWindow.LAST_TICK;
	}

	@ConfigItem(
		keyName = "syncOnFirstClick",
		name = "Sync to first click",
		description = "On 2 / 3 tick cycles, the first click of a streak sets which tick is the target,<br>"
			+ "so the cycle follows your own rhythm. Right after a miss, a click on another tick only<br>"
			+ "restarts the rhythm (a yellow mark, or cyan if it was too late for its tick) and doesn't count;<br>"
			+ "a click in the green zone is still a hit.",
		position = 2,
		section = tickSection
	)
	default boolean syncOnFirstClick()
	{
		return true;
	}

	@ConfigItem(
		keyName = "inputType",
		name = "Count",
		description = "Game actions: anything you do in the game (walk, attack, use, menu options), except dropping<br>"
			+ "items and picking an item to use.<br>"
			+ "Item use only: only using an item on something, like herb on tar or knife on logs (tick manipulation).<br>"
			+ "Any mouse click: every mouse button press on the game screen.",
		position = 3,
		section = tickSection
	)
	default InputType inputType()
	{
		return InputType.GAME_ACTIONS;
	}

	@Range(max = TickTracker.MAX_INPUT_LAG_MS)
	@Units(Units.MILLISECONDS)
	@ConfigItem(
		keyName = "inputLag",
		name = "Input lag",
		description = "Your ping plus the time the server takes to read a click. The game reads clicks as each tick starts,<br>"
			+ "so a click made closer than this to the end of a tick counts for the next tick. The bar shades that part<br>"
			+ "at its end and moves the green zone earlier to match. 0 counts every click in the tick the bar shows.<br>"
			+ "The World Lag plugin can measure it for you; otherwise try your ping plus about 100 (more on a busy world).",
		position = 4,
		section = tickSection
	)
	default int inputLag()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "resetHotkey",
		name = "Reset hotkey",
		description = "Resets the streak, best, hits and misses. You can also Shift + right-click the overlay,<br>"
			+ "or use the pop-out window's Reset button.",
		position = 5,
		section = tickSection
	)
	default Keybind resetHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "displayMode",
		name = "Show as",
		description = "In-game overlay (shown once you're logged in; Alt + drag to move it, Shift + right-click it<br>"
			+ "to reset or pop it out), a pop-out window you can drag anywhere on any monitor, or both",
		position = 0,
		section = displaySection
	)
	default DisplayMode displayMode()
	{
		return DisplayMode.OVERLAY;
	}

	@ConfigItem(
		keyName = "alwaysOnTop",
		name = "Window always on top",
		description = "Keep the pop-out window above other apps too. It always stays above RuneLite,<br>"
			+ "and hides while RuneLite is minimized.",
		position = 1,
		section = displaySection
	)
	default boolean alwaysOnTop()
	{
		return true;
	}

	@ConfigItem(
		keyName = "lockWindow",
		name = "Lock window position",
		description = "Stop the pop-out window from being dragged by accident",
		position = 2,
		section = displaySection
	)
	default boolean lockWindow()
	{
		return false;
	}

	@Range(min = 20, max = 100)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "windowOpacity",
		name = "Window opacity",
		description = "How see-through the pop-out window is",
		position = 3,
		section = displaySection
	)
	default int windowOpacity()
	{
		return 100;
	}

	@Range(min = BarStyle.MIN_BAR_WIDTH, max = BarStyle.MAX_BAR_WIDTH)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "barWidth",
		name = "Bar width",
		description = "Width of the tick bar",
		position = 4,
		section = displaySection
	)
	default int barWidth()
	{
		return BarStyle.DEFAULT_BAR_WIDTH;
	}

	@Range(min = BarStyle.MIN_BAR_HEIGHT, max = BarStyle.MAX_BAR_HEIGHT)
	@Units(Units.PIXELS)
	@ConfigItem(
		keyName = "barHeight",
		name = "Bar height",
		description = "Height of the tick bar",
		position = 5,
		section = displaySection
	)
	default int barHeight()
	{
		return BarStyle.DEFAULT_BAR_HEIGHT;
	}

	@ConfigItem(
		keyName = "markStyle",
		name = "Click marks",
		description = "How your clicks show on the bar.<br>"
			+ "Lines: a thin line where you clicked.<br>"
			+ "X: a cross, like the one the game draws where you click.<br>"
			+ "X + now line: crosses, plus a white line that shows where a click would land right now.",
		position = 6,
		section = displaySection
	)
	default MarkStyle markStyle()
	{
		return MarkStyle.X_AND_NOW_LINE;
	}

	@ConfigItem(
		keyName = "showStats",
		name = "Show stats",
		description = "Show streak, best streak, hits, misses and accuracy under the bar",
		position = 7,
		section = displaySection
	)
	default boolean showStats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showLastMarks",
		name = "Show last cycle's marks",
		description = "Keep a faded copy of where you clicked in the previous cycle, so you can compare. After a missed<br>"
			+ "cycle it also shows its clicks on the wrong tick in red.",
		position = 8,
		section = displaySection
	)
	default boolean showLastMarks()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "colorFill",
		name = "Bar fill",
		description = "Fill color while the bar is waiting for your click",
		position = 0,
		section = colorSection
	)
	default Color barColor()
	{
		return BarStyle.DEFAULT_BAR_COLOR;
	}

	@Alpha
	@ConfigItem(
		keyName = "colorHit",
		name = "Hit",
		description = "Hit marks, the HIT text and the flash on a hit. Once you've hit this cycle the bar turns a darker<br>"
			+ "shade of it, so the hit's own mark shows up on it.",
		position = 1,
		section = colorSection
	)
	default Color hitColor()
	{
		return BarStyle.DEFAULT_HIT_COLOR;
	}

	@Alpha
	@ConfigItem(
		keyName = "colorMiss",
		name = "Miss",
		description = "Flash and text on a miss, and the clicks on the wrong tick in a cycle you missed",
		position = 2,
		section = colorSection
	)
	default Color missColor()
	{
		return BarStyle.DEFAULT_MISS_COLOR;
	}

	@Alpha
	@ConfigItem(
		keyName = "colorZone",
		name = "Hit zone",
		description = "Shading of the target tick on 2 and 3 tick cycles",
		position = 3,
		section = colorSection
	)
	default Color zoneColor()
	{
		return BarStyle.DEFAULT_ZONE_COLOR;
	}

	@Alpha
	@ConfigItem(
		keyName = "colorBackground",
		name = "Background",
		description = "Background of the overlay and the pop-out window. The pop-out ignores this color's<br>"
			+ "transparency: use Window opacity to make it see-through.",
		position = 4,
		section = colorSection
	)
	default Color backgroundColor()
	{
		return BarStyle.DEFAULT_BACKGROUND_COLOR;
	}
}
