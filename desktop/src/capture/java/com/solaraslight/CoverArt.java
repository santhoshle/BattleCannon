/*
 * Copyright 2021-2026 Solaras Light
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.solaraslight;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.ParticleEffectPool.PooledEffect;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;

import java.util.function.Consumer;

/**
 * Renders the itch.io cover image, a wide header and the app icon from the game's own sprites, fonts and particle
 * effects. The images are drawn at twice the size itch.io asks for; the build script scales them down.
 */
class CoverArt {
	private static final float STEP = 1f / 60;

	private final CanyonSalvo game;
	private final SpriteBatch batch;
	private final int width, height;
	private final float tileHeight, padWidth, launcherHeight, launcherWidth, missileHeight, missileWidth;

	private CoverArt(CanyonSalvo game, int width, int height, float tileHeight) {
		this.game = game;
		this.batch = game.batch;
		this.width = width;
		this.height = height;
		this.tileHeight = tileHeight;
		padWidth = tileHeight * game.launchPad.getWidth() / game.launchPad.getHeight();
		launcherHeight = tileHeight * 0.95f;
		launcherWidth = launcherHeight * game.launcher.getWidth() / game.launcher.getHeight();
		missileHeight = tileHeight * 0.8f;
		missileWidth = missileHeight * game.missile.getWidth() / game.missile.getHeight();
	}

	static void renderAll(CanyonSalvo game, FileHandle mediaDir) {
		new CoverArt(game, 1260, 1000, 1000 / 6.5f).render(mediaDir.child("cover-2x.png"), CoverArt::drawCover);
		new CoverArt(game, 1920, 640, 640 / 4.6f).render(mediaDir.child("header-2x.png"), CoverArt::drawHeader);
		new CoverArt(game, 512, 512, 512 / 2.2f).render(mediaDir.child("icon-512.png"), CoverArt::drawIcon);
		game.batch.getProjectionMatrix().setToOrtho2D(0, 0, game.screenWidth, game.screenHeight);
	}

	private void render(FileHandle file, Consumer<CoverArt> scene) {
		FrameBuffer buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
		buffer.begin();
		ScreenUtils.clear(0, 0, 0, 1);
		batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
		batch.begin();
		scene.accept(this);
		batch.end();
		MediaCapture.ScriptedGame.saveFrame(file, width, height, 6);
		buffer.end();
		buffer.dispose();
		Gdx.app.log("capture", "Saved " + file.path());
	}

	// ---- Scenes ----

	private void drawCover() {
		drawBackground(1f, 0.5f);
		shade(0, 0, width, height, 0.12f);
		drawPadRow(0);
		drawPadRow(height - tileHeight);

		float leftX = width * 0.13f, rightX = width * 0.87f;
		// Enemy: launcher on the top row, its missile diving at the player's flares in the bottom-right column.
		drawLauncher(rightX, height - tileHeight, true);
		drawLauncher(leftX, 0, false);
		drawDispenser(rightX, tileHeight * 0.8f);
		drawDispenser(leftX, height - tileHeight * 0.8f);

		// The player's missile climbing the left column, trailing exhaust.
		float playerMissileY = height * 0.5f;
		trail(game.exhaust.obtain(), leftX, tileHeight - missileHeight, playerMissileY, false);
		drawMissile(leftX, playerMissileY, false);

		// The enemy's missile meeting the flare cloud just above the player's dispenser.
		float enemyMissileY = tileHeight * 1.9f;
		trail(game.enemyExhaust.obtain(), rightX, height - tileHeight + missileHeight, enemyMissileY + missileHeight, true);
		drawMissile(rightX, enemyMissileY, true);
		burst(game.flares.obtain(), rightX, tileHeight * 0.8f, 0.45f);
		burst(game.explosions.obtain(), rightX, enemyMissileY + missileHeight * 0.15f, 0.14f);

		float titleSize = height * 0.165f;
		drawTitle("CANYON", titleSize, height * 0.66f);
		drawTitle("SALVO", titleSize, height * 0.66f - titleSize * 1.05f);
		drawCaption("A turn-based missile duel", height * 0.25f, height * 0.04f);
	}

	private void drawHeader() {
		drawBackground(1f, 0.52f);
		shade(0, 0, width, height, 0.15f);
		float titleSize = height * 0.3f;
		drawTitle("CANYON SALVO", titleSize, height * 0.6f);
		drawCaption("Outguess the enemy. Don't be predictable.", height * 0.27f, height * 0.06f);
	}

	private void drawIcon() {
		drawBackground(1.9f, 0.57f);
		float x = width / 2f;
		trail(game.exhaust.obtain(), x, height * 0.18f, height * 0.62f, false);
		drawMissile(x, height * 0.62f, false);
		float size = height * 0.5f;
		float launcherW = size * game.launcher.getWidth() / game.launcher.getHeight();
		batch.draw(game.launcher, x - launcherW / 2, height * 0.04f, launcherW, size);
	}

	// ---- Drawing helpers ----

	/** Draws the battlefield covering the image, zoomed by {@code zoom}, centred on {@code focusY} of the texture. */
	private void drawBackground(float zoom, float focusY) {
		Texture background = game.background;
		float scale = Math.max((float) width / background.getWidth(), (float) height / background.getHeight()) * zoom;
		float w = background.getWidth() * scale, h = background.getHeight() * scale;
		batch.draw(background, (width - w) / 2, height / 2f - h * (1 - focusY), w, h);
	}

	private void shade(float x, float y, float w, float h, float alpha) {
		batch.setColor(0, 0, 0, alpha);
		batch.draw(game.pixel, x, y, w, h);
		batch.setColor(Color.WHITE);
	}

	private void drawPadRow(float y) {
		int count = (int) Math.ceil(width / padWidth) + 1;
		float startX = (width - count * padWidth) / 2;
		for (int i = 0; i < count; i++) {
			batch.draw(game.launchPad, startX + i * padWidth, y, padWidth, tileHeight);
		}
	}

	private void drawLauncher(float centerX, float rowY, boolean enemy) {
		batch.draw(game.launcher, centerX - launcherWidth / 2, rowY + (tileHeight - launcherHeight) / 2, launcherWidth,
				launcherHeight, 0, 0, game.launcher.getWidth(), game.launcher.getHeight(), false, enemy);
	}

	private void drawDispenser(float centerX, float centerY) {
		float size = tileHeight * 0.35f;
		batch.draw(game.flareDispenser, centerX - size / 2, centerY - size / 2, size, size);
	}

	private void drawMissile(float centerX, float y, boolean enemy) {
		batch.draw(game.missile, centerX - missileWidth / 2, y, missileWidth, missileHeight, 0, 0,
				game.missile.getWidth(), game.missile.getHeight(), false, enemy);
	}

	/** Moves an exhaust effect from {@code fromY} to {@code toY}, as a missile would, so it leaves a trail. */
	private void trail(PooledEffect effect, float x, float fromY, float toY, boolean enemy) {
		fitToImage(effect);
		effect.setPosition(x, fromY);
		effect.start();
		float speed = height / 2f, distance = Math.abs(toY - fromY);
		for (float travelled = 0; travelled < distance; travelled += speed * STEP) {
			effect.setPosition(x, fromY + (enemy ? -travelled : travelled));
			effect.update(STEP);
		}
		effect.setPosition(x, toY);
		effect.update(STEP);
		effect.draw(batch);
	}

	/** Starts an effect at (x, y) and lets it play for {@code seconds}. */
	private void burst(PooledEffect effect, float x, float y, float seconds) {
		fitToImage(effect);
		effect.setPosition(x, y);
		effect.start();
		for (float t = 0; t < seconds; t += STEP) {
			effect.update(STEP);
		}
		effect.draw(batch);
	}

	/**
	 * Pooled effects are scaled for the game window, so this rescales the copy for the image being drawn. The art is
	 * rendered just before the capture exits, so the rescaled copies are never used by the game again.
	 */
	private void fitToImage(PooledEffect effect) {
		effect.scaleEffect(height / game.screenHeight);
	}

	private void drawTitle(String text, float capHeight, float centerY) {
		BitmapFont title = game.title;
		float previous = title.getData().scaleX;
		title.getData().setScale(capHeight / Constants.TITLE_FONT_BAKED_SIZE);
		GlyphLayout layout = new GlyphLayout(title, text, Color.GOLD, width, Align.center, false);
		title.draw(batch, layout, 0, centerY + layout.height / 2);
		title.getData().setScale(previous);
	}

	private void drawCaption(String text, float centerY, float size) {
		BitmapFont font = game.font;
		float previous = font.getData().scaleX;
		font.getData().setScale(size / Constants.FONT_BAKED_SIZE);
		GlyphLayout layout = new GlyphLayout(font, text, Color.WHITE, width, Align.center, false);
		float padX = size * 0.8f, padY = size * 0.45f;
		shade((width - layout.width) / 2 - padX, centerY - layout.height / 2 - padY, layout.width + 2 * padX,
				layout.height + 2 * padY, 0.55f);
		font.draw(batch, layout, 0, centerY + layout.height / 2);
		font.getData().setScale(previous);
	}
}
