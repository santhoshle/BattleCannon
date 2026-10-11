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

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEffectPool;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;

/** Entry point shared by all platforms. Owns the assets and screen measurements used by every screen. */
public class CanyonSalvo extends Game {
	/** Screen height the particle effects in assets/particles were designed for. */
	private static final float PARTICLE_DESIGN_HEIGHT = 600f;

	SpriteBatch batch;
	Texture background, launcher, missile, flareDispenser, launchPad, buttonTexture, healthBarTexture, pixel;
	NinePatch button, healthBar;
	BitmapFont font, title;

	/** Particle effects pointing upwards (player) and their mirrored versions pointing downwards (enemy). */
	ParticleEffectPool flares, enemyFlares, exhaust, enemyExhaust, explosions;
	private ParticleEffect flareTemplate, exhaustTemplate, explosionTemplate;

	float screenWidth, screenHeight;
	float tileWidth, tileHeight;
	/** Height of the strip at the top of the game screen that holds the health bars. */
	float hudHeight;
	/** Horizontal gap kept between text and the screen edges. */
	float textMargin;

	private final GlyphLayout layout = new GlyphLayout();
	private final Color buttonShade = new Color();

	@Override
	public void create() {
		batch = new SpriteBatch();
		background = new Texture("battlefield.jpg");
		background.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		launchPad = new Texture("launch_pad.png");
		launchPad.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		launcher = new Texture("launcher.png");
		launcher.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		missile = new Texture("missile.png");
		missile.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		flareDispenser = new Texture("flare_dispenser.png");
		flareDispenser.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		buttonTexture = new Texture("button.png");
		buttonTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		button = new NinePatch(buttonTexture, 20, 20, 20, 20);
		healthBarTexture = new Texture("health_bar.png");
		healthBarTexture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
		healthBar = new NinePatch(healthBarTexture, 8, 8, 8, 8);
		// A single white pixel, stretched and tinted to draw plain panels.
		Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		white.setColor(Color.WHITE);
		white.fill();
		pixel = new Texture(white);
		white.dispose();

		screenHeight = Gdx.graphics.getHeight();
		screenWidth = Gdx.graphics.getWidth();
		tileHeight = screenHeight / 7;
		tileWidth = screenWidth / Constants.TOTAL_TILE;
		hudHeight = screenHeight * 0.075f;
		textMargin = screenWidth * 0.03f;

		// Fonts are pre-baked bitmap fonts (FreeType is unavailable on web), scaled relative to the screen height.
		font = new BitmapFont(Gdx.files.internal("ui_font.fnt"));
		font.getData().setScale(screenHeight * 0.03f / Constants.FONT_BAKED_SIZE);
		font.setUseIntegerPositions(false);

		title = new BitmapFont(Gdx.files.internal("title_font.fnt"));
		title.getData().setScale(screenHeight * 0.1f / Constants.TITLE_FONT_BAKED_SIZE);
		title.setUseIntegerPositions(false);

		flareTemplate = loadEffect("flares.p");
		exhaustTemplate = loadEffect("exhaust.p");
		explosionTemplate = loadEffect("explosion.p");
		flares = new ParticleEffectPool(flareTemplate, 2, 4);
		enemyFlares = new ParticleEffectPool(flipped(flareTemplate), 2, 4);
		exhaust = new ParticleEffectPool(exhaustTemplate, 2, 4);
		enemyExhaust = new ParticleEffectPool(flipped(exhaustTemplate), 2, 4);
		explosions = new ParticleEffectPool(explosionTemplate, 2, 8);

		// Android: Back returns to the menu instead of closing the app; the menu handles it itself.
		Gdx.input.setCatchKey(Keys.BACK, true);
		setScreen(new MainMenuScreen(this, null));
	}

	private ParticleEffect loadEffect(String name) {
		ParticleEffect effect = new ParticleEffect();
		effect.load(Gdx.files.internal("particles/" + name), Gdx.files.internal("particles"));
		effect.scaleEffect(screenHeight / PARTICLE_DESIGN_HEIGHT);
		return effect;
	}

	/** A copy of the effect mirrored vertically. It shares the template's textures. */
	private static ParticleEffect flipped(ParticleEffect template) {
		ParticleEffect effect = new ParticleEffect(template);
		effect.flipY();
		return effect;
	}

	/** Draws the background scaled to cover the screen without distortion, cropping whichever edges overflow. */
	void drawBackground() {
		float scale = Math.max(screenWidth / background.getWidth(), screenHeight / background.getHeight());
		float width = background.getWidth() * scale, height = background.getHeight() * scale;
		batch.draw(background, (screenWidth - width) / 2, (screenHeight - height) / 2, width, height);
	}

	/** Draws the background and the player's row of empty tiles. */
	void drawField() {
		drawBackground();
		for (int i = 0; i < Constants.TOTAL_TILE; i++) {
			batch.draw(launchPad, i * tileWidth, 0, tileWidth, tileHeight);
		}
	}

	/** Draws a translucent dark panel, used behind longer text so it stays readable over the background. */
	void drawPanel(float x, float y, float width, float height) {
		batch.setColor(0, 0, 0, 0.55f);
		batch.draw(pixel, x, y, width, height);
		batch.setColor(Color.WHITE);
	}

	/** Draws a menu button with its label centred; {@code highlighted} brightens it, e.g. under the mouse. */
	void drawButton(String label, Rectangle bounds, boolean highlighted) {
		float shade = highlighted ? 1f : 0.8f;
		button.setColor(buttonShade.set(shade, shade, shade, 1f));
		button.draw(batch, bounds.x, bounds.y, bounds.width, bounds.height);
		layout.setText(font, label, Color.WHITE, bounds.width, Align.center, false);
		font.draw(batch, layout, bounds.x, bounds.y + (bounds.height + layout.height) / 2);
	}

	/** Draws a single line of text within [x, x + width], vertically centred on {@code centerY}. */
	void drawTextCentredOn(BitmapFont font, String text, Color color, float x, float width, int align, float centerY) {
		layout.setText(font, text, color, width, align, false);
		font.draw(batch, layout, x, centerY + layout.height / 2);
	}

	/** Draws wrapped text inside the screen margins, with its top edge at {@code y}, and returns its height. */
	float drawText(BitmapFont font, String text, Color color, int align, float y) {
		layout.setText(font, text, color, screenWidth - 2 * textMargin, align, true);
		font.draw(batch, layout, textMargin, y);
		return layout.height;
	}

	float drawText(String text, int align, float y) {
		return drawText(font, text, Color.WHITE, align, y);
	}

	void drawCenteredText(String text, float y) {
		drawText(text, Align.center, y);
	}

	@Override
	public void dispose() {
		super.dispose();
		if (getScreen() != null) {
			getScreen().dispose();
		}
		background.dispose();
		launchPad.dispose();
		launcher.dispose();
		missile.dispose();
		flareDispenser.dispose();
		buttonTexture.dispose();
		healthBarTexture.dispose();
		pixel.dispose();
		batch.dispose();
		font.dispose();
		title.dispose();
		// Only the loaded templates own the particle textures; pooled and flipped copies share them.
		flareTemplate.dispose();
		exhaustTemplate.dispose();
		explosionTemplate.dispose();
	}
}
