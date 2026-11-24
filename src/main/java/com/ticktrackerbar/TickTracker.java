/*
 * Copyright (c) 2026, Tick Tracker Bar contributors
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The timing brain of the plugin. It knows nothing about RuneLite: the plugin feeds it real game
 * ticks and the player's clicks (as {@link System#nanoTime()} timestamps), and the overlay and the
 * pop-out window draw a {@link TickSnapshot} of it every frame.
 *
 * <p>A <b>cycle</b> is 1, 2 or 3 game ticks long. The bar fills up over the cycle and resets on the
 * game tick that starts the next cycle. The <b>target tick</b> is the last tick of the cycle (or every
 * tick of it when the hit window is "any tick"). A click during the target tick is a <b>hit</b>. If
 * the cycle ends without a hit while you are on a streak (or after clicking on the wrong tick), that
 * is a <b>miss</b> and the streak resets. Doing nothing at all is idle time and is not counted.
 *
 * <p><b>Sync to first click</b> (2t / 3t with the last-tick window): when you're not on a streak, a
 * click on another tick moves the cycle so that this tick becomes the target. On a fresh start (the
 * first click after logging in, a reset or a cycle change) that click counts as a hit. Straight after
 * a miss it doesn't: it only restarts the rhythm, the cycle it lands in isn't judged, and the next
 * cycle is judged as if you were on a streak. Otherwise a late click would be scored twice, once as
 * the miss and once as a hit, and clicking with the wrong rhythm would never drop below 50%.
 *
 * <p><b>Input lag</b>: the game takes in clicks once a tick, as the tick starts, and a click needs time to
 * get there (ping plus the server's own delay). The bar resets when the client hears about a tick, which is
 * later still. So a click made in the last moments of a tick on the bar reaches the game in time for the
 * tick after. With an input lag set, a click that close to the end of its tick counts for the next tick: at
 * once when that tick is in the same cycle, and when the tick arrives when it starts a new cycle. Like the
 * game's own clock, the end of a tick is taken to be 600 ms after it started.
 *
 * <p>Thread safety: game ticks and menu clicks arrive on the client thread, while mouse and keyboard
 * listeners and the pop-out window run on the Swing thread, so every public method is synchronized.
 */
public class TickTracker
{
	/** Length of one game tick. The bar is timed with this and re-synced on every real game tick. */
	public static final long TICK_NANOS = 600_000_000L;

	/** No game tick for this long (4 ticks) means logged out, loading or frozen: show "waiting". */
	static final long STALL_NANOS = 4 * TICK_NANOS;

	/** Spam-clicking shouldn't grow the mark list forever. */
	private static final int MAX_MARKS_PER_CYCLE = 32;

	/** The most input lag the setting allows. Under a tick, so a click never moves more than one tick. */
	public static final int MAX_INPUT_LAG_MS = 500;

	private int cycleLength = 1;
	private boolean lastTickOnly = true;
	private boolean syncOnFirstClick = true;
	private long inputLagNanos;

	private boolean ticking;
	private long tickCount;
	private long lastTickNanos;
	private long cycleStartTick;

	private boolean hitThisCycle;
	private boolean earlyThisCycle;
	/** This cycle was lined up by a re-sync click after a miss, so its end isn't judged. */
	private boolean syncCycle;
	/** The cycle after a re-sync: judged like a streak even though the streak is 0, and can't re-sync. */
	private boolean judgeThisCycle;
	/** A miss happened since the last hit (or reset, login or cycle change): re-syncing doesn't score. */
	private boolean recovering;
	/** Clicks too late for this cycle, waiting to be scored with the tick that starts the next one. */
	private int lateClicks;
	private List<TickSnapshot.Mark> marks = new ArrayList<>();
	private List<TickSnapshot.Mark> previousMarks = Collections.emptyList();

	private int streak;
	private int bestStreak;
	private int hits;
	private int misses;
	private TickSnapshot.Result lastResult = TickSnapshot.Result.NONE;
	private long lastResultNanos;

	/**
	 * @param cycleLength      game ticks per cycle (1, 2 or 3)
	 * @param lastTickOnly     true: only a click on the last tick of the cycle is a hit; false: any tick
	 * @param syncOnFirstClick when not on a streak, line the cycle up with the player's next click
	 * @param inputLagMs       how long before the end of a tick a click must be made to count in that tick
	 *                         (0 to {@link #MAX_INPUT_LAG_MS}); clicks after that count for the next tick
	 */
	public synchronized void configure(int cycleLength, boolean lastTickOnly, boolean syncOnFirstClick, int inputLagMs)
	{
		int length = Math.max(1, cycleLength);
		boolean cycleChanged = length != this.cycleLength || lastTickOnly != this.lastTickOnly;
		this.cycleLength = length;
		this.lastTickOnly = lastTickOnly;
		this.syncOnFirstClick = syncOnFirstClick;
		this.inputLagNanos = Math.max(0, Math.min(MAX_INPUT_LAG_MS, inputLagMs)) * 1_000_000L;
		if (cycleChanged)
		{
			// A different cycle is a fresh start, not a miss.
			streak = 0;
			recovering = false;
			startNewCycle();
			lateClicks = 0;
			previousMarks = Collections.emptyList();
		}
	}

	/** Call on every game tick (RuneLite's GameTick event). */
	public synchronized void onGameTick(long now)
	{
		lastTickNanos = now;
		if (!ticking)
		{
			ticking = true;
			tickCount = 0;
			startNewCycle();
			lateClicks = 0;
			return;
		}

		tickCount++;
		boolean missed = false;
		if (tickCount - cycleStartTick >= cycleLength)
		{
			boolean judgeNext = syncCycle;
			missed = finishCycle(now);
			startNewCycle();
			judgeThisCycle = judgeNext;
		}

		// Clicks from the end of the last cycle that the game takes in with this tick. Their marks are
		// already on the bar, where they were made.
		int late = lateClicks;
		lateClicks = 0;
		for (int i = 0; i < late; i++)
		{
			score(now, 0, false);
		}
		if (missed && late > 0)
		{
			// The cycle that just ended had nothing in time: keep showing that miss, even though a late click
			// already counts for this cycle.
			setResult(TickSnapshot.Result.MISS, now);
		}
	}

	/** Call whenever the player clicks or performs an action. */
	public synchronized void onInput(long now)
	{
		if (!ticking || now - lastTickNanos > STALL_NANOS)
		{
			return;
		}

		// Too close to the end of the tick for the game to take it in with this one: it counts for the next.
		int ahead = inputLagNanos > 0 && now - lastTickNanos + inputLagNanos >= TICK_NANOS ? 1 : 0;
		if (positionInCycle() + ahead >= cycleLength)
		{
			// That tick starts the next cycle, so score it once the tick arrives.
			lateClicks = Math.min(lateClicks + 1, MAX_MARKS_PER_CYCLE);
			addMark(now, TickSnapshot.MarkType.LATE);
			return;
		}
		score(now, ahead, true);
	}

	/**
	 * Scores a click as one on the tick {@code ahead} ticks after the current one (0 or 1, still in this cycle).
	 *
	 * @param mark put a mark on the bar for it (a late click already has one)
	 */
	private void score(long now, int ahead, boolean mark)
	{
		boolean onTarget = isTargetTick(ahead);
		if (!onTarget && syncOnFirstClick && streak == 0 && !hitThisCycle && !syncCycle && !judgeThisCycle)
		{
			// Not on a streak, so make the click's tick the target: the 2t / 3t cycle follows the player's rhythm.
			cycleStartTick = tickCount + ahead - (cycleLength - 1);
			marks.clear();
			earlyThisCycle = false;
			if (recovering)
			{
				// Straight after a miss this click only restarts the rhythm. Scoring it would turn a late
				// click into a hit right after counting it as a miss.
				syncCycle = true;
				if (mark)
				{
					addMark(now, TickSnapshot.MarkType.SYNC);
				}
				return;
			}
			onTarget = true;
		}

		TickSnapshot.MarkType type;
		if (!onTarget)
		{
			earlyThisCycle = true;
			type = TickSnapshot.MarkType.OFF_TICK;
		}
		else if (hitThisCycle || syncCycle)
		{
			type = TickSnapshot.MarkType.EXTRA;
		}
		else
		{
			hitThisCycle = true;
			recovering = false;
			hits++;
			streak++;
			bestStreak = Math.max(bestStreak, streak);
			setResult(TickSnapshot.Result.HIT, now);
			type = TickSnapshot.MarkType.HIT;
		}
		if (mark)
		{
			addMark(now, type);
		}
	}

	/** Logged out or hopping: stop the bar and end the streak without counting a miss. */
	public synchronized void pause()
	{
		ticking = false;
		streak = 0;
		recovering = false;
		startNewCycle();
		lateClicks = 0;
		previousMarks = Collections.emptyList();
	}

	/** Clears the streak, best streak, hits and misses. The next click is a fresh start. */
	public synchronized void resetStats()
	{
		streak = 0;
		bestStreak = 0;
		hits = 0;
		misses = 0;
		lastResult = TickSnapshot.Result.NONE;
		hitThisCycle = false;
		earlyThisCycle = false;
		syncCycle = false;
		judgeThisCycle = false;
		recovering = false;
		lateClicks = 0;
		marks.clear();
		previousMarks = Collections.emptyList();
	}

	public synchronized TickSnapshot snapshot(long now)
	{
		boolean waiting = !ticking || now - lastTickNanos > STALL_NANOS;
		long resultAgeMs = lastResult == TickSnapshot.Result.NONE
			? Long.MAX_VALUE
			: (now - lastResultNanos) / 1_000_000L;

		return new TickSnapshot(
			waiting,
			cycleLength,
			lastTickOnly,
			ticking ? positionInCycle() : 0,
			ticking ? progress(now) : 0,
			hitThisCycle,
			new ArrayList<>(marks),
			previousMarks,
			streak,
			bestStreak,
			hits,
			misses,
			lastResult,
			resultAgeMs,
			inputLagNanos / (double) TICK_NANOS);
	}

	/** Scores the cycle that just ended. Returns whether it was a miss. */
	private boolean finishCycle(long now)
	{
		// Only a miss if you were trying: on a streak, just re-synced, or clicked on the wrong tick.
		boolean judged = streak > 0 || judgeThisCycle || earlyThisCycle;
		boolean missed = !hitThisCycle && !syncCycle && judged;
		if (missed)
		{
			misses++;
			streak = 0;
			recovering = true;
			setResult(TickSnapshot.Result.MISS, now);
			previousMarks = Collections.unmodifiableList(markWrongTicks(marks));
		}
		else
		{
			previousMarks = Collections.unmodifiableList(marks);
		}
		return missed;
	}

	/** In a missed cycle, the clicks on other ticks were the wrong ticks. */
	private static List<TickSnapshot.Mark> markWrongTicks(List<TickSnapshot.Mark> cycleMarks)
	{
		List<TickSnapshot.Mark> result = new ArrayList<>(cycleMarks.size());
		for (TickSnapshot.Mark mark : cycleMarks)
		{
			result.add(mark.getType() == TickSnapshot.MarkType.OFF_TICK
				? new TickSnapshot.Mark(mark.getPosition(), TickSnapshot.MarkType.WRONG_TICK)
				: mark);
		}
		return result;
	}

	private void startNewCycle()
	{
		cycleStartTick = tickCount;
		hitThisCycle = false;
		earlyThisCycle = false;
		syncCycle = false;
		judgeThisCycle = false;
		marks = new ArrayList<>();
	}

	private void addMark(long now, TickSnapshot.MarkType type)
	{
		if (marks.size() < MAX_MARKS_PER_CYCLE)
		{
			marks.add(new TickSnapshot.Mark(progress(now), type));
		}
	}

	/** 0..1 across the whole cycle; each real game tick snaps the bar to the next tick divider. */
	private double progress(long now)
	{
		double intoTick = (now - lastTickNanos) / (double) TICK_NANOS;
		intoTick = Math.max(0, Math.min(1, intoTick));
		return (positionInCycle() + intoTick) / cycleLength;
	}

	private int positionInCycle()
	{
		return (int) (tickCount - cycleStartTick);
	}

	/** Whether the tick {@code ahead} ticks after the current one is a target tick. */
	private boolean isTargetTick(int ahead)
	{
		return !lastTickOnly || positionInCycle() + ahead == cycleLength - 1;
	}

	private void setResult(TickSnapshot.Result result, long now)
	{
		lastResult = result;
		lastResultNanos = now;
	}
}
