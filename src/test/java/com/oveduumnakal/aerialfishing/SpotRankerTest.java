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

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import net.runelite.api.coords.WorldPoint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link SpotRanker}, exercised on plain spot data with no live
 * client (each spot's NPC reference is {@code null}).
 */
public class SpotRankerTest
{
	private static final int TICK = 100;

	private static final WorldPoint PLAYER = new WorldPoint(3000, 3000, 0);

	private static final RankingParams PARAMS = new RankingParams(12, 20, 15);

	/**
	 * Builds a fresh spot at the given Chebyshev distance east of the player.
	 *
	 * @param distance tiles east of the player
	 * @param frenzied whether the spot is frenzied
	 * @param ageTicks how many ticks ago the spot last moved
	 * @return the constructed spot
	 */
	private static AerialFishSpot spot(int distance, boolean frenzied, int ageTicks)
	{
		WorldPoint location = new WorldPoint(PLAYER.getX() + distance, PLAYER.getY(), PLAYER.getPlane());
		AerialFishSpot s = new AerialFishSpot(null, location, TICK - ageTicks);
		s.setLastMoveTick(TICK - ageTicks);
		s.setFrenzied(frenzied);
		return s;
	}

	/**
	 * The closest reachable spot is ranked first, farther spots follow in order.
	 */
	@Test
	public void closestRanksFirst()
	{
		AerialFishSpot near = spot(1, false, 0);
		AerialFishSpot mid = spot(4, false, 0);
		AerialFishSpot far = spot(5, false, 0);

		List<AerialFishSpot> ranked = SpotRanker.rank(PLAYER, TICK, Arrays.asList(far, near, mid), PARAMS);

		assertEquals(3, ranked.size());
		assertEquals(near, ranked.get(0));
		assertEquals(mid, ranked.get(1));
		assertEquals(far, ranked.get(2));
		assertEquals(1, near.getRank());
	}

	/**
	 * An aging spot is kept in the ranking (expiry only affects order/color, never
	 * removes a still-fishable spot); the fresher spot ranks ahead.
	 */
	@Test
	public void agingSpotIsKeptButRanksAfterFresh()
	{
		AerialFishSpot fresh = spot(1, false, 0);
		AerialFishSpot aging = spot(1, false, 11);

		List<AerialFishSpot> ranked = SpotRanker.rank(PLAYER, TICK, Arrays.asList(aging, fresh), PARAMS);

		assertTrue(ranked.contains(fresh));
		assertTrue(ranked.contains(aging));
		assertEquals(fresh, ranked.get(0));
	}

	/**
	 * Speed wins: 1- and 2-tick spots outrank a frenzied pool, which in turn
	 * outranks a plain 3-tick spot.
	 */
	@Test
	public void fasterSpotsBeatFrenzyWhichBeatsPlainThreeTick()
	{
		AerialFishSpot oneTick = spot(1, false, 0);
		AerialFishSpot twoTick = spot(4, false, 0);
		AerialFishSpot frenzy = spot(1, true, 0);
		AerialFishSpot plainThree = spot(5, false, 0);

		List<AerialFishSpot> ranked = SpotRanker.rank(
			PLAYER, TICK, Arrays.asList(plainThree, frenzy, twoTick, oneTick), PARAMS);

		assertEquals(oneTick, ranked.get(0));
		assertEquals(twoTick, ranked.get(1));
		assertEquals(frenzy, ranked.get(2));
		assertEquals(plainThree, ranked.get(3));
		assertEquals(3, frenzy.getEffectiveCatchTicks());
	}

	/**
	 * Between two otherwise-equal spots, the longer-lived one is preferred.
	 */
	@Test
	public void longerLivedSpotWinsTie()
	{
		AerialFishSpot fresher = spot(1, false, 0);
		AerialFishSpot older = spot(1, false, 6);

		List<AerialFishSpot> ranked = SpotRanker.rank(PLAYER, TICK, Arrays.asList(older, fresher), PARAMS);

		assertEquals(fresher, ranked.get(0));
		assertEquals(older, ranked.get(1));
	}

	/**
	 * A spot beyond the maximum reach distance is dropped from the ranking.
	 */
	@Test
	public void tooFarSpotIsFiltered()
	{
		AerialFishSpot reachable = spot(5, false, 0);
		AerialFishSpot tooFar = spot(20, false, 0);

		List<AerialFishSpot> ranked = SpotRanker.rank(PLAYER, TICK, Arrays.asList(reachable, tooFar), PARAMS);

		assertTrue(ranked.contains(reachable));
		assertFalse(ranked.contains(tooFar));
	}
}
