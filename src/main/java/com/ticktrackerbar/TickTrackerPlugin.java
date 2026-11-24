/*
 * Copyright (c) 2026, Tick Tracker Bar contributors
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Tick Tracker Bar",
	description = "A tick bar that resets every game tick (or every 2 or 3 ticks), marks your clicks and tracks your on-tick streak",
	tags = {"tick", "timing", "1t", "2t", "3t", "manipulation", "prayer", "flick", "trainer", "streak"}
)
public class TickTrackerPlugin extends Plugin
{
	static final String WINDOW_LOCATION_KEY = "windowLocation";

	/** At least this much of the pop-out window must be on a screen, otherwise it's moved back into view. */
	private static final int MIN_VISIBLE_PX = 30;

	/** How far below the top of the game the pop-out window first appears. */
	private static final int FIRST_PLACE_FROM_TOP = 40;

	/** How often to check whether RuneLite's window is up yet, at client start. */
	private static final int WAIT_FOR_GAME_WINDOW_MS = 250;

	@Inject
	private Client client;

	@Inject
	private TickTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TickTrackerOverlay overlay;

	@Inject
	private KeyManager keyManager;

	@Inject
	private MouseManager mouseManager;

	@Getter
	private final TickTracker tracker = new TickTracker();

	@Getter
	private volatile BarStyle style = BarStyle.defaults();

	private volatile boolean running;

	/** The pop-out window, or null when it's closed. Swing thread only. */
	private TickTrackerWindow window;

	/** Runs while the pop-out is waiting for RuneLite's window to appear. Swing thread only. */
	private Timer gameWindowWait;

	private final HotkeyListener resetHotkey = new HotkeyListener(() -> config.resetHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			resetStats();
		}
	};

	private final MouseAdapter mouseListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			if (config.inputType() == TickTrackerConfig.InputType.MOUSE_CLICKS)
			{
				tracker.onInput(System.nanoTime());
			}
			return event;
		}
	};

	@Provides
	TickTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TickTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		running = true;
		applyConfig();
		// The bar starts on the next game tick.
		tracker.pause();
		overlayManager.add(overlay);
		keyManager.registerKeyListener(resetHotkey);
		mouseManager.registerMouseListener(mouseListener);
		SwingUtilities.invokeLater(this::updateWindow);
	}

	@Override
	protected void shutDown()
	{
		running = false;
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(resetHotkey);
		mouseManager.unregisterMouseListener(mouseListener);
		SwingUtilities.invokeLater(this::updateWindow);
	}

	/** The game's own tick: everything is synced to this, not to a free-running timer. */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		tracker.onGameTick(System.nanoTime());
	}

	// Low priority so clicks that other plugins cancel are already marked as consumed.
	@Subscribe(priority = -100)
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!event.isConsumed() && counts(config.inputType(), event.getMenuAction(), event.getMenuOption()))
		{
			tracker.onInput(System.nanoTime());
		}
	}

	/**
	 * Whether a clicked menu option counts as an action for the given setting.
	 *
	 * <p>Game actions leave out "Cancel", RuneLite's own menu entries (like this overlay's), picking an
	 * item or spell to use (nothing happens in the game until you pick what to use it on) and dropping.
	 * In 2t / 3t methods the drops happen on the other ticks, so counting them would put marks on the
	 * wrong ticks every cycle and could line the cycle up with a drop instead of the manipulation.
	 */
	static boolean counts(TickTrackerConfig.InputType input, MenuAction action, String option)
	{
		if (action == null)
		{
			return false;
		}
		switch (input)
		{
			case ITEM_USE:
				return isItemUse(action);
			case GAME_ACTIONS:
				return action != MenuAction.CANCEL
					&& action != MenuAction.WIDGET_TARGET
					&& !action.name().startsWith("RUNELITE")
					&& !isDrop(option);
			default:
				// Any mouse click is counted by the mouse listener instead.
				return false;
		}
	}

	/**
	 * The input lag to score with. Input lag values (like the World Lag plugin's) are measured on menu clicks,
	 * which the game handles on its 20 ms client cycle. With Count: Any mouse click, a press is seen as it
	 * happens, up to a cycle earlier, so it needs about this much more to line up.
	 */
	static final int MOUSE_PRESS_EXTRA_LAG_MS = 15;

	static int inputLagFor(TickTrackerConfig.InputType input, int inputLagMs)
	{
		return inputLagMs > 0 && input == TickTrackerConfig.InputType.MOUSE_CLICKS
			? inputLagMs + MOUSE_PRESS_EXTRA_LAG_MS
			: inputLagMs;
	}

	/** "Use X -> Y": an item (or spell) used on an item, object, NPC, player or ground item. */
	private static boolean isItemUse(MenuAction action)
	{
		return action.name().startsWith("WIDGET_TARGET_ON_");
	}

	private static boolean isDrop(String option)
	{
		return option != null && "Drop".equalsIgnoreCase(Text.removeTags(option).trim());
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state != GameState.LOGGED_IN && state != GameState.LOADING)
		{
			tracker.pause();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!TickTrackerConfig.GROUP.equals(event.getGroup()) || WINDOW_LOCATION_KEY.equals(event.getKey()))
		{
			return;
		}
		applyConfig();
		SwingUtilities.invokeLater(this::updateWindow);
	}

	void resetStats()
	{
		tracker.resetStats();
	}

	void setDisplayMode(TickTrackerConfig.DisplayMode mode)
	{
		configManager.setConfiguration(TickTrackerConfig.GROUP, "displayMode", mode);
	}

	private void applyConfig()
	{
		style = BarStyle.from(config);
		tracker.configure(
			config.tickMode().getTicks(),
			config.hitWindow() == TickTrackerConfig.HitWindow.LAST_TICK,
			config.syncOnFirstClick(),
			inputLagFor(config.inputType(), config.inputLag()));
	}

	/** Opens, updates or closes the pop-out window to match the settings. Swing thread only. */
	private void updateWindow()
	{
		boolean wanted = running && config.displayMode().isShowWindow();
		if (!wanted)
		{
			stopWaitingForGameWindow();
			if (window != null)
			{
				window.closeWindow();
				window = null;
			}
			return;
		}

		if (window == null)
		{
			// At client start plugins start before RuneLite's window is shown (or even placed), so wait
			// for it: the pop-out belongs to it and is first placed over it.
			Window game = gameWindow();
			if (!canHoldPopOut(game))
			{
				waitForGameWindow();
				return;
			}
			stopWaitingForGameWindow();

			window = new TickTrackerWindow(
				game,
				tracker,
				this::getStyle,
				this::resetStats,
				() -> setDisplayMode(TickTrackerConfig.DisplayMode.OVERLAY),
				this::saveWindowLocation);
			window.applySettings(config.alwaysOnTop(), config.lockWindow(), config.windowOpacity());
			placeWindow(window, game);
			window.showWindow();
			return;
		}
		window.applySettings(config.alwaysOnTop(), config.lockWindow(), config.windowOpacity());
	}

	private Window gameWindow()
	{
		Canvas canvas = client.getCanvas();
		return canvas != null ? SwingUtilities.getWindowAncestor(canvas) : null;
	}

	/**
	 * Whether RuneLite's window is on screen, so the pop-out can open over it. A minimized window still
	 * counts as showing, but a pop-out made then would open in the middle of the main screen and stay up
	 * over other apps (Windows only hides a window's pop-outs at the moment the window is minimized).
	 */
	static boolean canHoldPopOut(Window game)
	{
		if (game == null || !game.isShowing())
		{
			return false;
		}
		return !(game instanceof Frame) || (((Frame) game).getExtendedState() & Frame.ICONIFIED) == 0;
	}

	private void waitForGameWindow()
	{
		if (gameWindowWait == null)
		{
			gameWindowWait = new Timer(WAIT_FOR_GAME_WINDOW_MS, e -> updateWindow());
			gameWindowWait.setRepeats(true);
		}
		gameWindowWait.start();
	}

	private void stopWaitingForGameWindow()
	{
		if (gameWindowWait != null)
		{
			gameWindowWait.stop();
		}
	}

	/**
	 * Puts the window back where it was last dragged to. If it was never dragged, or that spot is
	 * off-screen (say a monitor was unplugged), it goes near the top of the game instead.
	 */
	private void placeWindow(TickTrackerWindow w, Window game)
	{
		Point saved = loadWindowLocation();
		if (saved != null && isOnAScreen(new Rectangle(saved, w.getSize())))
		{
			w.setLocation(saved);
			return;
		}

		Rectangle area = game.getBounds();
		Canvas canvas = client.getCanvas();
		try
		{
			if (canvas != null && canvas.isShowing())
			{
				area = new Rectangle(canvas.getLocationOnScreen(), canvas.getSize());
			}
		}
		catch (IllegalComponentStateException e)
		{
			// The canvas went away in the meantime; use the window.
		}

		Point spot = new Point(area.x + (area.width - w.getWidth()) / 2, area.y + FIRST_PLACE_FROM_TOP);
		if (isOnAScreen(new Rectangle(spot, w.getSize())))
		{
			w.setLocation(spot);
		}
		else
		{
			w.setLocationRelativeTo(null);
		}
	}

	private static boolean isOnAScreen(Rectangle bounds)
	{
		for (GraphicsDevice screen : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices())
		{
			Rectangle visible = screen.getDefaultConfiguration().getBounds().intersection(bounds);
			if (visible.width >= MIN_VISIBLE_PX && visible.height >= MIN_VISIBLE_PX)
			{
				return true;
			}
		}
		return false;
	}

	private Point loadWindowLocation()
	{
		String value = configManager.getConfiguration(TickTrackerConfig.GROUP, WINDOW_LOCATION_KEY);
		if (value == null)
		{
			return null;
		}
		String[] parts = value.split(",");
		if (parts.length != 2)
		{
			return null;
		}
		try
		{
			return new Point(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
		}
		catch (NumberFormatException e)
		{
			log.debug("Ignoring bad saved window location {}", value);
			return null;
		}
	}

	private void saveWindowLocation(Point location)
	{
		configManager.setConfiguration(TickTrackerConfig.GROUP, WINDOW_LOCATION_KEY, location.x + "," + location.y);
	}
}
