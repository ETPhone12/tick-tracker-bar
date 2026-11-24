/*
 * Copyright (c) 2026, Tick Tracker Bar contributors
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import com.ticktrackerbar.TickSnapshot.Mark;
import com.ticktrackerbar.TickSnapshot.MarkType;
import com.ticktrackerbar.TickSnapshot.Result;
import com.ticktrackerbar.TickTrackerConfig.MarkStyle;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Draws the bar into images, checks key pixels and text placement, and saves previews to build/previews. */
public class TickBarPainterTest
{
	private static final File PREVIEW_DIR = new File("build/previews");
	/** Roughly the color of the game world behind the overlay. */
	private static final Color BACKDROP = new Color(72, 84, 52);
	/** 1x, and Windows display scaling of 150% (the pop-out window is drawn scaled there). */
	private static final double[] SCALES = {1.0, 1.5};

	private final TickBarPainter painter = new TickBarPainter();
	private final BarStyle style = BarStyle.defaults();

	@Test
	public void drawsTheBarMarksAndFill() throws IOException
	{
		// 3-tick cycle on the target tick: a click on another tick at 20%, a hit at 80%, last cycle's hit as a ghost.
		TickSnapshot s = snapshot(false, 3, 2, 0.9, true,
			Arrays.asList(new Mark(0.2, MarkType.OFF_TICK), new Mark(0.8, MarkType.HIT)),
			Collections.singletonList(new Mark(0.75, MarkType.HIT)),
			12, 30, 154, 9, Result.HIT, 100);

		BufferedImage image = render(s, style, "3-tick-hit.png", false);
		TickBarPainter.Layout l = layout(style);
		int middle = l.barY + l.barH / 2;

		assertCross(image, l, markX(l, 0.8), style.getHitColor(), "hit");
		assertCross(image, l, markX(l, 0.2), TickBarPainter.NEUTRAL_MARK, "a click on another tick");
		assertEquals("bar turns dark green after a hit", TickBarPainter.hitFill(style).getRGB(),
			image.getRGB(l.barX + l.barW / 2, middle));
		assertTrue("so the hit's own X shows up on it",
			brightness(style.getHitColor().getRGB()) > brightness(TickBarPainter.hitFill(style).getRGB()) + 30);
		assertTrue("and the bar gets darker when you hit, not just changes hue",
			brightness(style.getBarColor().getRGB()) > brightness(TickBarPainter.hitFill(style).getRGB()) + 60);

		BufferedImage lines = render(s, withMarks(MarkStyle.LINES), "3-tick-hit-lines.png", false);
		assertEquals("hit line has a white core", Color.WHITE.getRGB(), lines.getRGB(markX(l, 0.8), middle));
		assertEquals("a click on another tick is neutral", TickBarPainter.NEUTRAL_MARK.getRGB(),
			lines.getRGB(markX(l, 0.2), middle));
	}

	@Test
	public void eachKindOfMarkHasItsOwnColor() throws IOException
	{
		TickSnapshot s = snapshot(false, 3, 1, 0.5, false,
			Arrays.asList(new Mark(0.1, MarkType.WRONG_TICK), new Mark(0.3, MarkType.OFF_TICK),
				new Mark(0.5, MarkType.SYNC), new Mark(0.7, MarkType.EXTRA), new Mark(0.9, MarkType.HIT)),
			Collections.emptyList(), 0, 4, 20, 3, Result.MISS, 100);
		for (MarkStyle marks : MarkStyle.values())
		{
			BarStyle st = withMarks(marks);
			BufferedImage image = render(s, st, "mark-kinds-" + marks.name().toLowerCase() + ".png", false);
			TickBarPainter.Layout l = layout(st);
			int middle = l.barY + l.barH / 2;
			if (marks.isCross())
			{
				assertCross(image, l, markX(l, 0.1), st.getMissColor(), marks + ", wrong tick");
				assertCross(image, l, markX(l, 0.3), TickBarPainter.NEUTRAL_MARK, marks + ", another tick");
				// Also where the now line is: the X of a click goes on top of the line.
				assertCross(image, l, markX(l, 0.5), TickBarPainter.SYNC_MARK, marks + ", restart click");
				assertCross(image, l, markX(l, 0.7), TickBarPainter.NEUTRAL_MARK, marks + ", extra click");
				assertCross(image, l, markX(l, 0.9), st.getHitColor(), marks + ", hit");
			}
			else
			{
				assertEquals("wrong tick is red", st.getMissColor().getRGB(), image.getRGB(markX(l, 0.1), middle));
				assertEquals(TickBarPainter.NEUTRAL_MARK.getRGB(), image.getRGB(markX(l, 0.3), middle));
				assertEquals("re-sync click is yellow", TickBarPainter.SYNC_MARK.getRGB(), image.getRGB(markX(l, 0.5), middle));
				assertEquals(TickBarPainter.NEUTRAL_MARK.getRGB(), image.getRGB(markX(l, 0.7), middle));
				assertEquals(Color.WHITE.getRGB(), image.getRGB(markX(l, 0.9), middle));
			}
		}
	}

	@Test
	public void barUsesTheFillColorUntilYouHit() throws IOException
	{
		TickSnapshot s = snapshot(false, 1, 0, 0.45, false,
			Collections.emptyList(),
			Collections.singletonList(new Mark(0.42, MarkType.HIT)),
			7, 7, 7, 0, Result.HIT, 500);

		BufferedImage image = render(s, style, "1-tick-waiting-for-click.png", false);
		TickBarPainter.Layout l = layout(style);
		assertEquals(style.getBarColor().getRGB(), image.getRGB(l.barX + 20, l.barY + l.barH / 2));
	}

	@Test
	public void nowLineShowsWhereAClickWouldLand() throws IOException
	{
		TickSnapshot s = snapshot(false, 2, 0, 0.3, false, Collections.emptyList(), Collections.emptyList(),
			0, 0, 0, 0, Result.NONE, Long.MAX_VALUE);
		for (MarkStyle marks : MarkStyle.values())
		{
			BarStyle st = withMarks(marks);
			BufferedImage image = render(s, st, "now-line-" + marks.name().toLowerCase() + ".png", false);
			TickBarPainter.Layout l = layout(st);
			int x = markX(l, 0.3);
			int white = TickBarPainter.NOW_LINE.getRGB();
			// From the row above the bar, through it, to the row below it: the rows click marks reach too.
			boolean line = true;
			for (int y = l.barY - 3; y < l.barY + l.barH + 3; y++)
			{
				line &= image.getRGB(x, y) == white && image.getRGB(x - 1, y) == white;
			}
			// Spelled out rather than read from the setting, so a wrong setting can't pass.
			assertEquals(marks + " shows the now line", marks == MarkStyle.X_AND_NOW_LINE, line);
			int middle = l.barY + l.barH / 2;
			if (line)
			{
				assertEquals("black edge after the line", Color.BLACK.getRGB(), image.getRGB(x + 1, middle));
				assertEquals("black edge before the line", Color.BLACK.getRGB(), image.getRGB(x - 2, middle));
			}
			assertEquals("the line sits on the front edge of the fill", st.getBarColor().getRGB(),
				image.getRGB(x - 3, middle));
		}
	}

	@Test
	public void inputLagMovesTheGreenZoneEarlierAndStripesTheEnd() throws IOException
	{
		// 3-tick cycle near its start, so the fill stays clear of the zone. 150 ms is a quarter of a tick.
		TickSnapshot plain = snapshot(false, 3, 0, 0.05, false, Collections.emptyList(), Collections.emptyList(),
			0, 0, 0, 0, Result.NONE, Long.MAX_VALUE);
		TickSnapshot lagged = withLate(plain, 0.25);
		BufferedImage before = render(plain, style, "zone-no-input-lag.png", false);
		BufferedImage after = render(lagged, style, "zone-input-lag.png", false);
		TickBarPainter.Layout l = layout(style);
		int late = TickBarPainter.lateWidth(l, lagged);
		assertEquals((int) Math.round(l.barW * 0.25 / 3), late);
		assertTrue(late > 10);

		int zoneStart = l.barX + l.barW * 2 / 3;
		int zoneEdge = solid(style.getZoneColor()).getRGB();
		int row = l.barY + 2;
		// (Without input lag the zone's left edge is under the last tick divider.)
		assertEquals("without input lag the zone runs to the end of the bar", zoneEdge,
			before.getRGB(l.barX + l.barW - 1, row));
		assertEquals("with it, the zone starts that much before the last tick", zoneEdge,
			after.getRGB(zoneStart - late, row));
		assertEquals("and ends that much before the end of the bar", zoneEdge, after.getRGB(l.barX + l.barW - late - 1, row));

		// Inside the end part: plain zone shading without input lag, two-color stripes with it.
		Set<Integer> plainColors = new HashSet<>();
		Set<Integer> stripedColors = new HashSet<>();
		for (int y = l.barY + 1; y < l.barY + l.barH - 1; y++)
		{
			for (int x = l.barX + l.barW - late + 1; x < l.barX + l.barW - 1; x++)
			{
				plainColors.add(before.getRGB(x, y));
				stripedColors.add(after.getRGB(x, y));
				// The stripes are two pixels wide, four apart, running diagonally, in the miss color.
				boolean stripe = (x + y) % 4 < 2;
				boolean nextIsStripe = (x + 1 + y) % 4 < 2;
				if (stripe != nextIsStripe && x + 1 < l.barX + l.barW - 1)
				{
					int red = (after.getRGB(x, y) >> 16) & 0xFF;
					int nextRed = (after.getRGB(x + 1, y) >> 16) & 0xFF;
					assertEquals("red stripes at " + x + "," + y, stripe, red > nextRed);
				}
			}
		}
		assertEquals(1, plainColors.size());
		assertEquals(2, stripedColors.size());
	}

	@Test
	public void barsWithoutAGreenZoneAreStripedToo() throws IOException
	{
		// The default 1-tick bar, and a 3-tick bar with Hit window: Any tick in cycle.
		TickSnapshot oneTick = withLate(snapshot(false, 1, 0, 0.05, false, Collections.emptyList(), Collections.emptyList(),
			0, 0, 0, 0, Result.NONE, Long.MAX_VALUE), 0.25);
		TickSnapshot anyTick = new TickSnapshot(false, 3, false, 0, 0.05, false, Collections.emptyList(),
			Collections.emptyList(), 0, 0, 0, 0, Result.NONE, Long.MAX_VALUE, 0.25);
		for (TickSnapshot s : Arrays.asList(oneTick, anyTick))
		{
			String name = s.getCycleLength() + "-tick-any-input-lag.png";
			BufferedImage image = render(s, style, s.isLastTickOnly() ? "1-tick-input-lag.png" : name, false);
			TickBarPainter.Layout l = layout(style);
			int late = TickBarPainter.lateWidth(l, s);
			assertTrue(late > 10);
			Set<Integer> striped = new HashSet<>();
			Set<Integer> before = new HashSet<>();
			for (int y = l.barY + 1; y < l.barY + l.barH - 1; y++)
			{
				for (int x = l.barX + l.barW - late + 1; x < l.barX + l.barW - 1; x++)
				{
					striped.add(image.getRGB(x, y));
				}
				for (int x = l.barX + l.barW - late - 8; x < l.barX + l.barW - late - 1; x++)
				{
					before.add(image.getRGB(x, y));
				}
			}
			assertEquals(s.getCycleLength() + " tick: plain track before the stripes", 1, before.size());
			assertEquals(s.getCycleLength() + " tick: stripes at the end", 2, striped.size());
		}
	}

	@Test
	public void stripesFollowTheMissColor()
	{
		TickSnapshot s = withLate(snapshot(false, 1, 0, 0.05, false, Collections.emptyList(), Collections.emptyList(),
			0, 0, 0, 0, Result.NONE, Long.MAX_VALUE), 0.25);
		BarStyle blue = new BarStyle(style.getBarWidth(), style.getBarHeight(), true, true, style.getMarkStyle(),
			style.getBarColor(), style.getHitColor(), Color.BLUE, style.getZoneColor(), style.getBackgroundColor());
		// The same painter draws red stripes first, then blue ones once the Miss color changes.
		BufferedImage red = draw(painter, s, style, 0, false, 1.0);
		BufferedImage bluish = draw(painter, s, blue, 0, false, 1.0);
		TickBarPainter.Layout l = layout(style);
		int x = l.barX + l.barW - 3;
		int y = l.barY + 2;
		while ((x + y) % 4 >= 2)
		{
			y++;
		}
		Color r = new Color(red.getRGB(x, y));
		Color b = new Color(bluish.getRGB(x, y));
		assertTrue("red stripe " + r, r.getRed() > r.getBlue() + 50);
		assertTrue("blue stripe " + b, b.getBlue() > b.getRed() + 50);
	}

	@Test
	public void lateClicksAreCyan() throws IOException
	{
		TickSnapshot s = withLate(snapshot(false, 1, 0, 0.5, false,
			Collections.singletonList(new Mark(0.9, MarkType.LATE)), Collections.emptyList(),
			0, 0, 0, 0, Result.NONE, Long.MAX_VALUE), 0.25);
		for (MarkStyle marks : MarkStyle.values())
		{
			BarStyle st = withMarks(marks);
			BufferedImage image = render(s, st, "late-click-" + marks.name().toLowerCase() + ".png", false);
			TickBarPainter.Layout l = layout(st);
			if (marks.isCross())
			{
				assertCross(image, l, markX(l, 0.9), TickBarPainter.LATE_MARK, marks + ", late click");
			}
			else
			{
				assertEquals(TickBarPainter.LATE_MARK.getRGB(), image.getRGB(markX(l, 0.9), l.barY + l.barH / 2));
			}
		}
	}

	@Test
	public void crossesAtTheEndsOfTheBarStayInsideThePanel()
	{
		TickSnapshot s = snapshot(false, 1, 0, 0.5, false,
			Arrays.asList(new Mark(0.0, MarkType.HIT), new Mark(1.0, MarkType.WRONG_TICK)),
			Collections.emptyList(), 0, 0, 0, 0, Result.NONE, Long.MAX_VALUE);
		int margin = 12;
		for (int height : new int[]{BarStyle.MIN_BAR_HEIGHT, 12, BarStyle.DEFAULT_BAR_HEIGHT, BarStyle.MAX_BAR_HEIGHT})
		{
			for (boolean stats : new boolean[]{false, true})
			{
				BarStyle st = new BarStyle(BarStyle.MIN_BAR_WIDTH, height, stats, true, MarkStyle.X, style.getBarColor(),
					style.getHitColor(), style.getMissColor(), style.getZoneColor(), style.getBackgroundColor());
				TickBarPainter.Layout l = layout(st);
				BufferedImage image = new BufferedImage(l.width + 2 * margin, l.height + 2 * margin, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = image.createGraphics();
				g.setColor(BACKDROP);
				g.fillRect(0, 0, image.getWidth(), image.getHeight());
				g.translate(margin, margin);
				painter.paint(g, s, st, 0, false);
				g.dispose();

				String where = " (bar height " + height + (stats ? ", stats" : "") + ")";
				for (int y = 0; y < image.getHeight(); y++)
				{
					for (int x = 0; x < image.getWidth(); x++)
					{
						boolean inPanel = x >= margin && x < margin + l.width && y >= margin && y < margin + l.height;
						if (!inPanel)
						{
							assertEquals("nothing drawn outside the panel at " + x + "," + y + where,
								BACKDROP.getRGB(), image.getRGB(x, y));
						}
					}
				}
				int middle = margin + l.barY + l.barH / 2;
				// The middle of an X covers its column and the one to its left, kept on the bar.
				assertEquals("X at the start of the bar" + where, st.getHitColor().getRGB(),
					image.getRGB(margin + l.barX, middle));
				assertEquals("X at the end of the bar" + where, st.getMissColor().getRGB(),
					image.getRGB(margin + l.barX + l.barW - 1, middle));
			}
		}
	}

	@Test
	public void theXIsTheSizeOfTheGamesCrossAndStaysInTheBarsRows()
	{
		TickSnapshot empty = snapshot(false, 1, 0, 0.1, false, Collections.emptyList(), Collections.emptyList(),
			0, 0, 0, 0, Result.NONE, Long.MAX_VALUE);
		TickSnapshot marked = snapshot(false, 1, 0, 0.1, false, Collections.singletonList(new Mark(0.5, MarkType.HIT)),
			Collections.emptyList(), 0, 0, 0, 0, Result.NONE, Long.MAX_VALUE);
		for (int height = BarStyle.MIN_BAR_HEIGHT; height <= BarStyle.MAX_BAR_HEIGHT; height++)
		{
			BarStyle st = new BarStyle(BarStyle.DEFAULT_BAR_WIDTH, height, false, false, MarkStyle.X, style.getBarColor(),
				style.getHitColor(), style.getMissColor(), style.getZoneColor(), style.getBackgroundColor());
			BufferedImage without = draw(painter, empty, st, 0, false, 1.0);
			BufferedImage with = draw(painter, marked, st, 0, false, 1.0);
			int top = Integer.MAX_VALUE;
			int bottom = -1;
			for (int y = 0; y < with.getHeight(); y++)
			{
				for (int x = 0; x < with.getWidth(); x++)
				{
					if (with.getRGB(x, y) != without.getRGB(x, y))
					{
						top = Math.min(top, y);
						bottom = Math.max(bottom, y);
					}
				}
			}
			TickBarPainter.Layout l = layout(st);
			String where = " (bar height " + height + ")";
			// The layout keeps 3 rows above and below the bar for marks.
			assertTrue("X starts in the rows kept above the bar" + where, top >= l.barY - 3);
			assertTrue("X ends in the rows kept below the bar" + where, bottom <= l.barY + l.barH + 2);
			if (height >= BarStyle.DEFAULT_BAR_HEIGHT)
			{
				assertEquals("full-size X with its edge" + where, 2 * TickBarPainter.MAX_CROSS_ARM + 3, bottom - top + 1);
			}
		}
	}

	@Test
	public void lastCyclesMarksAreFadedButItsRedMarksStayStrong()
	{
		for (MarkStyle marks : MarkStyle.values())
		{
			BarStyle st = withMarks(marks);
			TickBarPainter.Layout l = layout(st);
			int x = markX(l, 0.5);
			int y = l.barY + l.barH / 2;
			BufferedImage empty = draw(painter, faded(Collections.emptyList(), Collections.emptyList()), st, 0, false, 1.0);
			BufferedImage current = draw(painter, faded(Collections.singletonList(new Mark(0.5, MarkType.HIT)),
				Collections.emptyList()), st, 0, false, 1.0);
			BufferedImage ghost = draw(painter, faded(Collections.emptyList(),
				Collections.singletonList(new Mark(0.5, MarkType.HIT))), st, 0, false, 1.0);
			int ghostChange = difference(ghost.getRGB(x, y), empty.getRGB(x, y));
			assertTrue(marks + ": last cycle's hit shows", ghostChange > 0);
			assertTrue(marks + ": but fainter than this cycle's", ghostChange < difference(current.getRGB(x, y), empty.getRGB(x, y)));

			// On the dark track a faint red would look brown; a missed cycle's wrong-tick marks stay clearly red.
			BufferedImage wrong = draw(painter, faded(Collections.emptyList(),
				Collections.singletonList(new Mark(0.5, MarkType.WRONG_TICK))), st, 0, false, 1.0);
			Color c = new Color(wrong.getRGB(x, y));
			assertTrue(marks + ": red wrong-tick mark " + c, c.getRed() - c.getGreen() > 100);
		}
	}

	@Test
	public void greyMarksNeverCoverAColoredOne() throws IOException
	{
		// A hit, then an extra click a few pixels later: the hit's X stays whole on top.
		for (MarkStyle marks : new MarkStyle[]{MarkStyle.X, MarkStyle.X_AND_NOW_LINE})
		{
			BarStyle st = withMarks(marks);
			TickBarPainter.Layout l = layout(st);
			double extra = 0.5 + 4.0 / l.barW;
			TickSnapshot s = snapshot(false, 1, 0, 0.6, true,
				Arrays.asList(new Mark(0.5, MarkType.HIT), new Mark(extra, MarkType.EXTRA)),
				Arrays.asList(new Mark(0.3, MarkType.SYNC), new Mark(0.3 + 4.0 / l.barW, MarkType.OFF_TICK)),
				1, 1, 1, 0, Result.HIT, 100);
			BufferedImage image = render(s, st, "grey-under-colored-" + marks.name().toLowerCase() + ".png", false);
			assertCross(image, l, markX(l, 0.5), st.getHitColor(), marks + ", hit under an extra click");
		}
	}

	@Test
	public void looksTheSameWhenTheCallerLeftAntialiasingOn()
	{
		// RuneLite turns antialiasing on before it hands an overlay its Graphics.
		TickSnapshot s = snapshot(false, 3, 2, 0.9, true,
			Arrays.asList(new Mark(0.2, MarkType.OFF_TICK), new Mark(0.8, MarkType.HIT)),
			Collections.singletonList(new Mark(0.75, MarkType.HIT)), 12, 30, 154, 9, Result.HIT, 100);
		for (double scale : new double[]{1.0, 1.25, 1.5, 1.75})
		{
			BufferedImage plain = draw(painter, s, style, 0, true, scale);
			BufferedImage smooth = new BufferedImage(plain.getWidth(), plain.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = smooth.createGraphics();
			g.setColor(BACKDROP);
			g.fillRect(0, 0, smooth.getWidth(), smooth.getHeight());
			g.scale(scale, scale);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			painter.paint(g, s, style, 0, true);
			g.dispose();
			for (int y = 0; y < plain.getHeight(); y++)
			{
				for (int x = 0; x < plain.getWidth(); x++)
				{
					assertEquals("same pixel at " + x + "," + y + " (scale " + scale + ")", plain.getRGB(x, y), smooth.getRGB(x, y));
				}
			}
		}
	}

	@Test
	public void theCurrentTicksBoxIsYellow()
	{
		int previous = -1;
		for (int tick = 0; tick < 3; tick++)
		{
			TickSnapshot s = snapshot(false, 3, tick, (tick + 0.5) / 3, false, Collections.emptyList(),
				Collections.emptyList(), 0, 0, 0, 0, Result.NONE, Long.MAX_VALUE);
			BufferedImage image = draw(painter, s, style, 0, false, 1.0);
			TickBarPainter.Layout l = layout(style);
			int first = -1;
			for (int x = 0; x < image.getWidth() && first < 0; x++)
			{
				for (int y = l.headerY; y < l.headerY + l.lineHeight; y++)
				{
					if (image.getRGB(x, y) == TickBarPainter.GAME_YELLOW.getRGB())
					{
						first = x;
						break;
					}
				}
			}
			assertTrue("a yellow box on tick " + tick, first >= 0);
			assertTrue("further right each tick", first > previous);
			previous = first;
		}
	}

	@Test
	public void panelHasARaisedFrameLikeTheGamesInterfaces() throws IOException
	{
		BufferedImage image = render(result(1, Result.NONE), style, "frame.png", true);
		int w = image.getWidth();
		int h = image.getHeight();
		int inside = brightness(image.getRGB(3, h - 4));
		assertTrue("black edge", brightness(image.getRGB(0, 0)) < brightness(image.getRGB(w - 2, h - 2)));
		assertTrue("lighter along the top", brightness(image.getRGB(w / 2, 1)) > inside);
		assertTrue("lighter along the left", brightness(image.getRGB(1, h / 2)) > inside);
		assertTrue("darker along the bottom", brightness(image.getRGB(w / 2, h - 2)) < inside);
		assertTrue("darker along the right", brightness(image.getRGB(w - 2, h / 2)) < inside);
	}

	@Test
	public void textUsesTheGamesColors()
	{
		TickSnapshot s = snapshot(false, 3, 2, 0.9, true, Collections.emptyList(), Collections.emptyList(),
			12, 30, 154, 9, Result.NONE, Long.MAX_VALUE);
		RecordingPainter recorder = new RecordingPainter();
		draw(recorder, s, style, 0, false, 1.0);
		assertEquals("cycle length", TickBarPainter.GAME_ORANGE, colorOf(recorder, "3-TICK"));
		assertEquals("labels", TickBarPainter.GAME_ORANGE, colorOf(recorder, "Streak "));
		assertEquals("labels", TickBarPainter.GAME_ORANGE, colorOf(recorder, "Acc "));
		assertEquals("streak", TickBarPainter.GAME_YELLOW, colorOf(recorder, "12"));
		assertEquals("numbers", Color.WHITE, colorOf(recorder, "154"));
	}

	@Test
	public void sizeFollowsTheSettings() throws IOException
	{
		BarStyle bigWithStats = withSize(400, 30, true);
		BarStyle bigNoStats = withSize(400, 30, false);
		TickSnapshot s = snapshot(false, 2, 1, 0.7, false,
			Collections.singletonList(new Mark(0.6, MarkType.HIT)), Collections.emptyList(),
			3, 9, 40, 2, Result.MISS, 100);

		BufferedImage image = render(s, bigNoStats, "2-tick-no-stats-miss.png", false);
		assertEquals(400 + 2 * TickBarPainter.PAD, image.getWidth());
		assertTrue("hiding stats makes it shorter", image.getHeight() < layout(bigWithStats).height);
		assertTrue("a taller bar makes it taller", layout(bigWithStats).height > layout(style).height);
	}

	@Test
	public void rendersOtherStates() throws IOException
	{
		render(waiting(1), style, "waiting.png", false);
		render(waiting(3), withSize(BarStyle.MIN_BAR_WIDTH, BarStyle.MIN_BAR_HEIGHT, true), "waiting-thin-bar.png", true);

		render(snapshot(false, 3, 1, 0.5, false,
			Collections.singletonList(new Mark(0.4, MarkType.SYNC)),
			Collections.singletonList(new Mark(0.35, MarkType.WRONG_TICK)),
			0, 25, 310, 14, Result.MISS, 150), style, "3-tick-miss-then-resync.png", true);
	}

	@Test
	public void waitingShowsAPlainTrackWithTheMessageInside() throws IOException
	{
		for (int width : new int[]{BarStyle.MIN_BAR_WIDTH, BarStyle.DEFAULT_BAR_WIDTH})
		{
			for (int height = BarStyle.MIN_BAR_HEIGHT; height <= 14; height++)
			{
				BarStyle st = withSize(width, height, true);
				for (double scale : SCALES)
				{
					RecordingPainter recorder = new RecordingPainter();
					BufferedImage image = draw(recorder, waiting(3), st, 0, true, scale);
					TickBarPainter.Layout l = recorder.layout;
					TextRun msg = recorder.runs.stream()
						.filter(r -> r.text.startsWith("Waiting"))
						.findFirst()
						.orElseThrow(() -> new AssertionError("no waiting message"));
					String where = " (width " + width + ", height " + height + ", scale " + scale + ")";

					assertTrue("message fits across the bar" + where,
						msg.x >= l.barX && msg.x + msg.width <= l.barX + l.barW);
					// The bar keeps 3 rows above and below it for click marks; the message must stay inside those.
					assertTrue("message fits the bar's rows" + where,
						msg.y - l.ascent >= l.barY - 3 && msg.y + (l.lineHeight - l.ascent) <= l.barY + l.barH + 3);
					assertFalse("no hit zone while waiting" + where, contains(image, solid(st.getZoneColor())));
				}
			}
		}
	}

	@Test
	public void hitOrMissTextNeverRunsIntoTheTickBoxes()
	{
		boolean shownAtDefaultWidth = false;
		for (double scale : SCALES)
		{
			for (int ticks : new int[]{2, 3})
			{
				// Starts below the settings' minimum on purpose: the painter must cope with any width.
				for (int width = 120; width <= BarStyle.DEFAULT_BAR_WIDTH; width += 5)
				{
					for (boolean window : new boolean[]{false, true})
					{
						BarStyle st = withSize(width, BarStyle.DEFAULT_BAR_HEIGHT, true);
						int reserved = window ? reservedByWindow(st, scale) : 0;
						BufferedImage miss = draw(painter, result(ticks, Result.MISS), st, reserved, window, scale);
						BufferedImage hit = draw(painter, result(ticks, Result.HIT), st, reserved, window, scale);
						BufferedImage none = draw(painter, result(ticks, Result.NONE), st, reserved, window, scale);
						BufferedImage noBoxes = draw(painter, waiting(ticks), st, reserved, window, scale);

						int headerBottom = (int) Math.ceil((layout(st).headerY + layout(st).lineHeight + 2) * scale);
						int[] boxes = changedColumns(none, noBoxes, headerBottom);
						String where = " (" + ticks + " ticks, width " + width + ", scale " + scale
							+ (window ? ", pop-out" : ", overlay") + ")";
						assertTrue("tick boxes are drawn" + where, boxes != null);

						for (BufferedImage withText : Arrays.asList(miss, hit))
						{
							int[] text = changedColumns(withText, none, headerBottom);
							if (text != null)
							{
								assertTrue("result text clear of the boxes" + where, text[0] > boxes[1] + 1);
								if (width == BarStyle.DEFAULT_BAR_WIDTH)
								{
									shownAtDefaultWidth = true;
								}
							}
							else
							{
								assertTrue("result text only left out on narrow bars" + where, width < 190);
							}
						}
					}
				}
			}
		}
		assertTrue(shownAtDefaultWidth);
	}

	@Test
	public void textNeverOverlapsEvenWithHugeCounts()
	{
		int[][] counts = {{0, 0}, {154, 9}, {9999, 99}, {12345, 1234}, {99999, 9999}, {99999, 99999}, {999999, 999999}};
		for (double scale : SCALES)
		{
			for (int width : new int[]{BarStyle.MIN_BAR_WIDTH, 170, 180, BarStyle.DEFAULT_BAR_WIDTH, 400})
			{
				for (int[] c : counts)
				{
					for (boolean window : new boolean[]{false, true})
					{
						BarStyle st = withSize(width, BarStyle.DEFAULT_BAR_HEIGHT, true);
						TickSnapshot s = snapshot(false, 3, 2, 0.9, true, Collections.emptyList(),
							Collections.emptyList(), c[0], c[0], c[0], c[1], Result.MISS, 0);
						RecordingPainter recorder = new RecordingPainter();
						draw(recorder, s, st, window ? reservedByWindow(st, scale) : 0, window, scale);
						String where = " (width " + width + ", " + c[0] + "/" + c[1] + ", scale " + scale + ")";
						assertNoOverlaps(recorder, where);
					}
				}
			}
		}
	}

	@Test
	public void usualCountsKeepAllTheirLabels()
	{
		TickSnapshot s = snapshot(false, 3, 2, 0.9, true, Collections.emptyList(), Collections.emptyList(),
			12, 30, 154, 9, Result.NONE, Long.MAX_VALUE);
		for (double scale : SCALES)
		{
			RecordingPainter recorder = new RecordingPainter();
			draw(recorder, s, style, 0, false, scale);
			for (String label : new String[]{"Streak ", "Best ", "Hits ", "Miss ", "Acc "})
			{
				assertTrue(label + "at scale " + scale, recorder.runs.stream().anyMatch(r -> r.text.equals(label)));
			}
		}
	}

	@Test
	public void statsCloseTheGapBeforeDroppingTheAccLabel()
	{
		// At display scales like 150% and 175% a whole string can measure a pixel narrower than its pieces added
		// up, so the painter has to measure the same pieces it draws.
		int[][] counts = {{154, 9}, {2000, 150}, {6168, 500}, {9999, 99}, {12345, 1234}, {38538, 93958}, {99999, 9999}};
		for (double scale : new double[]{1.0, 1.25, 1.5, 1.75, 2.0})
		{
			for (int[] c : counts)
			{
				for (int width = BarStyle.MIN_BAR_WIDTH; width <= 240; width++)
				{
					BarStyle st = withSize(width, BarStyle.DEFAULT_BAR_HEIGHT, true);
					TickSnapshot s = snapshot(false, 3, 2, 0.9, true, Collections.emptyList(), Collections.emptyList(),
						0, 0, c[0], c[1], Result.NONE, Long.MAX_VALUE);
					RecordingPainter recorder = new RecordingPainter();
					draw(recorder, s, st, 0, true, scale);
					String where = " (width " + width + ", " + c[0] + "/" + c[1] + ", scale " + scale + ")";

					List<TextRun> line = lineWith(recorder, "Hits ");
					assertEquals("Hits " + c[0] + where, Arrays.asList("Hits ", String.valueOf(c[0]), "Miss ",
						String.valueOf(c[1])), Arrays.asList(line.get(0).text, line.get(1).text, line.get(2).text, line.get(3).text));
					int gap = line.get(2).x - (line.get(1).x + line.get(1).width);
					assertTrue("gap between Hits and Miss is " + gap + where,
						gap == TickBarPainter.STATS_GAP || gap == TickBarPainter.MIN_SPACE);

					// Where Miss would end with each gap, and the room "Acc 94%" needs after it.
					int missEnd = line.get(3).x + line.get(3).width - gap;
					String acc = s.getAccuracy() + "%";
					int accWidth = TickBarPainter.MIN_SPACE + recorder.fm.stringWidth("Acc ") + recorder.fm.stringWidth(acc);
					int right = recorder.layout.width - TickBarPainter.PAD;
					boolean fitsWideGap = missEnd + TickBarPainter.STATS_GAP + accWidth <= right;
					boolean fitsNarrowGap = missEnd + TickBarPainter.MIN_SPACE + accWidth <= right;
					boolean labelShown = line.stream().anyMatch(r -> r.text.equals("Acc "));

					assertEquals("wide gap only while the Acc label still fits with it" + where,
						fitsWideGap, gap == TickBarPainter.STATS_GAP);
					assertEquals("Acc label shown whenever it fits" + where, fitsNarrowGap, labelShown);
				}
			}
		}
	}

	/** The pieces of text on the line that has the given piece, left to right. */
	private static List<TextRun> lineWith(RecordingPainter recorder, String text)
	{
		int y = recorder.runs.stream().filter(r -> r.text.equals(text)).findFirst()
			.orElseThrow(() -> new AssertionError("'" + text + "' not drawn")).y;
		List<TextRun> line = new ArrayList<>();
		for (TextRun run : recorder.runs)
		{
			if (run.y == y)
			{
				line.add(run);
			}
		}
		line.sort(Comparator.comparingInt(r -> r.x));
		return line;
	}

	/** No two pieces of text on the same line overlap, and all text stays inside the panel. */
	private static void assertNoOverlaps(RecordingPainter recorder, String where)
	{
		Map<Integer, List<TextRun>> lines = new TreeMap<>();
		for (TextRun run : recorder.runs)
		{
			lines.computeIfAbsent(run.y, y -> new ArrayList<>()).add(run);
		}
		for (List<TextRun> line : lines.values())
		{
			line.sort(Comparator.comparingInt(r -> r.x));
			for (int i = 0; i < line.size(); i++)
			{
				TextRun run = line.get(i);
				assertTrue("'" + run.text + "' inside the panel" + where,
					run.x >= 0 && run.x + run.width <= recorder.layout.width);
				if (i > 0)
				{
					TextRun before = line.get(i - 1);
					assertTrue("'" + before.text + "' runs into '" + run.text + "'" + where,
						before.x + before.width <= run.x);
				}
			}
		}
	}

	/** What the pop-out window keeps free for its buttons, measured like the window does. */
	private static int reservedByWindow(BarStyle st, double scale)
	{
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = probe.createGraphics();
		try
		{
			g.scale(scale, scale);
			FontMetrics fm = g.getFontMetrics(TickBarPainter.font());
			return TickTrackerWindow.reservedHeaderWidth(fm, TickBarPainter.layout(fm, st));
		}
		finally
		{
			g.dispose();
		}
	}

	/** The leftmost and rightmost columns, above the given row, where the two images differ (null if none). */
	private static int[] changedColumns(BufferedImage a, BufferedImage b, int belowRow)
	{
		int min = Integer.MAX_VALUE;
		int max = -1;
		for (int y = 0; y < Math.min(belowRow, a.getHeight()); y++)
		{
			for (int x = 0; x < a.getWidth(); x++)
			{
				if (a.getRGB(x, y) != b.getRGB(x, y))
				{
					min = Math.min(min, x);
					max = Math.max(max, x);
				}
			}
		}
		return max < 0 ? null : new int[]{min, max};
	}

	private static boolean contains(BufferedImage image, Color color)
	{
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				if (image.getRGB(x, y) == color.getRGB())
				{
					return true;
				}
			}
		}
		return false;
	}

	private BufferedImage render(TickSnapshot s, BarStyle st, String file, boolean opaque) throws IOException
	{
		TickBarPainter.Layout l = layout(st);
		BufferedImage image = draw(painter, s, st, 0, opaque, 1.0);
		assertEquals(new Dimension(l.width, l.height), new Dimension(image.getWidth(), image.getHeight()));

		// Save a 3x copy so the pixel font is easy to look at.
		BufferedImage big = new BufferedImage(l.width * 3, l.height * 3, BufferedImage.TYPE_INT_ARGB);
		Graphics2D bg = big.createGraphics();
		bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		bg.drawImage(image, 0, 0, big.getWidth(), big.getHeight(), null);
		bg.dispose();
		assertTrue(PREVIEW_DIR.isDirectory() || PREVIEW_DIR.mkdirs());
		ImageIO.write(big, "png", new File(PREVIEW_DIR, file));
		return image;
	}

	/** Paints into an image the way the overlay (or, scaled, a HiDPI pop-out window) would. */
	private static BufferedImage draw(TickBarPainter p, TickSnapshot s, BarStyle st, int reserved, boolean opaque, double scale)
	{
		TickBarPainter.Layout l = layout(st);
		BufferedImage image = new BufferedImage((int) Math.ceil(l.width * scale) + 2, (int) Math.ceil(l.height * scale) + 2,
			BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(BACKDROP);
		g.fillRect(0, 0, image.getWidth(), image.getHeight());
		g.scale(scale, scale);
		Dimension size = p.paint(g, s, st, reserved, opaque);
		g.dispose();
		if (scale == 1.0)
		{
			BufferedImage exact = image.getSubimage(0, 0, size.width, size.height);
			BufferedImage copy = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D cg = copy.createGraphics();
			cg.drawImage(exact, 0, 0, null);
			cg.dispose();
			return copy;
		}
		return image;
	}

	private static TickBarPainter.Layout layout(BarStyle st)
	{
		BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = probe.createGraphics();
		try
		{
			return TickBarPainter.layout(g.getFontMetrics(TickBarPainter.font()), st);
		}
		finally
		{
			g.dispose();
		}
	}

	private BarStyle withSize(int width, int height, boolean stats)
	{
		return new BarStyle(width, height, stats, true, style.getMarkStyle(), style.getBarColor(), style.getHitColor(),
			style.getMissColor(), style.getZoneColor(), style.getBackgroundColor());
	}

	private BarStyle withMarks(MarkStyle marks)
	{
		return new BarStyle(style.getBarWidth(), style.getBarHeight(), true, true, marks, style.getBarColor(),
			style.getHitColor(), style.getMissColor(), style.getZoneColor(), style.getBackgroundColor());
	}

	private static int markX(TickBarPainter.Layout l, double position)
	{
		return l.barX + (int) Math.round(l.barW * position);
	}

	/**
	 * A whole X around the given column, in the mark's color with a black edge: both diagonals are two pixels
	 * wide, so its middle covers that column and the one to its left, and each arm ends at the arm's length
	 * up or down.
	 */
	private static void assertCross(BufferedImage image, TickBarPainter.Layout l, int x, Color color, String what)
	{
		int y = l.barY + l.barH / 2;
		int arm = TickBarPainter.crossArm(l);
		for (int i = -arm; i <= arm; i++)
		{
			for (int row : new int[]{i, -i})
			{
				String at = what + ": the X at " + i + "," + row;
				assertEquals(at, color.getRGB(), image.getRGB(x + i - 1, y + row));
				assertEquals(at, color.getRGB(), image.getRGB(x + i, y + row));
			}
		}
		assertEquals(what + ": black edge beside the middle", Color.BLACK.getRGB(), image.getRGB(x + 1, y));
		assertEquals(what + ": black edge beside the middle", Color.BLACK.getRGB(), image.getRGB(x - 2, y));
		for (int dx : new int[]{-arm, arm})
		{
			for (int dy : new int[]{-arm, arm})
			{
				String end = what + ": end of the arm at " + dx + "," + dy;
				assertEquals(end, color.getRGB(), image.getRGB(x + dx, y + dy));
				assertEquals(end, color.getRGB(), image.getRGB(x + dx - 1, y + dy));
				assertEquals(end + " has a black edge", Color.BLACK.getRGB(), image.getRGB(x + dx + (dx > 0 ? 1 : -2), y + dy));
			}
		}
	}

	private static int brightness(int argb)
	{
		Color c = new Color(argb);
		return (c.getRed() + c.getGreen() + c.getBlue()) / 3;
	}

	/** How far apart two colors are: the red, green and blue differences added up. */
	private static int difference(int a, int b)
	{
		Color x = new Color(a);
		Color y = new Color(b);
		return Math.abs(x.getRed() - y.getRed()) + Math.abs(x.getGreen() - y.getGreen()) + Math.abs(x.getBlue() - y.getBlue());
	}

	/** A 1-tick bar near its start (marks at 0.5 sit on the dark track) with these marks. */
	private static TickSnapshot faded(List<Mark> marks, List<Mark> previous)
	{
		return snapshot(false, 1, 0, 0.1, false, marks, previous, 0, 0, 0, 0, Result.NONE, Long.MAX_VALUE);
	}

	private static Color colorOf(RecordingPainter recorder, String text)
	{
		return recorder.runs.stream().filter(r -> r.text.equals(text)).findFirst()
			.orElseThrow(() -> new AssertionError("'" + text + "' not drawn")).color;
	}

	private static Color solid(Color c)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue());
	}

	private static TickSnapshot waiting(int cycle)
	{
		return snapshot(true, cycle, 0, 0, false, Collections.emptyList(), Collections.emptyList(),
			0, 0, 0, 0, Result.NONE, Long.MAX_VALUE);
	}

	/** On the target tick with nothing on the bar, just after a hit or a miss (or neither). */
	private static TickSnapshot result(int cycle, Result result)
	{
		return snapshot(false, cycle, cycle - 1, 0.95, false, Collections.emptyList(), Collections.emptyList(),
			0, 3, 10, 2, result, result == Result.NONE ? Long.MAX_VALUE : 0);
	}

	private static TickSnapshot snapshot(boolean waiting, int cycle, int tickInCycle, double progress, boolean hit,
		List<Mark> marks, List<Mark> previous, int streak, int best, int hits, int misses, Result result, long ageMs)
	{
		return new TickSnapshot(waiting, cycle, true, tickInCycle, progress, hit, marks, previous,
			streak, best, hits, misses, result, ageMs, 0);
	}

	/** The same snapshot with an input lag: this share of each tick, at its end, is too late for it. */
	private static TickSnapshot withLate(TickSnapshot s, double lateWindow)
	{
		return new TickSnapshot(s.isWaiting(), s.getCycleLength(), s.isLastTickOnly(), s.getTickInCycle(),
			s.getProgress(), s.isHitThisCycle(), s.getMarks(), s.getPreviousMarks(), s.getStreak(), s.getBestStreak(),
			s.getHits(), s.getMisses(), s.getLastResult(), s.getResultAgeMs(), lateWindow);
	}

	private static final class TextRun
	{
		final String text;
		final int x;
		final int y;
		final int width;
		final Color color;

		TextRun(String text, int x, int y, int width, Color color)
		{
			this.text = text;
			this.x = x;
			this.y = y;
			this.width = width;
			this.color = color;
		}
	}

	/** Remembers every piece of text the painter draws, measured with the same font metrics. */
	private static final class RecordingPainter extends TickBarPainter
	{
		final List<TextRun> runs = new ArrayList<>();
		TickBarPainter.Layout layout;
		/** The font metrics the painter measured with. */
		FontMetrics fm;

		@Override
		void text(Graphics2D g, String str, int x, int y, Color color)
		{
			FontMetrics fm = g.getFontMetrics();
			if (layout == null)
			{
				// Each test draws one style per recorder, so the layout of the first text is the layout.
				layout = TickBarPainter.layout(fm, currentStyle);
				this.fm = fm;
			}
			runs.add(new TextRun(str, x, y, fm.stringWidth(str), color));
			super.text(g, str, x, y, color);
		}

		private BarStyle currentStyle;

		@Override
		Dimension paint(Graphics2D g, TickSnapshot s, BarStyle st, int reservedRight, boolean opaque)
		{
			currentStyle = st;
			return super.paint(g, s, st, reservedRight, opaque);
		}
	}
}
