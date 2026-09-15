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
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;

/**
 * Draws the ranked aerial fishing spots: a tile outline plus a priority number on
 * each, and (optionally) a catch-tick label and an expiry countdown pie. Reads the
 * ordering the plugin computed on the game thread; performs no game logic itself.
 */
public class AerialFishingOverlay extends Overlay
{
	/** Below this fraction of remaining life, a spot is drawn in the expiring color. */
	private static final float EXPIRING_FRACTION = 0.25f;

	/** Vertical offset above the spot for the rank number. */
	private static final int RANK_HEIGHT_OFFSET = 40;

	/** Vertical offset above the spot for the catch-tick label. */
	private static final int LABEL_HEIGHT_OFFSET = 20;

	private final AerialFishingPlugin plugin;

	private final Client client;

	/**
	 * Creates the overlay bound above the scene.
	 *
	 * @param plugin the owning plugin, source of the ranked spots and config
	 * @param client the game client, for tile-to-canvas projection
	 */
	@Inject
	private AerialFishingOverlay(AerialFishingPlugin plugin, Client client)
	{
		this.plugin = plugin;
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/**
	 * Renders every ranked spot up to the configured limit.
	 *
	 * @param graphics the overlay graphics context
	 * @return {@code null}; this overlay sets no bounds
	 */
	@Override
	public Dimension render(Graphics2D graphics)
	{
		AerialFishingConfig config = plugin.getConfig();
		int maxRank = config.highlightBestOnly() ? 1 : config.maxRankShown();

		for (AerialFishSpot spot : plugin.getRankedSpots())
		{
			if (spot.getRank() > maxRank)
				continue;

			NPC npc = spot.getNpc();
			if (npc == null)
				continue;

			Color color = colorFor(spot, config);
			drawTile(graphics, npc, color);

			if (config.showRankNumbers())
				drawRank(graphics, npc, spot.getRank(), color);

			if (config.showTimers())
			{
				drawTickLabel(graphics, npc, spot, color);
				drawExpiryPie(graphics, npc, spot, config, color);
			}
		}

		return null;
	}

	/**
	 * Picks the color for a spot: expiring spots warn, the best spot stands out,
	 * and the rest use the secondary color.
	 *
	 * @param spot the spot being drawn
	 * @param config the plugin config
	 * @return the color to draw the spot in
	 */
	private Color colorFor(AerialFishSpot spot, AerialFishingConfig config)
	{
		if (lifeFraction(spot, config) < EXPIRING_FRACTION)
			return config.expiringColor();

		if (spot.getRank() == 1)
			return config.bestSpotColor();

		return config.otherSpotColor();
	}

	/**
	 * Outlines the spot's tile.
	 *
	 * @param graphics the overlay graphics context
	 * @param npc the spot NPC
	 * @param color the outline color
	 */
	private void drawTile(Graphics2D graphics, NPC npc, Color color)
	{
		Polygon poly = npc.getCanvasTilePoly();
		if (poly != null)
			OverlayUtil.renderPolygon(graphics, poly, color);
	}

	/**
	 * Draws the priority number above the spot.
	 *
	 * @param graphics the overlay graphics context
	 * @param npc the spot NPC
	 * @param rank the spot's priority rank
	 * @param color the text color
	 */
	private void drawRank(Graphics2D graphics, NPC npc, int rank, Color color)
	{
		String text = Integer.toString(rank);
		Point location = npc.getCanvasTextLocation(graphics, text, npc.getLogicalHeight() + RANK_HEIGHT_OFFSET);
		if (location != null)
			OverlayUtil.renderTextLocation(graphics, location, text, color);
	}

	/**
	 * Draws the estimated catch-tick label (with a marker for frenzied spots).
	 *
	 * @param graphics the overlay graphics context
	 * @param npc the spot NPC
	 * @param spot the spot being drawn
	 * @param color the text color
	 */
	private void drawTickLabel(Graphics2D graphics, NPC npc, AerialFishSpot spot, Color color)
	{
		String text = spot.getCatchTicks() + "t" + (spot.isFrenzied() ? "*" : "");
		Point location = npc.getCanvasTextLocation(graphics, text, npc.getLogicalHeight() + LABEL_HEIGHT_OFFSET);
		if (location != null)
			OverlayUtil.renderTextLocation(graphics, location, text, color);
	}

	/**
	 * Draws a countdown pie showing how much of the spot's expected life remains.
	 *
	 * @param graphics the overlay graphics context
	 * @param npc the spot NPC
	 * @param spot the spot being drawn
	 * @param config the plugin config
	 * @param color the pie color
	 */
	private void drawExpiryPie(Graphics2D graphics, NPC npc, AerialFishSpot spot,
		AerialFishingConfig config, Color color)
	{
		LocalPoint localPoint = npc.getLocalLocation();
		if (localPoint == null)
			return;

		int plane = client.getTopLevelWorldView().getPlane();
		Point location = Perspective.localToCanvas(client, localPoint, plane);
		if (location == null)
			return;

		ProgressPieComponent pie = new ProgressPieComponent();
		pie.setFill(color);
		pie.setBorderColor(color);
		pie.setPosition(location);
		pie.setProgress(lifeFraction(spot, config));
		pie.render(graphics);
	}

	/**
	 * Fraction of the spot's maximum expected life still remaining, in {@code [0, 1]}.
	 *
	 * @param spot the spot being drawn
	 * @param config the plugin config
	 * @return the remaining-life fraction
	 */
	private float lifeFraction(AerialFishSpot spot, AerialFishingConfig config)
	{
		int maxLife = Math.max(1, config.maxLifeTicks());
		float fraction = (float) (maxLife - spot.getAgeTicks()) / maxLife;
		return Math.max(0f, Math.min(1f, fraction));
	}
}
