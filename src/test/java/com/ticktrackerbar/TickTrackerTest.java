/*
 * Copyright (c) 2026, ETPhone12
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import com.ticktrackerbar.TickSnapshot.MarkType;
import com.ticktrackerbar.TickSnapshot.Result;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class TickTrackerTest
{
	private static final long MS = 1_000_000L;

	private TickTracker tracker;
	/** Time of the most recent game tick. */
	private long tickTime;

	@Before
	public void setUp()
	{
		tracker = new TickTracker();
		tickTime = 5_000 * MS;
	}

	@Test
	public void oneTickHitsBuildAStreakAndAMissBreaksIt()
	{
		start(1, true, true);
		click(300);
		assertEquals(1, snap().getHits());
		assertEquals(1, snap().getStreak());

		tick();
		click(200);
		assertEquals(2, snap().getStreak());

		tick(); // this tick passes without a click...
		tick(); // ...so the bar resets on a miss
		TickSnapshot s = snap();
		assertEquals(0, s.getStreak());
		assertEquals(2, s.getBestStreak());
		assertEquals(2, s.getHits());
		assertEquals(1, s.getMisses());
		assertEquals(Result.MISS, s.getLastResult());
		assertEquals(66, s.getAccuracy());
	}

	@Test
	public void idleTicksAreNotMisses()
	{
		start(1, true, true);
		ticks(20);
		TickSnapshot s = snap();
		assertEquals(0, s.getMisses());
		assertEquals(-1, s.getAccuracy());
		assertEquals(Result.NONE, s.getLastResult());
	}

	@Test
	public void extraClicksInTheSameTickCountOnce()
	{
		start(1, true, true);
		click(100);
		click(250);
		click(400);
		TickSnapshot s = snap();
		assertEquals(1, s.getHits());
		assertEquals(Arrays.asList(MarkType.HIT, MarkType.EXTRA, MarkType.EXTRA), types(s.getMarks()));
	}

	@Test
	public void threeTickSyncsToTheFirstClickThenNeedsTheLastTick()
	{
		start(3, true, true);
		// The first click lands on tick 1 of 3, but we're not on a streak, so the cycle lines up with it.
		click(100);
		assertEquals(1, snap().getHits());
		assertEquals(2, snap().getTickInCycle());

		tick(); // new cycle, tick 1 of 3
		click(100); // too early: marked, but not a hit
		assertEquals(Collections.singletonList(MarkType.OFF_TICK), types(snap().getMarks()));
		assertEquals(1, snap().getHits());

		ticks(2); // tick 3 of 3, the target tick
		click(300);
		assertEquals(2, snap().getHits());
		assertEquals(2, snap().getStreak());

		tick();
		assertEquals("the early click didn't cost the cycle, so it stays neutral",
			Arrays.asList(MarkType.OFF_TICK, MarkType.HIT), types(snap().getPreviousMarks()));

		ticks(2); // up to the target tick, no click...
		tick(); // ...so it ends on a miss
		assertEquals(1, snap().getMisses());
		assertEquals(0, snap().getStreak());
	}

	@Test
	public void clicksOnTheWrongTickWithoutSyncCountAsAMiss()
	{
		start(3, true, false);
		click(100); // tick 1 of 3
		assertEquals(0, snap().getHits());
		assertEquals(Collections.singletonList(MarkType.OFF_TICK), types(snap().getMarks()));

		ticks(3); // the cycle ends with no click on tick 3
		assertEquals(1, snap().getMisses());
		assertEquals("in a missed cycle the click shows as the wrong tick",
			Collections.singletonList(MarkType.WRONG_TICK), types(snap().getPreviousMarks()));
	}

	@Test
	public void theWrongRhythmNeverScoresAfterTheFirstClick()
	{
		start(3, true, true);
		// Clicking every 4 ticks on a 3 tick cycle: always a tick late.
		click(200);
		assertEquals(1, snap().getHits());
		for (int i = 0; i < 20; i++)
		{
			ticks(4);
			click(200);
			TickSnapshot s = snap();
			assertEquals("the click after a miss only restarts the rhythm",
				Collections.singletonList(MarkType.SYNC), types(s.getMarks()));
			assertEquals("and doesn't turn the miss into a hit", Result.MISS, s.getLastResult());
		}
		ticks(4);
		TickSnapshot s = snap();
		assertEquals(1, s.getHits());
		assertEquals(21, s.getMisses());
		assertEquals(0, s.getStreak());
		assertEquals(4, s.getAccuracy());
	}

	@Test
	public void longGapsDontScoreEither()
	{
		start(3, true, true);
		// Every 7 ticks: one cycle missed, then an idle cycle in between each click.
		for (int i = 0; i < 10; i++)
		{
			click(200);
			ticks(7);
		}
		assertEquals(1, snap().getHits());
	}

	@Test
	public void aLateClickCostsOneMissThenTheRhythmCarriesOn()
	{
		start(3, true, true);
		click(200); // tick 0
		ticks(3);
		click(200); // tick 3
		ticks(3);
		click(200); // tick 6
		assertEquals(3, snap().getStreak());

		ticks(4);
		click(200); // tick 10, one tick late
		TickSnapshot late = snap();
		assertEquals(1, late.getMisses());
		assertEquals("the late click isn't a hit", 3, late.getHits());
		assertEquals(Result.MISS, late.getLastResult());
		assertEquals(Collections.singletonList(MarkType.SYNC), types(late.getMarks()));

		ticks(3);
		click(200); // 3 ticks after the late click: back on the rhythm
		ticks(3);
		click(200);
		TickSnapshot s = snap();
		assertEquals(5, s.getHits());
		assertEquals(2, s.getStreak());
		assertEquals(1, s.getMisses());
		assertEquals(3, s.getBestStreak());
	}

	@Test
	public void anEarlyClickShowsAsTheWrongTickOnceTheCycleIsMissed()
	{
		start(3, true, true);
		click(200); // tick 0
		ticks(3);
		click(200); // tick 3
		ticks(2);
		click(200); // tick 5, one tick early
		assertEquals(Collections.singletonList(MarkType.OFF_TICK), types(snap().getMarks()));

		ticks(2); // the target tick passes without a click
		TickSnapshot s = snap();
		assertEquals(1, s.getMisses());
		assertEquals(Collections.singletonList(MarkType.WRONG_TICK), types(s.getPreviousMarks()));
	}

	@Test
	public void skippingAWholeCycleInRhythmStillHits()
	{
		start(3, true, true);
		// Every 6 ticks: right on the rhythm, but only every other cycle.
		for (int i = 0; i < 10; i++)
		{
			click(200);
			ticks(6);
		}
		TickSnapshot s = snap();
		assertEquals(10, s.getHits());
		assertEquals(10, s.getMisses());
		assertEquals(50, s.getAccuracy());
	}

	@Test
	public void aSingleRestartClickThenStoppingIsOneMoreMiss()
	{
		start(3, true, true);
		click(200);
		ticks(4); // missed
		click(200); // restarts the rhythm...
		ticks(30); // ...but nothing follows
		TickSnapshot s = snap();
		assertEquals(2, s.getMisses());
		assertEquals(1, s.getHits());
	}

	@Test
	public void idlingAfterAMissAddsNoMoreMisses()
	{
		start(3, true, true);
		click(200);
		ticks(4); // missed
		ticks(30);
		assertEquals(1, snap().getMisses());
	}

	@Test
	public void resetMakesTheNextClickAFreshStart()
	{
		start(3, true, true);
		click(200);
		ticks(4); // missed
		assertEquals(1, snap().getMisses());

		tracker.resetStats();
		click(200);
		TickSnapshot s = snap();
		assertEquals(1, s.getHits());
		assertEquals(1, s.getStreak());
		assertEquals(Collections.singletonList(MarkType.HIT), types(s.getMarks()));
	}

	@Test
	public void clicksOnOtherTicksStayNeutralWhileYouKeepHitting()
	{
		start(3, true, true);
		// A 3 tick method: the manipulation on the target tick and a click on each of the other two ticks.
		for (int i = 0; i < 6; i++)
		{
			click(100);
			tick();
			click(100);
			tick();
			click(100);
			tick();
		}
		TickSnapshot s = snap();
		assertEquals(6, s.getHits());
		assertEquals(0, s.getMisses());
		assertEquals(6, s.getStreak());
		assertEquals(Arrays.asList(MarkType.OFF_TICK, MarkType.OFF_TICK, MarkType.HIT), types(s.getPreviousMarks()));
	}

	@Test
	public void anyTickWindowAcceptsAClickAnywhereInTheCycle()
	{
		start(3, false, true);
		click(100); // tick 1 of 3
		ticks(4); // tick 2 of 3 in the next cycle
		click(100);
		TickSnapshot s = snap();
		assertEquals(2, s.getHits());
		assertEquals(2, s.getStreak());
		assertEquals(0, s.getMisses());
	}

	@Test
	public void barFillsOverTheCycleAndSnapsToEachGameTick()
	{
		start(3, true, false);
		assertEquals(0.5 / 3, tracker.snapshot(tickTime + 300 * MS).getProgress(), 1e-9);

		tick();
		assertEquals(1.5 / 3, tracker.snapshot(tickTime + 300 * MS).getProgress(), 1e-9);
		// A late game tick: the bar waits at the tick divider instead of running ahead.
		assertEquals(2.0 / 3, tracker.snapshot(tickTime + 900 * MS).getProgress(), 1e-9);

		ticks(2); // new cycle
		assertEquals(0.0, tracker.snapshot(tickTime).getProgress(), 1e-9);
	}

	@Test
	public void markShowsWhereInTheTickYouClicked()
	{
		start(1, true, true);
		click(150);
		assertEquals(0.25, snap().getMarks().get(0).getPosition(), 1e-9);
	}

	@Test
	public void lastCyclesMarksAreKeptForOneCycle()
	{
		start(1, true, true);
		click(300);
		tick();
		TickSnapshot s = snap();
		assertTrue(s.getMarks().isEmpty());
		assertEquals(Collections.singletonList(MarkType.HIT), types(s.getPreviousMarks()));

		tick();
		assertTrue(snap().getPreviousMarks().isEmpty());
	}

	@Test
	public void resetClearsTheCounters()
	{
		start(1, true, true);
		click(100);
		tick();
		click(100);
		tracker.resetStats();
		TickSnapshot s = snap();
		assertEquals(0, s.getHits());
		assertEquals(0, s.getStreak());
		assertEquals(0, s.getBestStreak());
		assertEquals(0, s.getMisses());
		assertTrue(s.getMarks().isEmpty());
	}

	@Test
	public void loggingOutEndsTheStreakWithoutAMiss()
	{
		start(1, true, true);
		click(100);
		tracker.pause();
		TickSnapshot s = snap();
		assertTrue(s.isWaiting());
		assertEquals(0, s.getStreak());
		assertEquals(0, s.getMisses());
		assertEquals(1, s.getHits());

		tick(); // logged back in
		assertFalse(snap().isWaiting());
		ticks(5);
		assertEquals(0, snap().getMisses());
	}

	@Test
	public void loggingBackInAfterAMissIsAFreshStart()
	{
		start(3, true, true);
		click(200);
		ticks(4); // missed
		tracker.pause();
		tick(); // logged back in
		click(200);
		assertEquals(2, snap().getHits());
	}

	@Test
	public void clicksBeforeTheFirstTickAreIgnored()
	{
		tracker.configure(1, true, true, 0);
		tracker.onInput(tickTime);
		assertEquals(0, tracker.snapshot(tickTime).getHits());
		assertTrue(tracker.snapshot(tickTime).isWaiting());
	}

	@Test
	public void showsWaitingWhenGameTicksStop()
	{
		start(1, true, true);
		long frozen = tickTime + TickTracker.STALL_NANOS + MS;
		assertFalse(tracker.snapshot(tickTime + 1_000 * MS).isWaiting());
		assertTrue(tracker.snapshot(frozen).isWaiting());

		// Clicking while the game is frozen doesn't count.
		tracker.onInput(frozen);
		assertEquals(0, tracker.snapshot(frozen).getHits());
	}

	@Test
	public void changingTheCycleRestartsTheStreakWithoutAMiss()
	{
		start(1, true, true);
		click(100);
		tracker.configure(3, true, true, 0);
		TickSnapshot s = snap();
		assertEquals(0, s.getStreak());
		assertEquals(0, s.getMisses());
		assertEquals(3, s.getCycleLength());
		assertEquals(0, s.getTickInCycle());

		// Changing only the sync option keeps the streak going.
		click(200);
		tracker.configure(3, true, false, 0);
		assertEquals(1, snap().getStreak());

		// So does changing the input lag.
		tracker.configure(3, true, false, 150);
		assertEquals(1, snap().getStreak());
	}

	@Test
	public void withoutInputLagAClickAtTheVeryEndStillCountsInItsTick()
	{
		start(1, true, true);
		click(599);
		TickSnapshot s = snap();
		assertEquals(1, s.getHits());
		assertEquals(Collections.singletonList(MarkType.HIT), types(s.getMarks()));
		assertEquals(0.0, s.getLateWindow(), 1e-9);
	}

	@Test
	public void withInputLagALateClickCountsForTheNextTick()
	{
		startWithLag(1, true, true, 150);
		assertEquals("150 ms is a quarter of a tick", 0.25, snap().getLateWindow(), 1e-9);
		click(100); // in time: a hit in this tick
		assertEquals(1, snap().getHits());

		tick();
		click(500); // within 150 ms of the end: the game takes it in with the next tick
		TickSnapshot s = snap();
		assertEquals("not scored yet", 1, s.getHits());
		assertEquals(Collections.singletonList(MarkType.LATE), types(s.getMarks()));

		tick();
		s = snap();
		assertEquals("the tick it was made in had no click in time", 1, s.getMisses());
		assertEquals("it scores in this tick instead", 2, s.getHits());
		assertEquals(1, s.getStreak());
		assertEquals("the miss stays on show", Result.MISS, s.getLastResult());
		assertEquals("its mark stays where it was made", Collections.singletonList(MarkType.LATE),
			types(s.getPreviousMarks()));
		assertTrue("and isn't drawn twice", s.getMarks().isEmpty());

		click(100); // the game already counted the late click for this tick
		assertEquals(Collections.singletonList(MarkType.EXTRA), types(snap().getMarks()));
		assertEquals(2, snap().getHits());

		tick(); // the late click counted once, not again with every tick
		assertEquals(2, snap().getHits());
		assertEquals(1, snap().getMisses());
	}

	@Test
	public void withoutInputLagClicksWhileATickIsLateStayInThatTick()
	{
		start(1, true, true);
		click(200);
		tick();
		click(200);
		// The next game tick comes 900 ms later instead of 600; a click 700 ms in still belongs to this tick.
		click(700);
		tickTime += 900 * MS;
		tracker.onGameTick(tickTime);
		TickSnapshot s = snap();
		assertEquals(0, s.getMisses());
		assertEquals(2, s.getHits());
		assertEquals(Arrays.asList(MarkType.HIT, MarkType.EXTRA), types(s.getPreviousMarks()));
	}

	@Test
	public void lateClicksAreDroppedWhenTheCycleChanges()
	{
		startWithLag(1, true, true, 150);
		click(500); // late: waits for the next tick
		tracker.configure(3, true, true, 150);
		tick();
		assertEquals(0, snap().getHits());
	}

	@Test
	public void clickingLateEveryTickKeepsTheStreak()
	{
		startWithLag(1, true, true, 150);
		for (int i = 0; i < 5; i++)
		{
			click(520);
			tick();
		}
		TickSnapshot s = snap();
		assertEquals(5, s.getHits());
		assertEquals(5, s.getStreak());
		assertEquals(0, s.getMisses());
	}

	@Test
	public void withInputLagTheTargetWindowStartsEarlier()
	{
		startWithLag(3, true, false, 150);
		tick(); // tick 1 of 3
		click(480); // late in tick 1: the game takes it in with tick 2, the target
		TickSnapshot s = snap();
		assertEquals(1, s.getHits());
		assertEquals(Collections.singletonList(MarkType.HIT), types(s.getMarks()));

		ticks(2); // the cycle ends on the hit
		assertEquals(0, snap().getMisses());
		assertEquals(1, snap().getStreak());
	}

	@Test
	public void withInputLagALateClickOnTheTargetTickMissesTheCycle()
	{
		startWithLag(3, true, true, 150);
		click(100); // lines the cycle up: tick 0 is the target, a hit
		ticks(3); // the target tick again
		click(500); // too late for it
		TickSnapshot s = snap();
		assertEquals(1, s.getHits());
		assertEquals(Collections.singletonList(MarkType.LATE), types(s.getMarks()));

		tick(); // the cycle ends without a hit; the late click lands in the next one
		s = snap();
		assertEquals(1, s.getMisses());
		assertEquals(1, s.getHits());
		assertEquals(Result.MISS, s.getLastResult());
		assertEquals("the late click restarts the rhythm on its own tick", 2, s.getTickInCycle());

		ticks(3); // 3 ticks on: in the rhythm of the late click
		click(100);
		assertEquals(2, snap().getHits());
		assertEquals(1, snap().getStreak());
	}

	@Test
	public void withInputLagALateClickCanStartTheRhythm()
	{
		startWithLag(3, true, true, 150);
		click(500); // tick 0, late: the game takes it in with tick 1, so tick 1 becomes the target
		TickSnapshot s = snap();
		assertEquals(1, s.getHits());
		assertEquals("the target is the next tick", 1, s.getTickInCycle());

		ticks(3); // tick 3, a cycle later
		click(500); // late again: counts for tick 4, the target
		assertEquals(2, snap().getHits());
		assertEquals(2, snap().getStreak());
	}

	@Test
	public void lateClicksAreDroppedByAResetOrLoggingOut()
	{
		startWithLag(1, true, true, 150);
		click(500);
		tracker.resetStats();
		tick();
		assertEquals(0, snap().getHits());

		click(500);
		tracker.pause();
		tick(); // logged back in: this tick only restarts the bar
		tick(); // the tick a click left over from before logging out would be scored on
		assertEquals(0, snap().getHits());
	}

	@Test
	public void inputLagIsKeptUnderATick()
	{
		startWithLag(1, true, true, 5_000);
		assertEquals((double) TickTracker.MAX_INPUT_LAG_MS / 600, snap().getLateWindow(), 1e-9);
		startWithLag(1, true, true, -20);
		assertEquals(0.0, snap().getLateWindow(), 1e-9);
	}

	/** Configures the tracker and delivers the first game tick. */
	private void start(int cycleLength, boolean lastTickOnly, boolean sync)
	{
		startWithLag(cycleLength, lastTickOnly, sync, 0);
	}

	private void startWithLag(int cycleLength, boolean lastTickOnly, boolean sync, int inputLagMs)
	{
		tracker.configure(cycleLength, lastTickOnly, sync, inputLagMs);
		tracker.onGameTick(tickTime);
	}

	private void tick()
	{
		tickTime += TickTracker.TICK_NANOS;
		tracker.onGameTick(tickTime);
	}

	private void ticks(int count)
	{
		for (int i = 0; i < count; i++)
		{
			tick();
		}
	}

	/** A click this many milliseconds into the current tick. */
	private void click(long ms)
	{
		tracker.onInput(tickTime + ms * MS);
	}

	private TickSnapshot snap()
	{
		return tracker.snapshot(tickTime + 100 * MS);
	}

	private static List<MarkType> types(List<TickSnapshot.Mark> marks)
	{
		return marks.stream().map(TickSnapshot.Mark::getType).collect(Collectors.toList());
	}
}
