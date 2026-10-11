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
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.ParticleEffectPool.PooledEffect;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;

/**
 * One game: each turn both sides place a missile launcher and a flare dispenser, then fire at the same time. A missile
 * that reaches the far row deals damage unless the opponent's flares are deployed in that column. The game ends when
 * either health runs out.
 */
public class GameScreen extends ScreenAdapter {

	private enum Phase {
		PLACE_CANNON, PLACE_SHIELD, FIRING, GAME_OVER
	}

	/** Launcher and missile heights as a fraction of the tile height. */
	private static final float LAUNCHER_SCALE = 0.95f, MISSILE_SCALE = 0.8f;
	/** Flare dispenser circle diameter as a fraction of the tile height. */
	private static final float DISPENSER_SCALE = 0.35f;
	/** How far in front of a row, in tiles, a dispenser spots a missile in its column and fires its flares. */
	private static final float FLARE_TRIGGER = 1.5f;
	/** How far in front of a row, in tiles, the flare cloud stops incoming missiles. */
	private static final float FLARE_REACH = 0.4f;
	/** Seconds the final explosions play before the result screen appears. */
	private static final float GAME_OVER_DELAY = 1.5f;

	private final CanyonSalvo game;
	private final EnemyAI enemyAI = new EnemyAI();

	/** Missile speed in pixels per second. */
	private final float velocity;
	private final float launcherWidth, launcherHeight, missileWidth, missileHeight;
	private final float aiRowY;

	private Phase phase;
	private float gameOverTimer;
	private String result;
	private int playerHealth = Constants.TOTAL_HEALTH, aiHealth = Constants.TOTAL_HEALTH;
	/** Health as shown on the bars; slides towards the real value so hits are easy to follow. */
	private float shownPlayerHealth = Constants.TOTAL_HEALTH, shownAiHealth = Constants.TOTAL_HEALTH;
	private final Color barColor = new Color();
	/** Width reserved for the "YOU" / "ENEMY" labels, measured from the wider one. */
	private final float hudLabelWidth;
	private int cannonX, shieldX, aiCannonX, aiShieldX;
	/** Bottom edge of each missile's sprite. */
	private float missileY, aiMissileY;
	private boolean missileDone, aiMissileDone, playerFlaresFired, aiFlaresFired;

	private PooledEffect playerExhaust, aiExhaust;
	/** Every running effect, including ones left to finish on their own after their owner is gone. */
	private final Array<PooledEffect> effects = new Array<>();

	public GameScreen(CanyonSalvo game) {
		this.game = game;
		// Matches the original per-frame speed of screenHeight / 120 at 60 FPS.
		velocity = game.screenHeight / 2f;
		// The enemy row sits just below the health bar strip.
		aiRowY = game.screenHeight - game.hudHeight - game.tileHeight;
		hudLabelWidth = new GlyphLayout(game.font, "ENEMY").width + game.screenWidth * 0.025f;
		launcherHeight = game.tileHeight * LAUNCHER_SCALE;
		launcherWidth = launcherHeight * game.launcher.getWidth() / game.launcher.getHeight();
		missileHeight = game.tileHeight * MISSILE_SCALE;
		missileWidth = missileHeight * game.missile.getWidth() / game.missile.getHeight();
		phase = Phase.PLACE_CANNON;
	}

	private float columnCenter(int tile) {
		return (tile + 0.5f) * game.tileWidth;
	}

	/** Dispensers sit at the front edge of their tile, facing the opponent, so they don't hide the launcher. */
	private float playerDispenserY() {
		return game.tileHeight * 0.8f;
	}

	private float aiDispenserY() {
		return aiRowY + game.tileHeight * 0.2f;
	}

	private PooledEffect startEffect(PooledEffect effect, float x, float y) {
		effect.setPosition(x, y);
		effect.start();
		effects.add(effect);
		return effect;
	}

	private void explode(float x, float y) {
		startEffect(game.explosions.obtain(), x, y);
	}

	private void startFiring() {
		aiCannonX = enemyAI.chooseCannonTile();
		aiShieldX = enemyAI.chooseShieldTile();

		missileY = game.tileHeight - missileHeight;
		aiMissileY = aiRowY;
		missileDone = false;
		aiMissileDone = false;
		playerFlaresFired = false;
		aiFlaresFired = false;

		playerExhaust = startEffect(game.exhaust.obtain(), columnCenter(cannonX), missileY);
		aiExhaust = startEffect(game.enemyExhaust.obtain(), columnCenter(aiCannonX), aiMissileY + missileHeight);
		phase = Phase.FIRING;
	}

	private void endTurn() {
		enemyAI.observe(cannonX, shieldX);

		if (playerHealth <= 0 || aiHealth <= 0) {
			if (playerHealth <= 0 && aiHealth <= 0) {
				result = "It is a draw";
			} else if (aiHealth <= 0) {
				result = "You Won";
			} else {
				result = "You Lose";
			}
			gameOverTimer = GAME_OVER_DELAY;
			phase = Phase.GAME_OVER;
		} else {
			phase = Phase.PLACE_CANNON;
		}
	}

	@Override
	public void render(float delta) {
		if (Gdx.input.isKeyJustPressed(Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Keys.BACK)) {
			game.setScreen(new MainMenuScreen(game, null));
			return;
		}
		handleInput();
		if (phase == Phase.FIRING) {
			updateMissiles(delta);
		} else if (phase == Phase.GAME_OVER) {
			gameOverTimer -= delta;
			if (gameOverTimer <= 0) {
				game.setScreen(new MainMenuScreen(game, result));
				return;
			}
		}
		draw(delta);
	}

	private void handleInput() {
		if (!Gdx.input.justTouched()) {
			return;
		}
		int tile = MathUtils.clamp((int) (Gdx.input.getX() / game.tileWidth), 0, Constants.TOTAL_TILE - 1);
		if (phase == Phase.PLACE_CANNON) {
			cannonX = tile;
			phase = Phase.PLACE_SHIELD;
		} else if (phase == Phase.PLACE_SHIELD) {
			shieldX = tile;
			startFiring();
		}
	}

	private void updateMissiles(float delta) {
		if (!missileDone) {
			missileY += velocity * delta;
			float noseY = missileY + missileHeight;
			playerExhaust.setPosition(columnCenter(cannonX), missileY);
			if (cannonX == aiShieldX && !aiFlaresFired && noseY >= aiRowY - FLARE_TRIGGER * game.tileHeight) {
				aiFlaresFired = true;
				startEffect(game.enemyFlares.obtain(), columnCenter(aiShieldX), aiDispenserY());
			}
			if (cannonX == aiShieldX && noseY >= aiRowY - FLARE_REACH * game.tileHeight) {
				missileDone = true;
			} else if (noseY >= aiRowY + game.tileHeight / 2) {
				aiHealth = Math.max(0, aiHealth - Constants.SINGLE_SHOT_DAMAGE);
				missileDone = true;
			}
			if (missileDone) {
				playerExhaust.allowCompletion();
				explode(columnCenter(cannonX), noseY);
			}
		}

		if (!aiMissileDone) {
			aiMissileY -= velocity * delta;
			aiExhaust.setPosition(columnCenter(aiCannonX), aiMissileY + missileHeight);
			if (aiCannonX == shieldX && !playerFlaresFired && aiMissileY <= game.tileHeight * (1 + FLARE_TRIGGER)) {
				playerFlaresFired = true;
				startEffect(game.flares.obtain(), columnCenter(shieldX), playerDispenserY());
			}
			if (aiCannonX == shieldX && aiMissileY <= game.tileHeight * (1 + FLARE_REACH)) {
				aiMissileDone = true;
			} else if (aiMissileY <= game.tileHeight / 2) {
				playerHealth = Math.max(0, playerHealth - Constants.SINGLE_SHOT_DAMAGE);
				aiMissileDone = true;
			}
			if (aiMissileDone) {
				aiExhaust.allowCompletion();
				explode(columnCenter(aiCannonX), aiMissileY);
			}
		}

		if (missileDone && aiMissileDone) {
			endTurn();
		}
	}

	private void draw(float delta) {
		game.batch.begin();
		game.drawField();

		boolean showEnemy = phase == Phase.FIRING || phase == Phase.GAME_OVER;
		if (showEnemy) {
			for (int i = 0; i < Constants.TOTAL_TILE; i++) {
				game.batch.draw(game.launchPad, i * game.tileWidth, aiRowY, game.tileWidth, game.tileHeight);
			}
			drawLauncher(aiCannonX, aiRowY, true);
		}
		if (phase != Phase.PLACE_CANNON) {
			drawLauncher(cannonX, 0, false);
		}
		if (showEnemy) {
			drawDispenser(shieldX, playerDispenserY());
			drawDispenser(aiShieldX, aiDispenserY());
		}

		// Flares, exhaust trails and explosions, drawn in the order they started; finished ones go back to their pool.
		for (int i = 0; i < effects.size; i++) {
			PooledEffect effect = effects.get(i);
			effect.draw(game.batch, delta);
			if (effect.isComplete()) {
				effect.free();
				effects.removeIndex(i--);
			}
		}

		if (phase == Phase.FIRING) {
			if (!missileDone) {
				drawMissile(cannonX, missileY, false);
			}
			if (!aiMissileDone) {
				drawMissile(aiCannonX, aiMissileY, true);
			}
		}

		if (phase == Phase.PLACE_CANNON) {
			game.drawCenteredText("Tap a launch pad to place your launcher", game.screenHeight * 0.3f);
		} else if (phase == Phase.PLACE_SHIELD) {
			game.drawCenteredText("Tap a launch pad to place your flares", game.screenHeight * 0.3f);
		}

		drawHud(delta);
		game.batch.end();
	}

	/** Health bars along the top: the player's on the left draining leftwards, the enemy's mirrored on the right. */
	private void drawHud(float delta) {
		float drain = Constants.TOTAL_HEALTH * 0.8f * delta;
		shownPlayerHealth = Math.max(playerHealth, shownPlayerHealth - drain);
		shownAiHealth = Math.max(aiHealth, shownAiHealth - drain);

		float top = game.screenHeight, hud = game.hudHeight, centerY = top - hud / 2;
		game.drawPanel(0, top - hud, game.screenWidth, hud);

		float margin = game.textMargin, gap = game.screenWidth * 0.04f;
		float labelWidth = hudLabelWidth, barHeight = hud * 0.5f;
		float barWidth = game.screenWidth / 2 - margin - labelWidth - gap / 2;
		game.drawTextCentredOn(game.font, "YOU", Color.WHITE, margin, labelWidth, Align.left, centerY);
		drawHealthBar(margin + labelWidth, centerY - barHeight / 2, barWidth, barHeight, playerHealth, shownPlayerHealth, false);
		float rightBarX = game.screenWidth / 2 + gap / 2;
		drawHealthBar(rightBarX, centerY - barHeight / 2, barWidth, barHeight, aiHealth, shownAiHealth, true);
		game.drawTextCentredOn(game.font, "ENEMY", Color.WHITE, rightBarX + barWidth, labelWidth, Align.right, centerY);
	}

	private void drawHealthBar(float x, float y, float width, float height, int health, float shown, boolean fromRight) {
		game.healthBar.draw(game.batch, x, y, width, height);

		float fraction = shown / Constants.TOTAL_HEALTH, inset = height * 0.18f;
		float innerWidth = width - 2 * inset, fillWidth = innerWidth * fraction;
		if (fillWidth > 0) {
			// Green when healthy, through yellow, to red when nearly destroyed.
			if (fraction > 0.5f) {
				barColor.set(1f, 0.82f, 0.1f, 1f).lerp(0.3f, 0.85f, 0.25f, 1f, (fraction - 0.5f) * 2);
			} else {
				barColor.set(0.9f, 0.2f, 0.12f, 1f).lerp(1f, 0.82f, 0.1f, 1f, fraction * 2);
			}
			float fillX = fromRight ? x + inset + innerWidth - fillWidth : x + inset;
			game.batch.setColor(barColor);
			game.batch.draw(game.pixel, fillX, y + inset, fillWidth, height - 2 * inset);
			// A light band along the top gives the fill a little depth.
			game.batch.setColor(1f, 1f, 1f, 0.25f);
			game.batch.draw(game.pixel, fillX, y + height * 0.55f, fillWidth, height * 0.45f - inset);
			game.batch.setColor(Color.WHITE);
		}

		float scale = game.font.getData().scaleX;
		game.font.getData().setScale(scale * 0.75f);
		game.drawTextCentredOn(game.font, String.valueOf(health), Color.WHITE, x, width, Align.center, y + height / 2);
		game.font.getData().setScale(scale);
	}

	/** Draws a launcher centred in its tile; the enemy's is mirrored to face down. */
	private void drawLauncher(int tile, float rowY, boolean enemy) {
		float x = columnCenter(tile) - launcherWidth / 2, y = rowY + (game.tileHeight - launcherHeight) / 2;
		game.batch.draw(game.launcher, x, y, launcherWidth, launcherHeight, 0, 0, game.launcher.getWidth(),
				game.launcher.getHeight(), false, enemy);
	}

	private void drawDispenser(int tile, float centerY) {
		float size = game.tileHeight * DISPENSER_SCALE;
		game.batch.draw(game.flareDispenser, columnCenter(tile) - size / 2, centerY - size / 2, size, size);
	}

	private void drawMissile(int tile, float y, boolean enemy) {
		float x = columnCenter(tile) - missileWidth / 2;
		game.batch.draw(game.missile, x, y, missileWidth, missileHeight, 0, 0, game.missile.getWidth(),
				game.missile.getHeight(), false, enemy);
	}

	@Override
	public void hide() {
		for (PooledEffect effect : effects) {
			effect.free();
		}
		effects.clear();
	}
}
