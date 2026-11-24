/*
 * Copyright (c) 2026, ETPhone12
 * All rights reserved. Licensed under the BSD 2-Clause License, see LICENSE.
 */
package com.ticktrackerbar;

import com.ticktrackerbar.TickTrackerConfig.InputType;
import net.runelite.api.MenuAction;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Which clicked menu options count as an action, for each "Count" setting. */
public class TickTrackerBarPluginInputTest
{
	@Test
	public void gameActionsCountWhatYouDoInTheGame()
	{
		assertTrue(TickTrackerBarPlugin.counts(InputType.GAME_ACTIONS, MenuAction.GAME_OBJECT_FIRST_OPTION, "Chop down"));
		assertTrue(TickTrackerBarPlugin.counts(InputType.GAME_ACTIONS, MenuAction.NPC_FIRST_OPTION, "Lure"));
		assertTrue(TickTrackerBarPlugin.counts(InputType.GAME_ACTIONS, MenuAction.WALK, "Walk here"));
		assertTrue("prayer flicking", TickTrackerBarPlugin.counts(InputType.GAME_ACTIONS, MenuAction.CC_OP, "Activate"));
		assertTrue("herb on tar", TickTrackerBarPlugin.counts(InputType.GAME_ACTIONS, MenuAction.WIDGET_TARGET_ON_WIDGET, "Use"));
	}

	@Test
	public void gameActionsLeaveOutDropsPickingAnItemAndRuneLiteMenus()
	{
		assertFalse(TickTrackerBarPlugin.counts(InputType.GAME_ACTIONS, MenuAction.CC_OP, "Drop"));
		assertFalse("menu text can carry color tags",
			TickTrackerBarPlugin.counts(InputType.GAME_ACTIONS, MenuAction.CC_OP, "<col=ffffff>Drop</col>"));
		assertFalse("picking the item to use", TickTrackerBarPlugin.counts(InputType.GAME_ACTIONS, MenuAction.WIDGET_TARGET, "Use"));
		assertFalse(TickTrackerBarPlugin.counts(InputType.GAME_ACTIONS, MenuAction.CANCEL, "Cancel"));
		assertFalse(TickTrackerBarPlugin.counts(InputType.GAME_ACTIONS, MenuAction.RUNELITE_OVERLAY, "Reset stats"));
		assertFalse(TickTrackerBarPlugin.counts(InputType.GAME_ACTIONS, null, "Walk here"));
	}

	@Test
	public void itemUseOnlyCountsUsingAnItemOnSomething()
	{
		assertTrue(TickTrackerBarPlugin.counts(InputType.ITEM_USE, MenuAction.WIDGET_TARGET_ON_WIDGET, "Use"));
		assertTrue(TickTrackerBarPlugin.counts(InputType.ITEM_USE, MenuAction.WIDGET_TARGET_ON_GAME_OBJECT, "Use"));
		assertTrue(TickTrackerBarPlugin.counts(InputType.ITEM_USE, MenuAction.WIDGET_TARGET_ON_NPC, "Use"));
		assertFalse(TickTrackerBarPlugin.counts(InputType.ITEM_USE, MenuAction.WIDGET_TARGET, "Use"));
		assertFalse(TickTrackerBarPlugin.counts(InputType.ITEM_USE, MenuAction.WALK, "Walk here"));
		assertFalse(TickTrackerBarPlugin.counts(InputType.ITEM_USE, MenuAction.NPC_FIRST_OPTION, "Lure"));
		assertFalse(TickTrackerBarPlugin.counts(InputType.ITEM_USE, MenuAction.CC_OP, "Drop"));
	}

	@Test
	public void anyMouseClickIsCountedByTheMouseListenerInstead()
	{
		assertFalse(TickTrackerBarPlugin.counts(InputType.MOUSE_CLICKS, MenuAction.WALK, "Walk here"));
	}

	@Test
	public void mousePressesGetALittleMoreInputLag()
	{
		// Presses are seen before the game reads them, up to one 20 ms client cycle earlier than menu clicks.
		assertEquals(150 + TickTrackerBarPlugin.MOUSE_PRESS_EXTRA_LAG_MS, TickTrackerBarPlugin.inputLagFor(InputType.MOUSE_CLICKS, 150));
		assertEquals(150, TickTrackerBarPlugin.inputLagFor(InputType.GAME_ACTIONS, 150));
		assertEquals(150, TickTrackerBarPlugin.inputLagFor(InputType.ITEM_USE, 150));
		assertEquals("0 still means off", 0, TickTrackerBarPlugin.inputLagFor(InputType.MOUSE_CLICKS, 0));
	}
}
