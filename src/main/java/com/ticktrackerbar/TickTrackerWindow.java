/*
 * Copyright (c) 2026, ETPhone12
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * The pop-out tick bar: a small borderless window you can drag anywhere on any monitor.
 * It never takes keyboard focus, so clicking or dragging it doesn't pull focus away from the game.
 * It belongs to the RuneLite window, so it always stays above RuneLite and hides while RuneLite is
 * minimized. Only use it from the Swing thread.
 */
class TickTrackerWindow extends JWindow
{
	private static final int FRAME_MS = 15;
	private static final int BUTTON_GAP = 3;
	private static final int BUTTON_PADDING = 10;
	/** Space between the HIT / MISS text and the Reset button. */
	private static final int RESULT_TO_BUTTONS = 6;

	private enum Button
	{
		NONE,
		RESET,
		CLOSE,
	}

	private final TickTracker tracker;
	private final Supplier<BarStyle> style;
	private final Runnable onReset;
	private final Runnable onClose;
	private final Consumer<Point> onMoved;
	private final TickBarPainter painter = new TickBarPainter();
	private final BarPanel panel = new BarPanel();
	private final Timer repaintTimer = new Timer(FRAME_MS, e -> repaintUnlessHidden());

	private boolean locked;
	private boolean wantOnTop;

	/**
	 * @param owner   the RuneLite window, or null for none
	 * @param onReset called when the Reset button is clicked
	 * @param onClose called when the X button is clicked
	 * @param onMoved called with the new screen position after the window is dragged
	 */
	TickTrackerWindow(Window owner, TickTracker tracker, Supplier<BarStyle> style, Runnable onReset, Runnable onClose,
		Consumer<Point> onMoved)
	{
		// With an owner, Windows hides the bar while RuneLite is minimized and keeps it above RuneLite.
		super(owner);
		this.tracker = tracker;
		this.style = style;
		this.onReset = onReset;
		this.onClose = onClose;
		this.onMoved = onMoved;

		setFocusableWindowState(false);
		setContentPane(panel);
		MouseHandler mouse = new MouseHandler();
		panel.addMouseListener(mouse);
		panel.addMouseMotionListener(mouse);
		repaintTimer.setCoalesce(true);
		// RuneLite sets its own "Always on top" on its window, and Java copies that to owned windows
		// like this one, so put our own setting back afterwards.
		addPropertyChangeListener("alwaysOnTop", e -> SwingUtilities.invokeLater(this::applyOnTop));
	}

	void showWindow()
	{
		setVisible(true);
		repaintTimer.start();
	}

	void closeWindow()
	{
		repaintTimer.stop();
		dispose();
	}

	void applySettings(boolean alwaysOnTop, boolean locked, int opacityPercent)
	{
		this.wantOnTop = alwaysOnTop;
		this.locked = locked;
		applyOnTop();
		setWindowOpacity(opacityPercent);
		panel.setHover(Button.NONE);
		fitToContent();
	}

	/** Resizes the window to fit the bar (bar size and stats are configurable). */
	void fitToContent()
	{
		if (!panel.getPreferredSize().equals(getSize()))
		{
			pack();
		}
	}

	/** Where the Reset button is, in window coordinates. */
	Rectangle resetButtonBounds()
	{
		return new Rectangle(panel.resetButton);
	}

	/** Where the X button is, in window coordinates. */
	Rectangle closeButtonBounds()
	{
		return new Rectangle(panel.closeButton);
	}

	/** How much of the header's right end the buttons (and the space before them) take up. */
	static int reservedHeaderWidth(FontMetrics fm, TickBarPainter.Layout l)
	{
		return l.lineHeight + BUTTON_GAP + resetButtonWidth(fm) + RESULT_TO_BUTTONS;
	}

	private static int resetButtonWidth(FontMetrics fm)
	{
		return fm.stringWidth("Reset") + BUTTON_PADDING;
	}

	/**
	 * Keeps the bar above other apps when the setting asks for it. It is only ever made less on top
	 * when RuneLite itself isn't on top: on Windows, taking "on top" away from an owned window also
	 * takes it away from its owner, which would switch off RuneLite's own "Always on top".
	 */
	private void applyOnTop()
	{
		Window owner = getOwner();
		boolean onTop = wantOnTop || (owner != null && owner.isAlwaysOnTop());
		if (isAlwaysOnTop() != onTop)
		{
			try
			{
				setAlwaysOnTop(onTop);
			}
			catch (SecurityException ignored)
			{
				// Not allowed on this system; the window still works, just not on top.
			}
		}
	}

	/** Windows hides the bar while RuneLite is minimized, but Java doesn't know that: skip the repaints. */
	private void repaintUnlessHidden()
	{
		Window owner = getOwner();
		if (owner instanceof Frame && (((Frame) owner).getExtendedState() & Frame.ICONIFIED) != 0)
		{
			return;
		}
		panel.repaint();
	}

	private void setWindowOpacity(int percent)
	{
		float opacity = Math.max(0.2f, Math.min(1f, percent / 100f));
		try
		{
			GraphicsDevice device = getGraphicsConfiguration().getDevice();
			if (opacity >= 1f || device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT))
			{
				setOpacity(opacity);
			}
		}
		catch (UnsupportedOperationException | IllegalComponentStateException | IllegalArgumentException ignored)
		{
			// See-through windows aren't supported on every system; stay opaque.
		}
	}

	private final class BarPanel extends JComponent
	{
		private Rectangle resetButton = new Rectangle();
		private Rectangle closeButton = new Rectangle();
		private Button hover = Button.NONE;
		/** The bar drawn at 1x, reused while the size stays the same. */
		private BufferedImage frame;

		BarPanel()
		{
			setOpaque(true);
		}

		@Override
		public Dimension getPreferredSize()
		{
			TickBarPainter.Layout l = relayout(metrics(), style.get());
			return new Dimension(l.width, l.height);
		}

		/** The font's measurements at 1x, the scale the bar is drawn at (see paintComponent). */
		private FontMetrics metrics()
		{
			Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
			try
			{
				return g.getFontMetrics(TickBarPainter.font());
			}
			finally
			{
				g.dispose();
			}
		}

		/** Works out the bar layout and where the buttons go, so they can be clicked before the first paint. */
		private TickBarPainter.Layout relayout(FontMetrics fm, BarStyle st)
		{
			TickBarPainter.Layout l = TickBarPainter.layout(fm, st);
			placeButtons(fm, l);
			return l;
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			BarStyle st = style.get();
			FontMetrics fm = metrics();
			TickBarPainter.Layout l = relayout(fm, st);
			if (frame == null || frame.getWidth() != l.width || frame.getHeight() != l.height)
			{
				frame = new BufferedImage(l.width, l.height, BufferedImage.TYPE_INT_ARGB);
			}

			Graphics2D ig = frame.createGraphics();
			try
			{
				// Keep the HIT / MISS text clear of the buttons.
				painter.paint(ig, tracker.snapshot(System.nanoTime()), st, reservedHeaderWidth(fm, l), true);
				ig.setFont(TickBarPainter.font());
				Color base = TickBarPainter.solid(st.getBackgroundColor());
				paintButton(ig, fm, resetButton, "Reset", hover == Button.RESET, base);
				paintButton(ig, fm, closeButton, null, hover == Button.CLOSE, base);
			}
			finally
			{
				ig.dispose();
			}

			// Drawn pixel for pixel at 1x, like the in-game overlay, then scaled up whole. On a display scaled
			// to 125-175%, drawing straight onto the screen puts neighbouring lines on uneven pixels and leaves
			// one-pixel gaps between them.
			Graphics2D g = (Graphics2D) graphics.create();
			try
			{
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				g.drawImage(frame, 0, 0, l.width, l.height, null);
			}
			finally
			{
				g.dispose();
			}

			if (getWidth() != l.width || getHeight() != l.height)
			{
				SwingUtilities.invokeLater(TickTrackerWindow.this::fitToContent);
			}
		}

		private void placeButtons(FontMetrics fm, TickBarPainter.Layout l)
		{
			int size = l.lineHeight;
			int closeX = l.width - TickBarPainter.PAD - size;
			closeButton = new Rectangle(closeX, l.headerY, size, size);
			int resetWidth = resetButtonWidth(fm);
			resetButton = new Rectangle(closeX - BUTTON_GAP - resetWidth, l.headerY, resetWidth, size);
		}

		/** A raised button like the game's own, its label in the game's orange (yellow under the mouse). */
		private void paintButton(Graphics2D g, FontMetrics fm, Rectangle r, String label, boolean hot, Color base)
		{
			Color face = TickBarPainter.blend(base, Color.WHITE, hot ? 0.22 : 0.1);
			g.setColor(face);
			g.fillRect(r.x, r.y, r.width, r.height);
			TickBarPainter.paintFrame(g, r.x, r.y, r.width, r.height, face);
			Color ink = hot ? TickBarPainter.GAME_YELLOW : TickBarPainter.GAME_ORANGE;
			if (label != null)
			{
				int tx = r.x + (r.width - fm.stringWidth(label)) / 2;
				int ty = r.y + (r.height - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent();
				g.setColor(Color.BLACK);
				g.drawString(label, tx + 1, ty + 1);
				g.setColor(ink);
				g.drawString(label, tx, ty);
			}
			else
			{
				int m = 4;
				for (int shadow = 1; shadow >= 0; shadow--)
				{
					g.setColor(shadow == 1 ? Color.BLACK : ink);
					g.drawLine(r.x + m + shadow, r.y + m + shadow, r.x + r.width - 1 - m + shadow, r.y + r.height - 1 - m + shadow);
					g.drawLine(r.x + r.width - 1 - m + shadow, r.y + m + shadow, r.x + m + shadow, r.y + r.height - 1 - m + shadow);
				}
			}
		}

		Button buttonAt(Point p)
		{
			if (resetButton.contains(p))
			{
				return Button.RESET;
			}
			if (closeButton.contains(p))
			{
				return Button.CLOSE;
			}
			return Button.NONE;
		}

		void setHover(Button button)
		{
			if (hover != button)
			{
				hover = button;
				repaint();
			}
			if (button != Button.NONE)
			{
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
			else
			{
				setCursor(locked ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			}
		}
	}

	/** Drag the window by any part that isn't a button; click the buttons. */
	private final class MouseHandler extends MouseAdapter
	{
		private Point pressedOnScreen;
		private Point windowAtPress;
		private Button pressed = Button.NONE;

		@Override
		public void mousePressed(MouseEvent e)
		{
			if (!SwingUtilities.isLeftMouseButton(e))
			{
				return;
			}
			pressed = panel.buttonAt(e.getPoint());
			if (pressed == Button.NONE && !locked)
			{
				pressedOnScreen = e.getLocationOnScreen();
				windowAtPress = getLocation();
			}
		}

		@Override
		public void mouseDragged(MouseEvent e)
		{
			if (pressedOnScreen == null)
			{
				return;
			}
			// Screen coordinates cover every monitor, so this drags across screens too.
			Point mouse = e.getLocationOnScreen();
			setLocation(windowAtPress.x + mouse.x - pressedOnScreen.x, windowAtPress.y + mouse.y - pressedOnScreen.y);
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			if (!SwingUtilities.isLeftMouseButton(e))
			{
				return;
			}
			if (pressedOnScreen != null)
			{
				pressedOnScreen = null;
				if (!getLocation().equals(windowAtPress))
				{
					onMoved.accept(getLocation());
				}
			}
			else if (pressed != Button.NONE && pressed == panel.buttonAt(e.getPoint()))
			{
				if (pressed == Button.RESET)
				{
					onReset.run();
				}
				else
				{
					onClose.run();
				}
			}
			pressed = Button.NONE;
		}

		@Override
		public void mouseMoved(MouseEvent e)
		{
			panel.setHover(panel.buttonAt(e.getPoint()));
		}

		@Override
		public void mouseExited(MouseEvent e)
		{
			panel.setHover(Button.NONE);
		}
	}
}
