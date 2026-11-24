/*
 * Copyright (c) 2026, Tick Tracker Bar contributors
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import java.util.List;
import lombok.Value;

/** An unchanging picture of the {@link TickTracker} at one moment, used for drawing a frame. */
@Value
public class TickSnapshot
{
	public enum MarkType
	{
		/** The click that hit the target tick. */
		HIT,
		/** Another click on a target tick that was already hit (or already used to re-sync). */
		EXTRA,
		/**
		 * A click on a tick that isn't the target tick. Shown as neutral: in 2t / 3t methods most of
		 * these are normal clicks. If the cycle ends in a miss they become {@link #WRONG_TICK}.
		 */
		OFF_TICK,
		/** A click on another tick in a cycle that ended in a miss. */
		WRONG_TICK,
		/** The click that restarted the rhythm right after a miss. It lines the cycle up but doesn't count. */
		SYNC,
		/**
		 * A click too close to the end of the cycle for the game to take it in that tick (see the input lag
		 * setting). It is scored with the next tick instead, in the next cycle.
		 */
		LATE,
	}

	public enum Result
	{
		NONE,
		HIT,
		MISS,
	}

	@Value
	public static class Mark
	{
		/** Where on the bar the click happened: 0 is the left edge, 1 the right edge. */
		double position;
		MarkType type;
	}

	/** No game tick has arrived recently (logged out, loading or frozen). */
	boolean waiting;
	int cycleLength;
	boolean lastTickOnly;
	/** 0-based tick within the current cycle. */
	int tickInCycle;
	/** How full the bar is, 0 to 1. */
	double progress;
	boolean hitThisCycle;
	List<Mark> marks;
	/** Marks from the cycle before this one, drawn faded. */
	List<Mark> previousMarks;
	int streak;
	int bestStreak;
	int hits;
	int misses;
	Result lastResult;
	/** Milliseconds since the last hit or miss. */
	long resultAgeMs;
	/**
	 * How much of each tick, at its end, is too late for a click to count in that tick: the input lag as a
	 * share of a tick, 0 to 1. Clicks there count for the next tick.
	 */
	double lateWindow;

	/** Percentage of attempted cycles that were hits, or -1 before the first attempt. */
	public int getAccuracy()
	{
		int attempts = hits + misses;
		return attempts == 0 ? -1 : (int) (hits * 100L / attempts);
	}
}
