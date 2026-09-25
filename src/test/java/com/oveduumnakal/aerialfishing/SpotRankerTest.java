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

	private static final RankingParams PARAMS = new RankingParams(12, 15, true);

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

	/**
	 * Catch ticks step up exactly at each band edge, and cap at 6 from 10 tiles out.
	 */
	@Test
	public void distanceBandEdges()
	{
		int[] distances = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15};
		int[] expected = {1, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6};
		for (int i = 0; i < distances.length; i++)
		{
			AerialFishSpot s = spot(distances[i], false, 0);
			SpotRanker.rank(PLAYER, TICK, Arrays.asList(s), PARAMS);
			assertEquals("distance " + distances[i], expected[i], s.getCatchTicks());
		}
	}

	/**
	 * A spot past the 3-tick band is still ranked, but after every 3-tick spot.
	 */
	@Test
	public void farSpotRanksAfterThreeTick()
	{
		AerialFishSpot threeTick = spot(5, false, 10);
		AerialFishSpot far = spot(6, false, 0);

		List<AerialFishSpot> ranked = SpotRanker.rank(PLAYER, TICK, Arrays.asList(far, threeTick), PARAMS);

		assertEquals(threeTick, ranked.get(0));
		assertEquals(far, ranked.get(1));
	}

	/**
	 * A spot exactly at the max reach distance is kept; one tile farther is dropped.
	 */
	@Test
	public void maxReachDistanceIsInclusive()
	{
		AerialFishSpot atEdge = spot(15, false, 0);
		AerialFishSpot pastEdge = spot(16, false, 0);

		List<AerialFishSpot> ranked = SpotRanker.rank(PLAYER, TICK, Arrays.asList(atEdge, pastEdge), PARAMS);

		assertTrue(ranked.contains(atEdge));
		assertFalse(ranked.contains(pastEdge));
	}

	/**
	 * Within the 3-tick tier, frenzied beats a plain spot even when the plain spot
	 * has more life left.
	 */
	@Test
	public void frenzyOutranksLongerLivedPlainSpotInTier()
	{
		AerialFishSpot oldFrenzy = spot(5, true, 10);
		AerialFishSpot freshPlain = spot(5, false, 0);

		List<AerialFishSpot> ranked = SpotRanker.rank(PLAYER, TICK, Arrays.asList(freshPlain, oldFrenzy), PARAMS);

		assertEquals(oldFrenzy, ranked.get(0));
		assertEquals(freshPlain, ranked.get(1));
	}

	/**
	 * A frenzied spot is pinned to the 3-tick tier even when it is close enough to
	 * catch in 1 tick, so a plain 2-tick spot still outranks it.
	 */
	@Test
	public void adjacentFrenzyStaysInThreeTickTier()
	{
		AerialFishSpot adjacentFrenzy = spot(1, true, 0);
		AerialFishSpot twoTick = spot(3, false, 0);

		List<AerialFishSpot> ranked = SpotRanker.rank(PLAYER, TICK, Arrays.asList(adjacentFrenzy, twoTick), PARAMS);

		assertEquals(twoTick, ranked.get(0));
		assertEquals(adjacentFrenzy, ranked.get(1));
		assertEquals(1, adjacentFrenzy.getCatchTicks());
	}

	/**
	 * A spot that falls out of reach loses its previous rank.
	 */
	@Test
	public void droppedSpotHasRankCleared()
	{
		AerialFishSpot s = spot(1, false, 0);
		SpotRanker.rank(PLAYER, TICK, Arrays.asList(s), PARAMS);
		assertEquals(1, s.getRank());

		s.setLocation(new WorldPoint(PLAYER.getX() + 20, PLAYER.getY(), PLAYER.getPlane()));
		SpotRanker.rank(PLAYER, TICK, Arrays.asList(s), PARAMS);

		assertEquals(0, s.getRank());
	}

	/**
	 * With no known player tile, nothing is ranked.
	 */
	@Test
	public void unknownPlayerRanksNothing()
	{
		List<AerialFishSpot> ranked = SpotRanker.rank(null, TICK, Arrays.asList(spot(1, false, 0)), PARAMS);

		assertTrue(ranked.isEmpty());
	}

	/**
	 * Past the 3-tick band, a nearer spot still outranks a farther one even when the
	 * farther spot has more life left.
	 */
	@Test
	public void nearerFarSpotBeatsLongerLivedFartherSpot()
	{
		AerialFishSpot sixTiles = spot(6, false, 10);
		AerialFishSpot twelveTiles = spot(12, false, 0);

		List<AerialFishSpot> ranked = SpotRanker.rank(PLAYER, TICK, Arrays.asList(twelveTiles, sixTiles), PARAMS);

		assertEquals(sixTiles, ranked.get(0));
		assertEquals(twelveTiles, ranked.get(1));
	}

	/**
	 * A frenzied spot counts down from its fixed 28-tick lifetime; a plain spot of
	 * the same age uses the configured minimum and has already run out.
	 */
	@Test
	public void frenziedSpotUsesFixedLifetime()
	{
		AerialFishSpot frenzy = spot(5, true, 20);
		AerialFishSpot plain = spot(5, false, 20);

		SpotRanker.rank(PLAYER, TICK, Arrays.asList(frenzy, plain), PARAMS);

		assertEquals(SpotRanker.FRENZIED_LIFE_TICKS - 20, frenzy.getRemainingTicks());
		assertEquals(0, plain.getRemainingTicks());
	}

	/**
	 * With frenzy not prioritized, a frenzied spot ranks by its real distance tier, so
	 * a nearer plain 3-tick spot beats a frenzied spot 8 tiles away.
	 */
	@Test
	public void unprioritizedFrenzyRanksByDistance()
	{
		RankingParams params = new RankingParams(12, 15, false);
		AerialFishSpot farFrenzy = spot(8, true, 0);
		AerialFishSpot plainThree = spot(5, false, 0);

		List<AerialFishSpot> ranked = SpotRanker.rank(PLAYER, TICK, Arrays.asList(farFrenzy, plainThree), params);

		assertEquals(plainThree, ranked.get(0));
		assertEquals(farFrenzy, ranked.get(1));
		assertEquals(5, farFrenzy.getEffectiveCatchTicks());
	}
}
