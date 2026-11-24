/*
 * Copyright (c) 2026, ETPhone12
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Try the pop-out tick bar without the game: a fake game tick every 600 ms, and a practice window
 * where every mouse click or key press counts as an action. Not part of the plugin.
 *
 * <p>Run it from your IDE. Optional arguments: 1, 2 or 3 ticks per cycle, then an input lag in milliseconds.
 */
public class TickTrackerDemo
{
	public static void main(String[] args)
	{
		int ticks = args.length > 0 ? Integer.parseInt(args[0]) : 1;
		int inputLagMs = args.length > 1 ? Integer.parseInt(args[1]) : 0;
		TickTracker tracker = new TickTracker();
		tracker.configure(ticks, true, true, inputLagMs);
		BarStyle style = BarStyle.defaults();

		ScheduledExecutorService fakeServer = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread thread = new Thread(r, "fake-game-tick");
			thread.setDaemon(true);
			return thread;
		});
		fakeServer.scheduleAtFixedRate(() -> tracker.onGameTick(System.nanoTime()), 0,
			TickTracker.TICK_NANOS, TimeUnit.NANOSECONDS);

		SwingUtilities.invokeLater(() ->
		{
			JFrame practice = new JFrame("Tick Tracker Bar demo (" + ticks + "-tick)");
			JLabel hint = new JLabel("<html><center>Click in here or press any key to act.<br><br>"
				+ "Drag the tick bar anywhere, even onto another monitor.</center></html>", SwingConstants.CENTER);
			hint.setPreferredSize(new Dimension(420, 200));
			hint.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					tracker.onInput(System.nanoTime());
				}
			});
			practice.addKeyListener(new KeyAdapter()
			{
				@Override
				public void keyPressed(KeyEvent e)
				{
					tracker.onInput(System.nanoTime());
				}
			});
			practice.add(hint);
			practice.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
			practice.pack();
			practice.setLocationRelativeTo(null);
			practice.setVisible(true);

			// Owned by the practice window, like the plugin's bar is owned by RuneLite's window.
			TickTrackerWindow window = new TickTrackerWindow(practice, tracker, () -> style, tracker::resetStats,
				() -> System.exit(0), location -> { });
			window.applySettings(true, false, 100);
			window.setLocation(practice.getX(), Math.max(0, practice.getY() - window.getHeight() - 10));
			window.showWindow();
		});
	}
}
