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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import net.runelite.api.coords.WorldPoint;

/**
 * Ranks aerial fishing spots by click priority.
 *
 * <p>The core rule is <b>speed wins</b>: a spot that catches in fewer ticks is
 * always preferred, so a 1- or 2-tick spot outranks a frenzied pool. Frenzied
 * spots catch on a flat 3-tick cycle regardless of distance, so they are pinned
 * to the 3-tick tier and only win ties against a plain 3-tick spot.
 *
 * <p>Catch time comes from Chebyshev distance: 1-2 tiles catches in 1 tick,
 * 3-4 in 2, 5 in 3, 6-7 in 4, 8-9 in 5, and 10 or more caps at 6. These bands
 * match the Brain off Aerial Fishing and aerial-cue plugins, the latter measured
 * from the cormorant projectile's real flight distance. A spot is dropped from the ranking only when it sits beyond the
 * {@link RankingParams} max reach distance; how soon it may relocate feeds the
 * tie-break and the overlay's expiry color, but never removes it.
 *
 * <p>All methods are static and side-effect only the passed spots; the class holds
 * no state so it can be unit-tested on plain data with no live client.
 */
public final class SpotRanker
{
	/** Distance (inclusive) at which a catch still takes only 1 tick. */
	private static final int ONE_TICK_MAX_DISTANCE = 2;

	/** Distance (inclusive) at which a catch takes 2 ticks. */
	private static final int TWO_TICK_MAX_DISTANCE = 4;

	/** Distance (inclusive) at which a catch takes 3 ticks. */
	private static final int THREE_TICK_MAX_DISTANCE = 5;

	/** Distance (inclusive) at which a catch takes 4 ticks. */
	private static final int FOUR_TICK_MAX_DISTANCE = 7;

	/** Distance (inclusive) at which a catch takes 5 ticks. */
	private static final int FIVE_TICK_MAX_DISTANCE = 9;

	/** Catch ticks for any spot beyond {@link #FIVE_TICK_MAX_DISTANCE}; the flight time caps here. */
	private static final int MAX_CATCH_TICKS = 6;

	/** Catch tier a frenzied spot is pinned to. */
	private static final int FRENZY_TIER = 3;

	/**
	 * Priority order: lowest catch tier first, then frenzied ahead of plain within
	 * a tier, then the longer-lived spot, then the physically closer spot as a
	 * stable final tie-break.
	 */
	private static final Comparator<AerialFishSpot> PRIORITY =
		Comparator.comparingInt(AerialFishSpot::getEffectiveCatchTicks)
			.thenComparing((a, b) -> Boolean.compare(b.isFrenzied(), a.isFrenzied()))
			.thenComparing((a, b) -> Integer.compare(b.getRemainingTicks(), a.getRemainingTicks()))
			.thenComparingInt(AerialFishSpot::getChebyshevDistance);

	/**
	 * Prevents instantiation of this static-only helper.
	 */
	private SpotRanker()
	{
	}

	/**
	 * Recomputes every spot's derived fields, drops spots beyond the max reach
	 * distance, and returns the remainder sorted best-first with {@code rank} set
	 * (1 = click next). The input collection is not modified; the returned list is
	 * a new list referencing the same spot objects.
	 *
	 * @param player the player's world tile, or {@code null} if unknown
	 * @param currentTick the current game tick counter
	 * @param spots the tracked spots to rank
	 * @param params the tuning inputs
	 * @return the reachable spots, sorted by priority, each with its rank assigned
	 */
	public static List<AerialFishSpot> rank(WorldPoint player, int currentTick,
		Collection<AerialFishSpot> spots, RankingParams params)
	{
		List<AerialFishSpot> reachable = new ArrayList<>();
		for (AerialFishSpot spot : spots)
		{
			if (evaluate(spot, player, currentTick, params))
				reachable.add(spot);
		}

		reachable.sort(PRIORITY);
		for (int i = 0; i < reachable.size(); i++)
			reachable.get(i).setRank(i + 1);

		return reachable;
	}

	/**
	 * Fills in one spot's derived fields and reports whether it should be ranked.
	 *
	 * @param spot the spot to evaluate
	 * @param player the player's world tile, or {@code null} if unknown
	 * @param currentTick the current game tick counter
	 * @param params the tuning inputs
	 * @return {@code true} if the spot is within the max reach distance
	 */
	private static boolean evaluate(AerialFishSpot spot, WorldPoint player, int currentTick,
		RankingParams params)
	{
		int distance = distance(player, spot.getLocation());
		int catchTicks = catchTicksForDistance(distance);
		int age = Math.max(0, currentTick - spot.getLastMoveTick());
		int remaining = Math.max(0, params.getMinLifeTicks() - age);

		spot.setChebyshevDistance(distance);
		spot.setCatchTicks(catchTicks);
		spot.setEffectiveCatchTicks(spot.isFrenzied() ? FRENZY_TIER : catchTicks);
		spot.setRemainingTicks(remaining);
		spot.setRank(0);

		return distance <= params.getMaxReachDistance();
	}

	/**
	 * Maps Chebyshev distance to raw catch ticks.
	 *
	 * @param distance the Chebyshev tile distance from the player to the spot
	 * @return the catch time in ticks, from 1 up to {@link #MAX_CATCH_TICKS}
	 */
	private static int catchTicksForDistance(int distance)
	{
		if (distance <= ONE_TICK_MAX_DISTANCE)
			return 1;

		if (distance <= TWO_TICK_MAX_DISTANCE)
			return 2;

		if (distance <= THREE_TICK_MAX_DISTANCE)
			return 3;

		if (distance <= FOUR_TICK_MAX_DISTANCE)
			return 4;

		if (distance <= FIVE_TICK_MAX_DISTANCE)
			return 5;

		return MAX_CATCH_TICKS;
	}

	/**
	 * Chebyshev distance between two tiles, treating a missing tile as unreachable.
	 *
	 * @param player the player's world tile, or {@code null}
	 * @param spot the spot's world tile, or {@code null}
	 * @return the Chebyshev distance, or {@link Integer#MAX_VALUE} if either tile is null
	 */
	private static int distance(WorldPoint player, WorldPoint spot)
	{
		if (player == null || spot == null)
			return Integer.MAX_VALUE;

		return player.distanceTo2D(spot);
	}
}
