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
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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
	@Inject
	private Client client;

	@Inject
	@Getter
	private AerialFishingConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private AerialFishingOverlay overlay;

	private final Map<Integer, AerialFishSpot> spots = new HashMap<>();

	private int tickCounter;

	@Getter
	private List<AerialFishSpot> rankedSpots = Collections.emptyList();

	/**
	 * Registers the overlay when the plugin starts.
	 */
	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
	}

	/**
	 * Removes the overlay and clears tracked state when the plugin stops.
	 */
	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
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
	 * Starts tracking a newly-spawned aerial fishing spot.
	 *
	 * @param event the npc-spawned event
	 */
	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (npc.getId() == NpcID.FISHING_SPOT_AERIAL)
			spots.put(npc.getIndex(), new AerialFishSpot(npc, npc.getWorldLocation(), tickCounter));
	}

	/**
	 * Stops tracking a despawned aerial fishing spot.
	 *
	 * @param event the npc-despawned event
	 */
	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		spots.remove(event.getNpc().getIndex());
	}

	/**
	 * Ages the tracked spots, refreshes frenzy state, and re-ranks every tick.
	 *
	 * @param event the game-tick event
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		tickCounter++;
		if (spots.isEmpty())
		{
			rankedSpots = Collections.emptyList();
			return;
		}

		refreshSpots();

		Player local = client.getLocalPlayer();
		WorldPoint player = local == null ? null : local.getWorldLocation();
		RankingParams params = new RankingParams(
			config.minLifeTicks(),
			config.maxLifeTicks(),
			config.reachBufferTicks(),
			config.maxReachDistance());

		rankedSpots = SpotRanker.rank(player, tickCounter, new ArrayList<>(spots.values()), params);
	}

	/**
	 * Updates each tracked spot's tile (resetting its age when it relocates) and
	 * its frenzied flag from the current spot-anim.
	 */
	private void refreshSpots()
	{
		boolean detectFrenzy = config.detectFrenzy() && config.frenzySpotanimId() >= 0;
		int frenzyId = config.frenzySpotanimId();

		for (AerialFishSpot spot : spots.values())
		{
			NPC npc = spot.getNpc();
			WorldPoint location = npc.getWorldLocation();
			if (location != null && !location.equals(spot.getLocation()))
			{
				spot.setLocation(location);
				spot.setLastMoveTick(tickCounter);
			}

			spot.setFrenzied(detectFrenzy && npc.hasSpotAnim(frenzyId));
		}
	}

	/**
	 * Drops all tracked and ranked spots.
	 */
	private void reset()
	{
		spots.clear();
		rankedSpots = Collections.emptyList();
	}
}
