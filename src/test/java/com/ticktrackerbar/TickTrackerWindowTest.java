/*
 * Copyright (c) 2026, ETPhone12
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import java.awt.Color;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Drives the pop-out window with mouse events without ever showing it on screen.
 * Skipped on machines without a desktop (such as build servers).
 */
public class TickTrackerWindowTest
{
	private final AtomicInteger resets = new AtomicInteger();
	private final AtomicInteger closes = new AtomicInteger();
	private final AtomicReference<Point> moved = new AtomicReference<>();
	private TickTrackerWindow window;

	@Before
	public void setUp() throws Exception
	{
		Assume.assumeFalse("needs a desktop", GraphicsEnvironment.isHeadless());
		TickTracker tracker = new TickTracker();
		tracker.onGameTick(System.nanoTime());
		SwingUtilities.invokeAndWait(() ->
		{
			window = new TickTrackerWindow(null, tracker, BarStyle::defaults, resets::incrementAndGet,
				closes::incrementAndGet, moved::set);
			window.applySettings(true, false, 100);
			window.setLocation(100, 100);
		});
	}

	@After
	public void tearDown() throws Exception
	{
		if (window != null)
		{
			SwingUtilities.invokeAndWait(window::closeWindow);
		}
	}

	@Test
	public void dragsOntoAMonitorLeftOfTheMainOne() throws Exception
	{
		// Monitors to the left of (or above) the main screen have negative coordinates.
		SwingUtilities.invokeAndWait(() ->
		{
			Component panel = window.getContentPane();
			press(panel, 60, 40, 160, 140);
			drag(panel, -800, 250);
			drag(panel, -1540, 340);
			release(panel, -1540, 340);
		});
		assertEquals(new Point(-1600, 300), window.getLocation());
		assertEquals("new position is saved", new Point(-1600, 300), moved.get());
	}

	@Test
	public void dragsOntoAMonitorRightOfTheMainOne() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Component panel = window.getContentPane();
			press(panel, 60, 40, 160, 140);
			drag(panel, 2960, 540);
			release(panel, 2960, 540);
		});
		assertEquals(new Point(2900, 500), window.getLocation());
		assertEquals(new Point(2900, 500), moved.get());
	}

	@Test
	public void lockedWindowDoesNotMove() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			window.applySettings(true, true, 100);
			Component panel = window.getContentPane();
			press(panel, 60, 40, 160, 140);
			drag(panel, 900, 900);
			release(panel, 900, 900);
		});
		assertEquals(new Point(100, 100), window.getLocation());
		assertNull(moved.get());
	}

	@Test
	public void buttonsResetAndCloseWithoutMovingTheWindow() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			Component panel = window.getContentPane();
			click(panel, center(window.resetButtonBounds()));
			click(panel, center(window.closeButtonBounds()));
		});
		assertEquals(1, resets.get());
		assertEquals(1, closes.get());
		assertEquals(new Point(100, 100), window.getLocation());
		assertNull(moved.get());
	}

	@Test
	public void sizesToTheBarAndDrawsButtons() throws Exception
	{
		BufferedImage[] image = new BufferedImage[1];
		SwingUtilities.invokeAndWait(() ->
		{
			Component panel = window.getContentPane();
			image[0] = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image[0].createGraphics();
			panel.paint(g);
			g.dispose();
		});

		assertEquals(window.getContentPane().getPreferredSize(), window.getSize());
		assertEquals(BarStyle.DEFAULT_BAR_WIDTH + 2 * TickBarPainter.PAD, window.getWidth());
		Rectangle reset = window.resetButtonBounds();
		Rectangle close = window.closeButtonBounds();
		assertFalse(reset.isEmpty());
		assertFalse("buttons don't overlap", reset.intersects(close));
		assertTrue("buttons fit in the window", new Rectangle(window.getSize()).contains(close));

		// Raised like the game's buttons, with an orange label.
		int face = brightness(image[0].getRGB(reset.x + 2, reset.y + reset.height / 2));
		assertTrue("lighter along the top", brightness(image[0].getRGB(reset.x + 2, reset.y + 1)) > face);
		assertTrue("darker along the bottom", brightness(image[0].getRGB(reset.x + 2, reset.y + reset.height - 2)) < face);
		boolean orange = false;
		for (int y = reset.y; y < reset.y + reset.height; y++)
		{
			for (int x = reset.x; x < reset.x + reset.width; x++)
			{
				orange |= image[0].getRGB(x, y) == TickBarPainter.GAME_ORANGE.getRGB();
			}
		}
		assertTrue("orange label", orange);

		BufferedImage big = new BufferedImage(image[0].getWidth() * 3, image[0].getHeight() * 3, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = big.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(image[0], 0, 0, big.getWidth(), big.getHeight(), null);
		g.dispose();
		File dir = new File("build/previews");
		assertTrue(dir.isDirectory() || dir.mkdirs());
		ImageIO.write(big, "png", new File(dir, "popout-window.png"));
	}

	@Test
	public void scaledDisplaysShowTheSamePixelsMadeBigger() throws Exception
	{
		// Windows display scaling hands the window a scaled Graphics (the user's screens are at 125% and 150%).
		// Every edge row and column must still be the bar's own edge: no gaps or stray lines around it.
		double[] scales = {1.25, 1.5, 1.75, 2.0};
		BufferedImage[] images = new BufferedImage[scales.length + 1];
		SwingUtilities.invokeAndWait(() ->
		{
			Component panel = window.getContentPane();
			images[0] = paintAt(panel, 1.0);
			for (int i = 0; i < scales.length; i++)
			{
				images[i + 1] = paintAt(panel, scales[i]);
			}
		});
		BufferedImage plain = images[0];
		int w = plain.getWidth();
		int h = plain.getHeight();
		for (int i = 0; i < scales.length; i++)
		{
			BufferedImage scaled = images[i + 1];
			int sw = (int) Math.round(w * scales[i]);
			int sh = (int) Math.round(h * scales[i]);
			String at = " at " + scales[i];
			assertTrue("left edge" + at, colors(plain, 0, 0, 1, h).containsAll(colors(scaled, 0, 0, 1, sh)));
			assertTrue("right edge" + at, colors(plain, w - 1, 0, 1, h).containsAll(colors(scaled, sw - 1, 0, 1, sh)));
			assertTrue("top edge" + at, colors(plain, 0, 0, w, 1).containsAll(colors(scaled, 0, 0, sw, 1)));
			assertTrue("bottom edge" + at, colors(plain, 0, h - 1, w, 1).containsAll(colors(scaled, 0, sh - 1, sw, 1)));
			// Just inside the edge is the frame's light and dark bevel, never the panel's own color. (The screen
			// pixel holding the middle of 1x column 1 shows that column.)
			int col = (int) Math.floor(1.5 * scales[i]);
			int bottomRow = (int) Math.floor((h - 1.5) * scales[i]);
			assertTrue("bevel inside the left edge" + at,
				colors(plain, 1, 1, 1, h - 2).containsAll(colors(scaled, col, col, 1, bottomRow - col + 1)));
		}
	}

	@Test
	public void belongsToTheGameWindowAndNeverTakesFocus() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JFrame game = new JFrame();
			TickTrackerWindow owned = ownedBy(game);
			try
			{
				assertSame(game, owned.getOwner());
				assertFalse(owned.isFocusableWindow());
			}
			finally
			{
				owned.closeWindow();
				game.dispose();
			}
		});
	}

	@Test
	public void staysAboveRuneLiteWhenRuneLiteIsAlwaysOnTop() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			JFrame game = new JFrame();
			TickTrackerWindow owned = ownedBy(game);
			try
			{
				// Making the bar "not on top" while RuneLite is on top would take "on top" away from RuneLite.
				game.setAlwaysOnTop(true);
				owned.applySettings(false, false, 100);
				assertTrue(owned.isAlwaysOnTop());

				// When RuneLite isn't on top, the plugin's setting decides.
				game.setAlwaysOnTop(false);
				owned.applySettings(false, false, 100);
				assertFalse(owned.isAlwaysOnTop());
				owned.applySettings(true, false, 100);
				assertTrue(owned.isAlwaysOnTop());
			}
			finally
			{
				owned.closeWindow();
				game.dispose();
			}
		});
	}

	@Test
	public void keepsItsOwnOnTopSettingWhenRuneLiteReappliesItsOwn() throws Exception
	{
		JFrame[] game = new JFrame[1];
		TickTrackerWindow[] owned = new TickTrackerWindow[1];
		SwingUtilities.invokeAndWait(() ->
		{
			game[0] = new JFrame();
			owned[0] = ownedBy(game[0]);
			owned[0].applySettings(true, false, 100);
			// RuneLite sets its own "Always on top" (off) on its window after every RuneLite setting change,
			// and Java copies it to the windows it owns.
			game[0].setAlwaysOnTop(true);
			game[0].setAlwaysOnTop(false);
			assertFalse(owned[0].isAlwaysOnTop());
		});
		SwingUtilities.invokeAndWait(() -> { }); // let the bar put its own setting back
		try
		{
			assertTrue(owned[0].isAlwaysOnTop());
		}
		finally
		{
			SwingUtilities.invokeAndWait(() ->
			{
				owned[0].closeWindow();
				game[0].dispose();
			});
		}
	}

	@Test
	public void opensOnlyOnceRuneLiteIsOnScreenAndNotMinimized() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			assertFalse("no RuneLite window yet", TickTrackerBarPlugin.canHoldPopOut(null));
			JFrame hidden = new JFrame();
			// Stands in for RuneLite's window once it's shown; the test never puts a window on screen.
			JFrame shown = new JFrame()
			{
				@Override
				public boolean isShowing()
				{
					return true;
				}
			};
			try
			{
				assertFalse("not shown yet", TickTrackerBarPlugin.canHoldPopOut(hidden));
				assertTrue(TickTrackerBarPlugin.canHoldPopOut(shown));
				shown.setExtendedState(Frame.ICONIFIED);
				assertFalse("minimized", TickTrackerBarPlugin.canHoldPopOut(shown));
				shown.setExtendedState(Frame.ICONIFIED | Frame.MAXIMIZED_BOTH);
				assertFalse("minimized from full screen", TickTrackerBarPlugin.canHoldPopOut(shown));
				shown.setExtendedState(Frame.MAXIMIZED_BOTH);
				assertTrue("full screen", TickTrackerBarPlugin.canHoldPopOut(shown));
			}
			finally
			{
				hidden.dispose();
				shown.dispose();
			}
		});
	}

	/** Paints the panel the way Swing does on a display with this scaling. */
	private static BufferedImage paintAt(Component panel, double scale)
	{
		BufferedImage image = new BufferedImage((int) Math.round(panel.getWidth() * scale),
			(int) Math.round(panel.getHeight() * scale), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.scale(scale, scale);
		panel.paint(g);
		g.dispose();
		return image;
	}

	/** The colors in a rectangle of the image. */
	private static Set<Integer> colors(BufferedImage image, int x, int y, int w, int h)
	{
		Set<Integer> colors = new HashSet<>();
		for (int row = y; row < y + h; row++)
		{
			for (int col = x; col < x + w; col++)
			{
				colors.add(image.getRGB(col, row));
			}
		}
		return colors;
	}

	private static int brightness(int argb)
	{
		Color c = new Color(argb);
		return (c.getRed() + c.getGreen() + c.getBlue()) / 3;
	}

	private static TickTrackerWindow ownedBy(JFrame game)
	{
		return new TickTrackerWindow(game, new TickTracker(), BarStyle::defaults, () -> { }, () -> { }, p -> { });
	}

	private void click(Component panel, Point p)
	{
		press(panel, p.x, p.y, window.getX() + p.x, window.getY() + p.y);
		panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0, p.x, p.y,
			window.getX() + p.x, window.getY() + p.y, 1, false, MouseEvent.BUTTON1));
	}

	private static void press(Component panel, int x, int y, int screenX, int screenY)
	{
		panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_PRESSED, 0, InputEvent.BUTTON1_DOWN_MASK,
			x, y, screenX, screenY, 1, false, MouseEvent.BUTTON1));
	}

	private static void drag(Component panel, int screenX, int screenY)
	{
		panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, 0, InputEvent.BUTTON1_DOWN_MASK,
			0, 0, screenX, screenY, 0, false, MouseEvent.NOBUTTON));
	}

	private static void release(Component panel, int screenX, int screenY)
	{
		panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_RELEASED, 0, 0,
			0, 0, screenX, screenY, 1, false, MouseEvent.BUTTON1));
	}

	private static Point center(Rectangle r)
	{
		return new Point(r.x + r.width / 2, r.y + r.height / 2);
	}
}
