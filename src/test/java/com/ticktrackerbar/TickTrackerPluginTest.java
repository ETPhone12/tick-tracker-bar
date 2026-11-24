/*
 * Copyright (c) 2026, Tick Tracker Bar contributors
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Starts RuneLite with this plugin loaded. Run it with {@code gradlew run} or from your IDE. */
public class TickTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TickTrackerPlugin.class);
		RuneLite.main(args);
	}
}
