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
import java.awt.image.BufferedImage;
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
import net.runelite.client.util.ImageUtil;

/**
 * Draws the ranked aerial fishing spots: a tile outline plus a priority number on
 * each, and (optionally) a catch-tick label and an expiry countdown pie. Reads the
 * ordering the plugin computed on the game thread; performs no game logic itself.
 */
public class AerialFishingOverlay extends Overlay
{
	/** Below this fraction of remaining life, a spot is drawn in the expiring color. */
	private static final float EXPIRING_FRACTION = 0.25f;

	/** Duration of one game tick, in milliseconds, for smooth sub-tick countdowns. */
	private static final long TICK_MS = 600L;

	/** Fixed world-height offset for the rank number, anchored to the tile (not the model). */
	private static final int RANK_Z_OFFSET = 80;

	/** Fixed world-height offset for the catch-tick label, below the rank number. */
	private static final int LABEL_Z_OFFSET = 30;

	/** World-height offset for the seconds countdown, at the tile. */
	private static final int SECONDS_Z_OFFSET = 0;

	/** World-height offset for the bird animation, hovering over the water. */
	private static final int BIRD_Z_OFFSET = 40;

	/**
	 * Downward nudge applied to the drawn bird, as a fraction of its drawn height, so
	 * the offset scales with zoom (10px on a 16px bird when first tuned).
	 */
	private static final float BIRD_Y_OFFSET_FRACTION = 0.625f;

	/** Smallest height, in pixels, the bird is scaled down to when fully zoomed out. */
	private static final int BIRD_MIN_HEIGHT = 4;

	/** Number of frames in the bird flight strip. */
	private static final int BIRD_FRAMES = 8;

	/** Milliseconds each bird frame is shown, giving roughly a 14 fps flap. */
	private static final long BIRD_FRAME_MS = 72L;

	/** Classpath name of the horizontal bird flight strip. */
	private static final String BIRD_STRIP = "bird_flight_strip.png";

	private final AerialFishingPlugin plugin;

	private final Client client;

	/** The native-size flight frames sliced from the strip, or {@code null} if it failed to load. */
	private final BufferedImage[] birdFrames;

	/** The flight frames scaled to {@link #scaledHeight}, rebuilt when the config size changes. */
	private BufferedImage[] scaledFrames;

	/** The height, in pixels, the {@link #scaledFrames} are currently scaled to. */
	private int scaledHeight = -1;

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
		this.birdFrames = loadBirdFrames();
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	/**
	 * Loads the flight strip and slices it into equal-width frames.
	 *
	 * @return the sliced frames, or {@code null} if the strip could not be loaded
	 */
	private BufferedImage[] loadBirdFrames()
	{
		BufferedImage strip = ImageUtil.loadImageResource(getClass(), BIRD_STRIP);
		if (strip == null)
			return null;

		int frameWidth = strip.getWidth() / BIRD_FRAMES;
		int height = strip.getHeight();
		BufferedImage[] frames = new BufferedImage[BIRD_FRAMES];
		for (int i = 0; i < BIRD_FRAMES; i++)
			frames[i] = strip.getSubimage(i * frameWidth, 0, frameWidth, height);

		return frames;
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

			if (config.showCatchTicks())
				drawTickLabel(graphics, npc, spot, color);

			drawExpiry(graphics, npc, spot, config, color);
		}

		drawBird(graphics, config);
		return null;
	}

	/**
	 * Draws the current flight frame on the spot the player is fishing, if the bird
	 * animation is enabled and a spot is active. The frame is chosen from wall-clock
	 * time so the flap runs smoothly regardless of the client frame rate.
	 *
	 * @param graphics the overlay graphics context
	 * @param config the plugin config
	 */
	private void drawBird(Graphics2D graphics, AerialFishingConfig config)
	{
		if (!config.showBirdAnimation() || birdFrames == null)
			return;

		AerialFishSpot spot = plugin.getActiveBirdSpot();
		if (spot == null)
			return;

		NPC npc = spot.getNpc();
		if (npc == null)
			return;

		LocalPoint localPoint = npc.getLocalLocation();
		Polygon tile = npc.getCanvasTilePoly();
		if (localPoint == null || tile == null)
			return;

		BufferedImage[] scaled = ensureScaled(birdHeightFor(tile, config.birdTileScale()));
		int index = (int) (System.currentTimeMillis() / BIRD_FRAME_MS % BIRD_FRAMES);
		BufferedImage frame = scaled[index];

		Point location = Perspective.getCanvasImageLocation(client, localPoint, frame, BIRD_Z_OFFSET);
		if (location == null)
			return;

		int nudge = Math.round(frame.getHeight() * BIRD_Y_OFFSET_FRACTION);
		graphics.drawImage(frame, location.getX(), location.getY() + nudge, null);
	}

	/**
	 * The bird height, in pixels, that makes its width the given percentage of the
	 * tile's on-screen width, keeping the frame's aspect ratio.
	 *
	 * @param tile the spot tile's canvas polygon
	 * @param percent the bird width as a percentage of the tile width
	 * @return the bird height in pixels, at least {@link #BIRD_MIN_HEIGHT}
	 */
	private int birdHeightFor(Polygon tile, int percent)
	{
		float width = tile.getBounds().width * percent / 100f;
		float aspect = birdFrames[0].getHeight() / (float) birdFrames[0].getWidth();
		return Math.max(BIRD_MIN_HEIGHT, Math.round(width * aspect));
	}

	/**
	 * Returns the flight frames scaled to the given height, rebuilding them only when
	 * the requested height has changed since the last call.
	 *
	 * @param height the target frame height in pixels
	 * @return the scaled frames
	 */
	private BufferedImage[] ensureScaled(int height)
	{
		if (scaledFrames != null && scaledHeight == height)
			return scaledFrames;

		int nativeWidth = birdFrames[0].getWidth();
		int nativeHeight = birdFrames[0].getHeight();
		int width = Math.max(1, Math.round(height * (nativeWidth / (float) nativeHeight)));
		BufferedImage[] scaled = new BufferedImage[BIRD_FRAMES];
		for (int i = 0; i < BIRD_FRAMES; i++)
			scaled[i] = ImageUtil.resizeImage(birdFrames[i], width, height);

		scaledFrames = scaled;
		scaledHeight = height;
		return scaled;
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
		if (remainingFraction(spot, config) < EXPIRING_FRACTION)
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
		Point location = tileText(graphics, npc, text, RANK_Z_OFFSET);
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
		Point location = tileText(graphics, npc, text, LABEL_Z_OFFSET);
		if (location != null)
			OverlayUtil.renderTextLocation(graphics, location, text, color);
	}

	/**
	 * Projects text to a fixed height above the spot's tile, so it stays put while
	 * the spot's model animates (which is what makes model-anchored text jump).
	 *
	 * @param graphics the overlay graphics context
	 * @param npc the spot NPC
	 * @param text the text to place
	 * @param zOffset the fixed world-height offset above the tile
	 * @return the canvas point for the text, or {@code null} if off-screen
	 */
	private Point tileText(Graphics2D graphics, NPC npc, String text, int zOffset)
	{
		LocalPoint localPoint = npc.getLocalLocation();
		if (localPoint == null)
			return null;

		return Perspective.getCanvasTextLocation(client, graphics, localPoint, text, zOffset);
	}

	/**
	 * Draws the configured expiry indicator (none, pie, or seconds) for a spot.
	 *
	 * @param graphics the overlay graphics context
	 * @param npc the spot NPC
	 * @param spot the spot being drawn
	 * @param config the plugin config
	 * @param color the indicator color
	 */
	private void drawExpiry(Graphics2D graphics, NPC npc, AerialFishSpot spot,
		AerialFishingConfig config, Color color)
	{
		ExpiryDisplay mode = config.expiryDisplay();
		if (mode == ExpiryDisplay.PIE)
			drawExpiryPie(graphics, npc, spot, config, color);
		else if (mode == ExpiryDisplay.SECONDS)
			drawExpirySeconds(graphics, npc, spot, config, color);
	}

	/**
	 * Draws a smoothly-shrinking countdown pie at the spot.
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
		pie.setProgress(remainingFraction(spot, config));
		pie.render(graphics);
	}

	/**
	 * Draws a seconds countdown (one decimal place) at the spot.
	 *
	 * @param graphics the overlay graphics context
	 * @param npc the spot NPC
	 * @param spot the spot being drawn
	 * @param config the plugin config
	 * @param color the text color
	 */
	private void drawExpirySeconds(Graphics2D graphics, NPC npc, AerialFishSpot spot,
		AerialFishingConfig config, Color color)
	{
		String text = String.format("%.1f", remainingSeconds(spot, config));
		Point location = tileText(graphics, npc, text, SECONDS_Z_OFFSET);
		if (location != null)
			OverlayUtil.renderTextLocation(graphics, location, text, color);
	}

	/**
	 * Milliseconds until the spot is expected to relocate, clamped to {@code [0, full]},
	 * interpolated from wall-clock time so it advances every frame rather than every tick.
	 *
	 * @param spot the spot being drawn
	 * @param config the plugin config
	 * @return the remaining time in milliseconds
	 */
	private long remainingMillis(AerialFishSpot spot, AerialFishingConfig config)
	{
		long full = lifeMillis(spot, config);
		long elapsed = System.currentTimeMillis() - spot.getLastMoveTimeMillis();
		return Math.max(0L, Math.min(full, full - elapsed));
	}

	/**
	 * Fraction of the spot's expected life still remaining, in {@code [0, 1]}.
	 *
	 * @param spot the spot being drawn
	 * @param config the plugin config
	 * @return the remaining-life fraction
	 */
	private float remainingFraction(AerialFishSpot spot, AerialFishingConfig config)
	{
		return remainingMillis(spot, config) / (float) lifeMillis(spot, config);
	}

	/**
	 * The spot's full expected lifetime in milliseconds: fixed for a frenzied spot,
	 * otherwise the configured minimum.
	 *
	 * @param spot the spot being drawn
	 * @param config the plugin config
	 * @return the lifetime in milliseconds, at least 1
	 */
	private long lifeMillis(AerialFishSpot spot, AerialFishingConfig config)
	{
		return Math.max(1L, (long) SpotRanker.lifeTicks(spot, config.minLifeTicks()) * TICK_MS);
	}

	/**
	 * Seconds until the spot is expected to relocate.
	 *
	 * @param spot the spot being drawn
	 * @param config the plugin config
	 * @return the remaining time in seconds
	 */
	private double remainingSeconds(AerialFishSpot spot, AerialFishingConfig config)
	{
		return remainingMillis(spot, config) / 1000.0;
	}
}
