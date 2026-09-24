/*
 * Copyright (c) 2026, Oveduumnakal
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oveduumnakal.aerialfishing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

/**
 * Highlights aerial fishing spots at Molch Island and numbers them by click
 * priority so the fastest reachable spot is always obvious.
 *
 * <p>The plugin tracks every {@code FISHING_SPOT_AERIAL} NPC, notes when each one
 * relocates (to age its expiry), and each tick re-runs {@link SpotRanker} to order
 * the spots. It is advisory only: it draws on screen and never sends input.
 */
@Slf4j
@PluginDescriptor(
	name = "Aerial Fishing Helper",
	description = "Ranks aerial fishing spots by click priority so you always click the fastest one next",
	tags = {"fishing", "aerial", "molch", "cormorant", "skilling", "tench"}
)
public class AerialFishingPlugin extends Plugin
{
	/** NPC id of a frenzied aerial fishing spot (a distinct id from the normal spot). */
	private static final int FISHING_SPOT_AERIAL_FRENZY = 16346;

	/** NPC id of the cormorant that flies from its perch out to a spot and back. */
	private static final int FISHING_CORMORANT = NpcID.FISHING_CORMORANT_ON_PERCH;

	/** Tiles within which a cormorant counts as "at" the active spot. */
	private static final int CORMORANT_AT_SPOT_TILES = 1;

	/** Config group of RuneLite's built-in Fishing plugin. */
	private static final String FISHING_GROUP = "fishing";

	/** Built-in Fishing plugin keys whose spot highlights this plugin can suppress. */
	private static final String[] FISHING_HIGHLIGHT_KEYS = {"showTiles", "showIcons", "showNames"};

	@Inject
	private Client client;

	@Inject
	@Getter
	private AerialFishingConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private AerialFishingOverlay overlay;

	@Inject
	private ConfigManager configManager;

	private final Map<Integer, AerialFishSpot> spots = new HashMap<>();

	private final Map<String, String> savedFishingConfig = new HashMap<>();

	private boolean builtinSuppressed;

	private int tickCounter;

	@Getter
	private List<AerialFishSpot> rankedSpots = Collections.emptyList();

	/** NPC index of the spot the player is currently fishing, or {@code -1} for none. */
	private int activeSpotIndex = -1;

	/** Whether the cormorant has been seen at the active spot this fishing trip. */
	private boolean birdArrived;

	/** The tick the active spot was clicked, used as a safety timeout. */
	private int activeStartTick;

	/**
	 * Registers the overlay when the plugin starts.
	 */
	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		applyBuiltinSuppression();
	}

	/**
	 * Removes the overlay, restores the built-in highlights, and clears tracked
	 * state when the plugin stops.
	 */
	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		restoreBuiltinHighlights();
		reset();
	}

	/**
	 * Provides the plugin configuration.
	 *
	 * @param configManager the config manager
	 * @return the bound configuration
	 */
	@Provides
	AerialFishingConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(AerialFishingConfig.class);
	}

	/**
	 * Applies this plugin's built-in-highlight toggle the moment it changes.
	 *
	 * @param event the config-changed event
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!AerialFishingConfig.GROUP.equals(event.getGroup()))
			return;

		if ("hideBuiltinHighlights".equals(event.getKey()))
			applyBuiltinSuppression();
	}

	/**
	 * Clears tracked spots on logout or world hop so stale spots never linger.
	 *
	 * @param event the game-state event
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
			reset();
	}

	/**
	 * Rescans the scene's NPCs, updates tracked spots, and re-ranks every tick. A
	 * per-tick scan (rather than spawn events) means spots are found even when the
	 * plugin is enabled after they already exist, or when a spot relocates in place.
	 *
	 * @param event the game-tick event
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		tickCounter++;

		Player local = client.getLocalPlayer();
		if (local == null)
		{
			reset();
			return;
		}

		syncSpots();
		if (spots.isEmpty())
		{
			rankedSpots = Collections.emptyList();
			clearActiveBird();
			return;
		}

		RankingParams params = new RankingParams(
			config.minLifeTicks(),
			config.maxLifeTicks(),
			config.maxReachDistance());

		rankedSpots = SpotRanker.rank(local.getWorldLocation(), tickCounter,
			new ArrayList<>(spots.values()), params);

		updateActiveBird();
	}

	/**
	 * Marks the spot the player clicks so the overlay can animate a bird on it. Any
	 * left-click interaction on an aerial spot (other than Examine) starts the trip.
	 *
	 * @param event the menu-click event
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuEntry entry = event.getMenuEntry();
		NPC npc = entry.getNpc();
		if (npc == null || !isAerialSpot(npc.getId()))
			return;

		if ("Examine".equalsIgnoreCase(event.getMenuOption()))
			return;

		activeSpotIndex = npc.getIndex();
		birdArrived = false;
		activeStartTick = tickCounter;
	}

	/**
	 * Advances the active-bird state: the bird shows from the click until the
	 * cormorant, having reached the spot, heads back. Also clears the state if the
	 * spot despawns or the trip runs past a safety timeout.
	 */
	private void updateActiveBird()
	{
		if (activeSpotIndex < 0)
			return;

		AerialFishSpot spot = spots.get(activeSpotIndex);
		if (spot == null || spot.getLocation() == null)
		{
			clearActiveBird();
			return;
		}

		if (tickCounter - activeStartTick > config.maxLifeTicks())
		{
			clearActiveBird();
			return;
		}

		if (cormorantAt(spot.getLocation()))
			birdArrived = true;
		else if (birdArrived)
			clearActiveBird();
	}

	/**
	 * Whether a cormorant NPC currently sits on or beside the given tile.
	 *
	 * @param location the spot tile to test
	 * @return {@code true} if a cormorant is at that tile
	 */
	private boolean cormorantAt(WorldPoint location)
	{
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (npc.getId() != FISHING_CORMORANT)
				continue;

			WorldPoint birdLocation = npc.getWorldLocation();
			if (birdLocation != null
				&& birdLocation.getPlane() == location.getPlane()
				&& birdLocation.distanceTo(location) <= CORMORANT_AT_SPOT_TILES)
				return true;
		}

		return false;
	}

	/**
	 * The spot the overlay should animate a bird on, or {@code null} if none.
	 *
	 * @return the active bird spot, or {@code null}
	 */
	public AerialFishSpot getActiveBirdSpot()
	{
		if (activeSpotIndex < 0)
			return null;

		return spots.get(activeSpotIndex);
	}

	/**
	 * Clears the active-bird state so no bird is drawn.
	 */
	private void clearActiveBird()
	{
		activeSpotIndex = -1;
		birdArrived = false;
	}

	/**
	 * Reconciles the tracked spots with the NPCs currently in the scene: adds new
	 * aerial spots, resets a spot's age when it relocates, refreshes its frenzied
	 * flag, and drops spots no longer present.
	 */
	private void syncSpots()
	{
		boolean detectFrenzy = config.detectFrenzy();
		long now = System.currentTimeMillis();
		Set<Integer> present = new HashSet<>();

		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			if (!isAerialSpot(npc.getId()))
				continue;

			int index = npc.getIndex();
			present.add(index);
			WorldPoint location = npc.getWorldLocation();
			AerialFishSpot spot = spots.get(index);
			if (spot == null)
			{
				spot = new AerialFishSpot(npc, location, tickCounter);
				spots.put(index, spot);
			}
			else if (location != null && !location.equals(spot.getLocation()))
			{
				spot.setLocation(location);
				spot.setLastMoveTick(tickCounter);
				spot.setLastMoveTimeMillis(now);
			}

			spot.setFrenzied(detectFrenzy && npc.getId() == FISHING_SPOT_AERIAL_FRENZY);
		}

		spots.keySet().removeIf(index -> !present.contains(index));
	}

	/**
	 * Whether an NPC id is an aerial fishing spot (normal or frenzied).
	 *
	 * @param npcId the NPC id to test
	 * @return {@code true} if it is an aerial spot
	 */
	private static boolean isAerialSpot(int npcId)
	{
		return npcId == NpcID.FISHING_SPOT_AERIAL || npcId == FISHING_SPOT_AERIAL_FRENZY;
	}

	/**
	 * Applies or lifts built-in-highlight suppression to match the config toggle.
	 */
	private void applyBuiltinSuppression()
	{
		if (config.hideBuiltinHighlights())
			suppressBuiltinHighlights();
		else
			restoreBuiltinHighlights();
	}

	/**
	 * Switches off the built-in Fishing plugin's spot highlights, remembering the
	 * previous values so they can be restored, so only this plugin's ranked spots
	 * are marked.
	 */
	private void suppressBuiltinHighlights()
	{
		if (builtinSuppressed)
			return;

		for (String key : FISHING_HIGHLIGHT_KEYS)
		{
			savedFishingConfig.put(key, configManager.getConfiguration(FISHING_GROUP, key));
			configManager.setConfiguration(FISHING_GROUP, key, false);
		}

		builtinSuppressed = true;
	}

	/**
	 * Restores the built-in Fishing plugin's spot-highlight settings to what they
	 * were before suppression.
	 */
	private void restoreBuiltinHighlights()
	{
		if (!builtinSuppressed)
			return;

		for (String key : FISHING_HIGHLIGHT_KEYS)
		{
			String previous = savedFishingConfig.get(key);
			if (previous == null)
				configManager.unsetConfiguration(FISHING_GROUP, key);
			else
				configManager.setConfiguration(FISHING_GROUP, key, previous);
		}

		savedFishingConfig.clear();
		builtinSuppressed = false;
	}

	/**
	 * Drops all tracked and ranked spots.
	 */
	private void reset()
	{
		spots.clear();
		rankedSpots = Collections.emptyList();
		clearActiveBird();
	}
}
