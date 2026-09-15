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

import java.awt.Color;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * User-facing settings for the Aerial Fishing Helper plugin: what the overlay
 * draws, the colors it uses, and the tick constants that drive spot ranking.
 */
@ConfigGroup(AerialFishingConfig.GROUP)
public interface AerialFishingConfig extends Config
{
	/** The config group key, shared with {@code ConfigChanged} handling. */
	String GROUP = "aerialfishing";

	/** Section holding the visual/display toggles. */
	@ConfigSection(
		name = "Display",
		description = "What the overlay draws",
		position = 0
	)
	String displaySection = "display";

	/** Section holding the ranking-tuning constants. */
	@ConfigSection(
		name = "Tuning",
		description = "Advanced ranking constants",
		position = 1
	)
	String tuningSection = "tuning";

	/** Section holding the overlay colors. */
	@ConfigSection(
		name = "Colors",
		description = "Overlay colors",
		position = 2
	)
	String colorSection = "colors";

	/**
	 * Whether to draw the priority number above each ranked spot.
	 *
	 * @return {@code true} to show rank numbers
	 */
	@ConfigItem(
		keyName = "showRankNumbers",
		name = "Show rank numbers",
		description = "Draw the click-order number (1, 2, 3...) above each spot",
		section = displaySection,
		position = 0
	)
	default boolean showRankNumbers()
	{
		return true;
	}

	/**
	 * Whether to highlight only the single best spot instead of every ranked spot.
	 *
	 * @return {@code true} to show only rank 1
	 */
	@ConfigItem(
		keyName = "highlightBestOnly",
		name = "Highlight best only",
		description = "Highlight only the single best spot to click next",
		section = displaySection,
		position = 1
	)
	default boolean highlightBestOnly()
	{
		return false;
	}

	/**
	 * Whether to draw the catch-tick label and the expiry countdown pie.
	 *
	 * @return {@code true} to show timers
	 */
	@ConfigItem(
		keyName = "showTimers",
		name = "Show tick and expiry timers",
		description = "Draw each spot's catch-tick estimate and a countdown until it moves",
		section = displaySection,
		position = 2
	)
	default boolean showTimers()
	{
		return true;
	}

	/**
	 * The maximum rank to draw (ignored when highlighting the best spot only).
	 *
	 * @return the highest rank number to show
	 */
	@Range(min = 1, max = 10)
	@ConfigItem(
		keyName = "maxRankShown",
		name = "Max spots ranked",
		description = "How many spots to number, best first",
		section = displaySection,
		position = 3
	)
	default int maxRankShown()
	{
		return 3;
	}

	/**
	 * Whether to treat spots carrying {@link #frenzySpotanimId()} as frenzied.
	 *
	 * <p>Off by default: aerial frenzy has no confirmed client-side signal, so this
	 * stays opt-in until a spot-anim id is verified in game.
	 *
	 * @return {@code true} to enable frenzy detection
	 */
	@ConfigItem(
		keyName = "detectFrenzy",
		name = "Detect frenzied spots",
		description = "Pin spots with the configured spot-anim to the 3-tick tier (experimental)",
		section = tuningSection,
		position = 0
	)
	default boolean detectFrenzy()
	{
		return false;
	}

	/**
	 * The spot-anim (graphic) id that marks a frenzied spot when detection is on.
	 *
	 * @return the frenzy spot-anim id, or a negative value for none
	 */
	@ConfigItem(
		keyName = "frenzySpotanimId",
		name = "Frenzy spot-anim id",
		description = "Graphic id that identifies a frenzied spot (advanced; -1 disables)",
		section = tuningSection,
		position = 1
	)
	default int frenzySpotanimId()
	{
		return -1;
	}

	/**
	 * The conservative lower bound of a spot's lifetime in ticks.
	 *
	 * @return the minimum spot lifetime in ticks
	 */
	@Range(min = 4, max = 30)
	@ConfigItem(
		keyName = "minLifeTicks",
		name = "Min spot life (ticks)",
		description = "Assume a spot may relocate this soon; used to skip spots about to move",
		section = tuningSection,
		position = 2
	)
	default int minLifeTicks()
	{
		return 12;
	}

	/**
	 * The upper bound of a spot's lifetime in ticks, used for the countdown pie.
	 *
	 * @return the maximum spot lifetime in ticks
	 */
	@Range(min = 4, max = 40)
	@ConfigItem(
		keyName = "maxLifeTicks",
		name = "Max spot life (ticks)",
		description = "Longest a spot is expected to stay before relocating",
		section = tuningSection,
		position = 3
	)
	default int maxLifeTicks()
	{
		return 20;
	}

	/**
	 * The safety margin in ticks added to catch time when testing reachability.
	 *
	 * @return the reach buffer in ticks
	 */
	@Range(min = 0, max = 5)
	@ConfigItem(
		keyName = "reachBufferTicks",
		name = "Reach buffer (ticks)",
		description = "Extra ticks required beyond catch time before trusting a spot",
		section = tuningSection,
		position = 4
	)
	default int reachBufferTicks()
	{
		return 1;
	}

	/**
	 * The maximum Chebyshev distance at which a spot is still shown.
	 *
	 * @return the maximum reach distance in tiles
	 */
	@Range(min = 5, max = 20)
	@ConfigItem(
		keyName = "maxReachDistance",
		name = "Max reach (tiles)",
		description = "Ignore spots farther than this many tiles from you",
		section = tuningSection,
		position = 5
	)
	default int maxReachDistance()
	{
		return 10;
	}

	/**
	 * The color of the best spot to click next.
	 *
	 * @return the rank-1 highlight color
	 */
	@Alpha
	@ConfigItem(
		keyName = "bestSpotColor",
		name = "Best spot",
		description = "Color of the top-ranked spot",
		section = colorSection,
		position = 0
	)
	default Color bestSpotColor()
	{
		return new Color(0, 255, 0, 200);
	}

	/**
	 * The color of ranked spots other than the best.
	 *
	 * @return the secondary highlight color
	 */
	@Alpha
	@ConfigItem(
		keyName = "otherSpotColor",
		name = "Other spots",
		description = "Color of ranked spots below the best",
		section = colorSection,
		position = 1
	)
	default Color otherSpotColor()
	{
		return new Color(0, 200, 255, 160);
	}

	/**
	 * The color used when a spot is close to relocating.
	 *
	 * @return the expiring-spot color
	 */
	@Alpha
	@ConfigItem(
		keyName = "expiringColor",
		name = "Expiring spot",
		description = "Color of a spot that is about to move",
		section = colorSection,
		position = 2
	)
	default Color expiringColor()
	{
		return new Color(255, 150, 0, 200);
	}
}
