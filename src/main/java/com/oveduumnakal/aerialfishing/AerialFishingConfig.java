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
	 * Whether to draw each spot's estimated catch time in ticks.
	 *
	 * @return {@code true} to show the catch-tick label
	 */
	@ConfigItem(
		keyName = "showCatchTicks",
		name = "Show catch-tick label",
		description = "Draw each spot's estimated catch time in ticks",
		section = displaySection,
		position = 2
	)
	default boolean showCatchTicks()
	{
		return true;
	}

	/**
	 * How to show each spot's estimated time until it relocates.
	 *
	 * @return the expiry display mode
	 */
	@ConfigItem(
		keyName = "expiryDisplay",
		name = "Expiry display",
		description = "How soon a spot will move: none, a countdown pie, or a seconds countdown",
		section = displaySection,
		position = 3
	)
	default ExpiryDisplay expiryDisplay()
	{
		return ExpiryDisplay.PIE;
	}

	/**
	 * The maximum rank to draw (ignored when highlighting the best spot only).
	 *
	 * @return the highest rank number to show
	 */
	@Range(min = 1, max = 15)
	@ConfigItem(
		keyName = "maxRankShown",
		name = "Max spots ranked",
		description = "How many spots to number, best first",
		section = displaySection,
		position = 4
	)
	default int maxRankShown()
	{
		return 8;
	}

	/**
	 * Whether to switch off RuneLite's built-in Fishing plugin spot highlights while
	 * aerial spots are nearby, so only the ranked spots are marked. The built-in
	 * settings are restored on leaving the spots or when this plugin stops, and on
	 * the next start if the client exited while they were switched off.
	 *
	 * @return {@code true} to suppress the built-in highlights
	 */
	@ConfigItem(
		keyName = "hideBuiltinHighlights",
		name = "Hide built-in highlights",
		description = "While aerial spots are nearby, hide RuneLite's Fishing plugin spot tiles/icons/names",
		section = displaySection,
		position = 5
	)
	default boolean hideBuiltinHighlights()
	{
		return true;
	}

	/**
	 * Whether to draw the flying-bird animation on a spot from when you click it until
	 * the cormorant heads back.
	 *
	 * @return {@code true} to show the bird animation
	 */
	@ConfigItem(
		keyName = "showBirdAnimation",
		name = "Show bird animation",
		description = "Animate a flying bird on the spot you clicked until the cormorant starts returning",
		section = displaySection,
		position = 6
	)
	default boolean showBirdAnimation()
	{
		return true;
	}

	/**
	 * The on-screen height of the bird animation in pixels.
	 *
	 * @return the bird animation height in pixels
	 */
	@Range(min = 12, max = 96)
	@ConfigItem(
		keyName = "birdAnimationSize",
		name = "Bird size (px)",
		description = "On-screen height of the bird animation",
		section = displaySection,
		position = 7
	)
	default int birdAnimationSize()
	{
		return 16;
	}

	/**
	 * Whether to treat frenzied spots (a distinct NPC id) as the 3-tick tier.
	 *
	 * <p>On by default. A frenzied spot still ranks below any 1- or 2-tick spot, so
	 * this only changes where a frenzied pool sits within the 3-tick tier.
	 *
	 * @return {@code true} to factor frenzy into the ranking
	 */
	@ConfigItem(
		keyName = "detectFrenzy",
		name = "Prioritize frenzied spots",
		description = "Rank frenzied spots as a flat 3-tick tier (still below any 1- or 2-tick spot)",
		section = tuningSection,
		position = 0
	)
	default boolean detectFrenzy()
	{
		return true;
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
		description = "Earliest a spot may relocate; drives the expiry countdown so it empties before a spot moves",
		section = tuningSection,
		position = 2
	)
	default int minLifeTicks()
	{
		return 12;
	}

	/**
	 * The upper bound of a spot's lifetime in ticks, used as the active-bird safety
	 * timeout.
	 *
	 * @return the maximum spot lifetime in ticks
	 */
	@Range(min = 4, max = 40)
	@ConfigItem(
		keyName = "maxLifeTicks",
		name = "Max spot life (ticks)",
		description = "Longest a spot is expected to stay; caps how long the bird animation can linger",
		section = tuningSection,
		position = 3
	)
	default int maxLifeTicks()
	{
		return 20;
	}

	/**
	 * The maximum Chebyshev distance at which a spot is still shown.
	 *
	 * @return the maximum reach distance in tiles
	 */
	@Range(min = 5, max = 25)
	@ConfigItem(
		keyName = "maxReachDistance",
		name = "Max reach (tiles)",
		description = "Ignore spots farther than this many tiles from you",
		section = tuningSection,
		position = 4
	)
	default int maxReachDistance()
	{
		return 15;
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
