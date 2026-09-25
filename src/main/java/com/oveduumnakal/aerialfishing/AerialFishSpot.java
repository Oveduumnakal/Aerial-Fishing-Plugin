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

import lombok.Getter;
import lombok.Setter;

import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;

/**
 * A single tracked aerial fishing spot at Molch Island.
 *
 * <p>The fields split into two groups. The <i>input</i> fields ({@link #npc},
 * {@link #location}, {@link #lastMoveTick}, {@link #lastMoveTimeMillis},
 * {@link #frenzied}) are maintained from game events by the plugin. The
 * <i>computed</i> fields ({@link #chebyshevDistance} through {@link #rank}) are
 * (re)written every tick by {@link SpotRanker} and read by the overlay.
 *
 * <p>{@link #npc} may be {@code null} in unit tests, which exercise the ranker on
 * plain data without a live client; nothing in {@link SpotRanker} dereferences it.
 */
@Getter
@Setter
public class AerialFishSpot
{
	/** The tracked fishing-spot NPC, or {@code null} in tests. */
	private final NPC npc;

	/** The spot's current world tile; reset when the spot relocates. */
	private WorldPoint location;

	/** The tick on which the spot last changed tiles (age is measured from here). */
	private int lastMoveTick;

	/** Wall-clock time (ms) the spot last changed tiles, for smooth per-frame countdown. */
	private long lastMoveTimeMillis;

	/** Whether the spot is a frenzied pool (by NPC id), whether or not ranking prioritizes it. */
	private boolean frenzied;

	/** Chebyshev tile distance from the player to this spot. */
	private int chebyshevDistance;

	/** Raw catch time in ticks from distance alone, 1 to 6. */
	private int catchTicks;

	/** Catch tier used for ranking: 3 for a prioritized frenzied spot, else {@link #catchTicks}. */
	private int effectiveCatchTicks;

	/** Estimated ticks before the spot relocates (conservative, clamped at zero). */
	private int remainingTicks;

	/** Priority rank assigned by the ranker; 1 is the best spot to click next. */
	private int rank;

	/**
	 * Creates a freshly-observed spot.
	 *
	 * @param npc the fishing-spot NPC, or {@code null} in tests
	 * @param location the spot's world tile
	 * @param firstSeenTick the tick on which the spot was first observed, taken as its last move
	 */
	public AerialFishSpot(NPC npc, WorldPoint location, int firstSeenTick)
	{
		this.npc = npc;
		this.location = location;
		this.lastMoveTick = firstSeenTick;
		this.lastMoveTimeMillis = System.currentTimeMillis();
	}
}
