/*
 * Copyright (c) 2026, Tick Tracker Bar contributors
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.List;
import net.runelite.client.ui.FontManager;

/**
 * Draws the tick bar, the click marks and the stats, styled like the game's own interfaces. Used by
 * both the in-game overlay and the pop-out window, so the two always look the same.
 *
 * <pre>
 *  3-TICK [■][■][■]                     HIT
 *  [██████████|██████████|███X██│▒▒▒]   &lt;- bar, tick dividers, green target zone, your clicks, now line
 *  Streak 12                          Best 30
 *  Hits 154  Miss 9                   Acc 94%
 * </pre>
 */
class TickBarPainter
{
	static final int PAD = 6;
	private static final int GAP = 4;
	private static final int MARK_OVERHANG = 3;
	private static final int BOX_SIZE = 7;
	private static final int BOX_GAP = 3;
	/** Smallest space between two pieces of text on the same line. */
	static final int MIN_SPACE = 6;
	/** Space between Hits and Miss, closed to {@link #MIN_SPACE} when the line gets crowded. */
	static final int STATS_GAP = 10;
	/** Smallest space between the tick boxes and the HIT / MISS text. */
	private static final int RESULT_GAP = 4;
	/** The biggest X a click can get: about the size of the game's own click cross. */
	static final int MAX_CROSS_ARM = 7;

	static final long HIT_FLASH_MS = 200;
	static final long MISS_FLASH_MS = 600;
	static final long RESULT_TEXT_MS = 900;

	static final String WAITING = "Waiting for game ticks...";
	private static final String WAITING_SHORT = "Waiting...";

	/** The game's interface text orange, used for its titles and labels. */
	static final Color GAME_ORANGE = new Color(255, 152, 31);
	/** The game's bright yellow, used for text it wants you to notice. */
	static final Color GAME_YELLOW = new Color(255, 255, 0);

	private static final Color PANEL_EDGE = new Color(0, 0, 0, 200);
	private static final Color TRACK = new Color(0, 0, 0, 150);
	private static final Color DIVIDER = new Color(0, 0, 0, 230);
	private static final Color FRAME = new Color(0, 0, 0, 230);
	private static final Color LABEL = GAME_ORANGE;
	private static final Color VALUE = Color.WHITE;
	private static final Color STREAK = GAME_YELLOW;
	static final Color NEUTRAL_MARK = new Color(190, 190, 190);
	static final Color SYNC_MARK = GAME_YELLOW;
	/** The game's cyan: a click too late for its cycle, which counts for the next tick. */
	static final Color LATE_MARK = new Color(0, 255, 255);
	private static final Color HIT_MARK = Color.WHITE;
	static final Color NOW_LINE = Color.WHITE;
	/** How strong the miss-colored stripes over the too-late end of the bar are. */
	static final int LATE_STRIPE_ALPHA = 140;
	private static final Color BOX_DONE = new Color(255, 152, 31, 120);
	private static final Color BOX_EMPTY = new Color(0, 0, 0, 110);
	private static final Color BOX_EDGE = new Color(0, 0, 0, 220);
	/** Last cycle's marks are drawn this faint (out of 255)... */
	static final int GHOST_ALPHA = 80;
	/** ...except the red wrong-tick marks of a missed cycle, which only ever show there. */
	static final int WRONG_TICK_ALPHA = 200;
	/** After a hit the bar turns a darker shade of the hit color, so the hit's own X shows up on it. */
	static final double HIT_FILL_SHADE = 0.4;

	/** One X per arm length, drawn around (0, 0). */
	private static final Cross[] CROSSES = new Cross[MAX_CROSS_ARM + 1];

	/** The stripes over the too-late end of the bar, made for {@link #stripeColor}. */
	private TexturePaint stripes;
	private Color stripeColor;

	static
	{
		for (int arm = 1; arm <= MAX_CROSS_ARM; arm++)
		{
			CROSSES[arm] = new Cross(arm);
		}
	}

	/** Where everything goes. The pop-out window also uses this to place its buttons. */
	static final class Layout
	{
		int width;
		int height;
		int ascent;
		int lineHeight;
		int headerY;
		int barX;
		int barY;
		int barW;
		int barH;
		int statsY;
	}

	/**
	 * A pixel X like the game's click cross: two diagonals two pixels wide, with a one pixel black edge.
	 * The pixels {@code (-1, 0)} and {@code (0, 0)} are its middle, like the two pixel wide line marks.
	 */
	static final class Cross
	{
		final int arm;
		final Area core = new Area();
		final Area outline = new Area();

		Cross(int arm)
		{
			this.arm = arm;
			for (int i = -arm; i <= arm; i++)
			{
				for (int row : new int[]{i, -i})
				{
					core.add(new Area(new Rectangle(i - 1, row, 2, 1)));
					outline.add(new Area(new Rectangle(i - 2, row - 1, 4, 3)));
				}
			}
			outline.subtract(core);
		}
	}

	static Font font()
	{
		return FontManager.getRunescapeSmallFont();
	}

	/** Two pixel wide diagonal stripes in the given color, four pixels apart. Made again only when the color changes. */
	private TexturePaint stripes(Color color)
	{
		if (!color.equals(stripeColor))
		{
			BufferedImage tile = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 4; y++)
			{
				for (int x = 0; x < 4; x++)
				{
					if ((x + y) % 4 < 2)
					{
						tile.setRGB(x, y, color.getRGB());
					}
				}
			}
			stripes = new TexturePaint(tile, new Rectangle(0, 0, 4, 4));
			stripeColor = color;
		}
		return stripes;
	}

	/**
	 * How many pixels at the end of each tick on the bar are too late for a click to count in that tick
	 * (see the input lag setting).
	 */
	static int lateWidth(Layout l, TickSnapshot s)
	{
		double window = Math.max(0, Math.min(1, s.getLateWindow()));
		return (int) Math.round(l.barW * window / s.getCycleLength());
	}

	static Layout layout(FontMetrics fm, BarStyle style)
	{
		Layout l = new Layout();
		l.ascent = fm.getAscent();
		l.lineHeight = fm.getAscent() + fm.getDescent();
		l.barW = style.getBarWidth();
		l.barH = style.getBarHeight();
		l.barX = PAD;
		l.width = l.barW + PAD * 2;

		int y = PAD;
		l.headerY = y;
		y += l.lineHeight + GAP;
		l.barY = y + MARK_OVERHANG;
		y = l.barY + l.barH + MARK_OVERHANG;
		if (style.isShowStats())
		{
			y += GAP;
			l.statsY = y;
			y += l.lineHeight * 2;
		}
		l.height = y + PAD;
		return l;
	}

	/** How long each arm of a click's X is on a bar this tall: it fills the bar, up to the game's cross size. */
	static int crossArm(Layout l)
	{
		return Math.max(3, Math.min(MAX_CROSS_ARM, (l.barH + 2 * MARK_OVERHANG) / 2 - 3));
	}

	/**
	 * @param reservedRight pixels at the right end of the header to leave empty (the window's buttons go there)
	 * @param opaque        paint the background without transparency (the pop-out window is opaque)
	 * @return the size of what was drawn
	 */
	Dimension paint(Graphics2D g, TickSnapshot s, BarStyle style, int reservedRight, boolean opaque)
	{
		g.setFont(font());
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		// Crisp pixel edges, like the game's own interfaces.
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		FontMetrics fm = g.getFontMetrics();
		Layout l = layout(fm, style);

		Color bg = opaque ? solid(style.getBackgroundColor()) : style.getBackgroundColor();
		g.setColor(bg);
		g.fillRect(0, 0, l.width, l.height);
		paintFrame(g, 0, 0, l.width, l.height, bg);

		paintHeader(g, fm, l, s, style, reservedRight);
		if (s.isWaiting())
		{
			paintWaiting(g, fm, l);
		}
		else
		{
			paintBar(g, l, s, style);
		}
		if (style.isShowStats())
		{
			paintStats(g, fm, l, s);
		}
		return new Dimension(l.width, l.height);
	}

	/**
	 * A frame like the game's interface boxes: a black edge, with a lighter line along the top and left
	 * inside it and a darker one along the bottom and right, so the box looks raised.
	 */
	static void paintFrame(Graphics2D g, int x, int y, int w, int h, Color base)
	{
		g.setColor(PANEL_EDGE);
		g.drawRect(x, y, w - 1, h - 1);
		g.setColor(blend(base, Color.WHITE, 0.18));
		g.fillRect(x + 1, y + 1, w - 3, 1);
		g.fillRect(x + 1, y + 2, 1, h - 4);
		g.setColor(blend(base, Color.BLACK, 0.45));
		g.fillRect(x + 1, y + h - 2, w - 2, 1);
		g.fillRect(x + w - 2, y + 1, 1, h - 3);
	}

	private void paintHeader(Graphics2D g, FontMetrics fm, Layout l, TickSnapshot s, BarStyle style, int reservedRight)
	{
		int baseline = l.headerY + l.ascent;
		int n = s.getCycleLength();
		String mode = n + "-TICK";
		text(g, mode, PAD, baseline, LABEL);
		// Where the left part of the header ends (+1 for the text shadow).
		int leftEnd = PAD + fm.stringWidth(mode) + 1;

		// One little box per tick in the cycle: filled up to the current tick, target tick outlined green.
		if (n > 1 && !s.isWaiting())
		{
			int x = PAD + fm.stringWidth(mode) + 6;
			int y = l.headerY + (l.lineHeight - BOX_SIZE) / 2;
			for (int i = 0; i < n; i++)
			{
				int bx = x + i * (BOX_SIZE + BOX_GAP);
				if (i < s.getTickInCycle())
				{
					g.setColor(BOX_DONE);
				}
				else
				{
					g.setColor(i == s.getTickInCycle() ? GAME_YELLOW : BOX_EMPTY);
				}
				g.fillRect(bx, y, BOX_SIZE, BOX_SIZE);
				boolean target = s.isLastTickOnly() && i == n - 1;
				g.setColor(target ? solid(style.getZoneColor()) : BOX_EDGE);
				g.drawRect(bx, y, BOX_SIZE - 1, BOX_SIZE - 1);
			}
			leftEnd = x + n * (BOX_SIZE + BOX_GAP) - BOX_GAP;
		}

		// HIT / MISS at the right, fading out.
		long age = s.getResultAgeMs();
		if (s.isWaiting() || age >= RESULT_TEXT_MS)
		{
			return;
		}
		String result;
		Color color;
		if (s.getLastResult() == TickSnapshot.Result.HIT)
		{
			result = "HIT";
			color = style.getHitColor();
		}
		else if (s.getLastResult() == TickSnapshot.Result.MISS)
		{
			result = "MISS";
			color = style.getMissColor();
		}
		else
		{
			return;
		}

		int right = l.width - PAD - reservedRight;
		// On a very narrow bar there's no room next to the tick boxes. Measured with the wider word so HIT
		// and MISS both show or both don't; the bar's frame still flashes either way.
		if (right - fm.stringWidth("MISS") < leftEnd + RESULT_GAP)
		{
			return;
		}
		int alpha = (int) (255 * (1 - (double) age / RESULT_TEXT_MS));
		text(g, result, right - fm.stringWidth(result), baseline, withAlpha(color, alpha));
	}

	private void paintBar(Graphics2D g, Layout l, TickSnapshot s, BarStyle style)
	{
		int x = l.barX;
		int y = l.barY;
		int w = l.barW;
		int h = l.barH;
		int n = s.getCycleLength();

		g.setColor(TRACK);
		g.fillRect(x, y, w, h);

		// The target tick (last tick of a 2t / 3t cycle) is shaded green: where a click counts as a hit. With an
		// input lag the game takes clicks in that much earlier, so the zone starts and ends that much earlier.
		int late = lateWidth(l, s);
		boolean zone = s.isLastTickOnly() && n > 1;
		int zoneX = x + w * (n - 1) / n - late;
		int zoneEnd = x + w - late;
		if (zone)
		{
			g.setColor(style.getZoneColor());
			g.fillRect(zoneX, y, zoneEnd - zoneX, h);
		}

		int fill = (int) Math.round(w * clamp01(s.getProgress()));
		g.setColor(s.isHitThisCycle() ? hitFill(style) : style.getBarColor());
		g.fillRect(x, y, fill, h);

		// The end of the cycle, too late for a click to count in it: such a click counts for the next tick.
		if (late > 0)
		{
			g.setPaint(stripes(withAlpha(solid(style.getMissColor()), LATE_STRIPE_ALPHA)));
			g.fillRect(x + w - late, y, late, h);
		}

		if (zone)
		{
			g.setColor(solid(style.getZoneColor()));
			g.drawRect(zoneX, y, zoneEnd - zoneX - 1, h - 1);
		}

		g.setColor(DIVIDER);
		for (int i = 1; i < n; i++)
		{
			g.fillRect(x + w * i / n - 1, y, 2, h);
		}
		g.setColor(FRAME);
		g.drawRect(x - 1, y - 1, w + 1, h + 1);

		// The now line and the marks reach past the frame, so they go over it. The line goes under the
		// marks, so the X of a click you just made shows on top of it.
		if (style.getMarkStyle().isNowLine())
		{
			paintNowLine(g, l, s);
		}
		if (style.isShowLastMarks())
		{
			paintMarks(g, l, s.getPreviousMarks(), style, true);
		}
		paintMarks(g, l, s.getMarks(), style, false);

		// The frame flashes green on a hit and red on a miss.
		Color flash = flashColor(s, style);
		if (flash != null)
		{
			g.setColor(flash);
			g.drawRect(x - 1, y - 1, w + 1, h + 1);
			g.drawRect(x - 2, y - 2, w + 3, h + 3);
		}
	}

	/**
	 * No ticks are arriving, so the zone, dividers and marks mean nothing. Draws a plain track with the
	 * message, made as tall as a line of text when the bar is thinner than that. It still fits in the
	 * rows the layout keeps for the bar and its marks, so the size doesn't change.
	 */
	private void paintWaiting(Graphics2D g, FontMetrics fm, Layout l)
	{
		int h = Math.max(l.barH, l.lineHeight);
		int top = l.barY - (h - l.barH) / 2;
		g.setColor(TRACK);
		g.fillRect(l.barX, top, l.barW, h);
		g.setColor(FRAME);
		g.drawRect(l.barX - 1, top - 1, l.barW + 1, h + 1);

		String msg = fm.stringWidth(WAITING) + 2 <= l.barW ? WAITING : WAITING_SHORT;
		int tx = l.barX + (l.barW - fm.stringWidth(msg)) / 2;
		int ty = top + (h - l.lineHeight) / 2 + l.ascent;
		text(g, msg, tx, ty, LABEL);
	}

	/** A white line where a click made right now would put its mark: the front edge of the fill. */
	private void paintNowLine(Graphics2D g, Layout l, TickSnapshot s)
	{
		int x = markX(l, s.getProgress());
		int top = l.barY - MARK_OVERHANG;
		int bottom = l.barY + l.barH + MARK_OVERHANG;
		g.setColor(Color.BLACK);
		g.fillRect(x - 2, top - 1, 4, bottom - top + 2);
		g.setColor(NOW_LINE);
		g.fillRect(x - 1, top, 2, bottom - top);
	}

	/**
	 * Draws the grey marks first and the colored ones over them, so a click right after a hit doesn't cover
	 * the hit's X. In the faded copy of last cycle, red marks stay strong: they only ever show there, and they
	 * show why a cycle was missed.
	 */
	private void paintMarks(Graphics2D g, Layout l, List<TickSnapshot.Mark> marks, BarStyle style, boolean lastCycle)
	{
		for (boolean grey : new boolean[]{true, false})
		{
			for (TickSnapshot.Mark mark : marks)
			{
				TickSnapshot.MarkType type = mark.getType();
				if ((type == TickSnapshot.MarkType.EXTRA || type == TickSnapshot.MarkType.OFF_TICK) != grey)
				{
					continue;
				}
				int alpha = !lastCycle ? 255 : type == TickSnapshot.MarkType.WRONG_TICK ? WRONG_TICK_ALPHA : GHOST_ALPHA;
				paintMark(g, l, mark, style, alpha);
			}
		}
	}

	private void paintMark(Graphics2D g, Layout l, TickSnapshot.Mark mark, BarStyle style, int alpha)
	{
		if (style.getMarkStyle().isCross())
		{
			paintCross(g, l, mark, style, alpha);
		}
		else
		{
			paintLine(g, l, mark, style, alpha);
		}
	}

	/** An X where you clicked, colored by what the click did. */
	private void paintCross(Graphics2D g, Layout l, TickSnapshot.Mark mark, BarStyle style, int alpha)
	{
		int x = markX(l, mark.getPosition());
		int y = l.barY + l.barH / 2;
		Cross cross = CROSSES[crossArm(l)];

		// A click at the very start or end of the bar keeps its X where it happened; the panel's edge cuts
		// off what doesn't fit.
		Shape clip = g.getClip();
		g.clipRect(0, 0, l.width, l.height);
		g.translate(x, y);
		g.setColor(new Color(0, 0, 0, alpha));
		g.fill(cross.outline);
		g.setColor(withAlpha(markColor(mark.getType(), style), alpha));
		g.fill(cross.core);
		g.translate(-x, -y);
		g.setClip(clip);
	}

	/** A line through the bar where you clicked: colored tips above and below it, and a core through it. */
	private void paintLine(Graphics2D g, Layout l, TickSnapshot.Mark mark, BarStyle style, int alpha)
	{
		int x = markX(l, mark.getPosition());
		int top = l.barY - MARK_OVERHANG;
		int bottom = l.barY + l.barH + MARK_OVERHANG;

		Color tips = markColor(mark.getType(), style);
		// A hit's core is white so it shows on the green the bar turns after a hit.
		Color core = mark.getType() == TickSnapshot.MarkType.HIT ? HIT_MARK : tips;

		// Dark outline so the mark shows up on any fill color.
		g.setColor(new Color(0, 0, 0, alpha * 3 / 4));
		g.fillRect(x - 2, top - 1, 4, bottom - top + 2);
		g.setColor(withAlpha(tips, alpha));
		g.fillRect(x - 1, top, 2, bottom - top);
		g.setColor(withAlpha(core, alpha));
		g.fillRect(x - 1, l.barY, 2, l.barH);
	}

	/**
	 * The bar's fill once you've hit this cycle: a darker shade of the hit color. A hit's X is drawn in the hit
	 * color itself, so it stands out on it, and the bar still changes brightness when you hit, which shows
	 * even to players who can't tell the orange from the green.
	 */
	static Color hitFill(BarStyle style)
	{
		return blend(style.getHitColor(), Color.BLACK, HIT_FILL_SHADE);
	}

	private static Color markColor(TickSnapshot.MarkType type, BarStyle style)
	{
		switch (type)
		{
			case HIT:
				return style.getHitColor();
			case WRONG_TICK:
				return style.getMissColor();
			case SYNC:
				return SYNC_MARK;
			case LATE:
				return LATE_MARK;
			default:
				// EXTRA and OFF_TICK: clicks that didn't need to count.
				return NEUTRAL_MARK;
		}
	}

	/** The column of a point on the bar; a mark there takes this column and the one to its left. */
	private static int markX(Layout l, double position)
	{
		int x = l.barX + (int) Math.round(l.barW * clamp01(position));
		return Math.max(l.barX + 1, Math.min(l.barX + l.barW - 1, x));
	}

	private void paintStats(Graphics2D g, FontMetrics fm, Layout l, TickSnapshot s)
	{
		int left = PAD;
		int right = l.width - PAD;
		int line1 = l.statsY + l.ascent;
		int line2 = line1 + l.lineHeight;

		int end = pair(g, fm, "Streak ", String.valueOf(s.getStreak()), left, line1, STREAK);
		pairRight(g, fm, "Best ", String.valueOf(s.getBestStreak()), right, line1, end);

		String hits = String.valueOf(s.getHits());
		String misses = String.valueOf(s.getMisses());
		int accuracy = s.getAccuracy();
		String acc = accuracy < 0 ? "-" : accuracy + "%";
		// Big counts on a narrow bar: first close the gap between Hits and Miss, then drop the "Acc" label.
		// Measure the pieces pair() and pairRight() draw: when the display is scaled (say 150%) a whole string
		// can measure a pixel narrower than its pieces added up.
		int needed = fm.stringWidth("Hits ") + fm.stringWidth(hits) + STATS_GAP
			+ fm.stringWidth("Miss ") + fm.stringWidth(misses)
			+ MIN_SPACE + fm.stringWidth("Acc ") + fm.stringWidth(acc);
		int gap = left + needed <= right ? STATS_GAP : MIN_SPACE;
		end = pair(g, fm, "Hits ", hits, left, line2, VALUE);
		end = pair(g, fm, "Miss ", misses, end + gap, line2, VALUE);
		pairRight(g, fm, "Acc ", acc, right, line2, end);
	}

	private static Color flashColor(TickSnapshot s, BarStyle style)
	{
		long age = s.getResultAgeMs();
		if (s.getLastResult() == TickSnapshot.Result.MISS && age < MISS_FLASH_MS)
		{
			return withAlpha(style.getMissColor(), (int) (255 * (1 - (double) age / MISS_FLASH_MS)));
		}
		if (s.getLastResult() == TickSnapshot.Result.HIT && age < HIT_FLASH_MS)
		{
			return withAlpha(style.getHitColor(), (int) (255 * (1 - (double) age / HIT_FLASH_MS)));
		}
		return null;
	}

	/** Draws "label value" and returns the x where it ended. */
	private int pair(Graphics2D g, FontMetrics fm, String label, String value, int x, int baseline, Color valueColor)
	{
		text(g, label, x, baseline, LABEL);
		x += fm.stringWidth(label);
		text(g, value, x, baseline, valueColor);
		return x + fm.stringWidth(value);
	}

	/**
	 * Right-aligned "label value". If it would run into the text that ends at {@code leftEnd}, the label
	 * is left out, and if even the value doesn't fit, nothing is drawn.
	 */
	private void pairRight(Graphics2D g, FontMetrics fm, String label, String value, int right, int baseline, int leftEnd)
	{
		int valueX = right - fm.stringWidth(value);
		int labelX = valueX - fm.stringWidth(label);
		if (labelX >= leftEnd + MIN_SPACE)
		{
			pair(g, fm, label, value, labelX, baseline, VALUE);
		}
		else if (valueX >= leftEnd + MIN_SPACE)
		{
			text(g, value, valueX, baseline, VALUE);
		}
	}

	/** Text with a 1px black shadow, like the game's own. Tests override this to see the text. */
	void text(Graphics2D g, String str, int x, int y, Color color)
	{
		g.setColor(new Color(0, 0, 0, color.getAlpha()));
		g.drawString(str, x + 1, y + 1);
		g.setColor(color);
		g.drawString(str, x, y);
	}

	private static Color withAlpha(Color c, int alpha)
	{
		int a = Math.max(0, Math.min(255, c.getAlpha() * alpha / 255));
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	static Color solid(Color c)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue());
	}

	/** {@code c} moved {@code amount} (0 to 1) of the way towards {@code toward}, keeping its transparency. */
	static Color blend(Color c, Color toward, double amount)
	{
		return new Color(
			(int) Math.round(c.getRed() + (toward.getRed() - c.getRed()) * amount),
			(int) Math.round(c.getGreen() + (toward.getGreen() - c.getGreen()) * amount),
			(int) Math.round(c.getBlue() + (toward.getBlue() - c.getBlue()) * amount),
			c.getAlpha());
	}

	private static double clamp01(double v)
	{
		return Math.max(0, Math.min(1, v));
	}
}
