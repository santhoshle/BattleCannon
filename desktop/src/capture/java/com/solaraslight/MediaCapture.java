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
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Plays one scripted match of the real game and records it, for the itch.io page. Not shipped with the game.
 * <p>
 * Clicks are injected by replacing {@link Gdx#input} each frame, and every frame advances by a fixed time step, so the
 * output doesn't depend on how fast the machine renders. The enemy is the real {@link EnemyAI} with its own seeded
 * random generator, so the match can be planned ahead and replays identically.
 * <p>
 * Arguments: the media folder (screenshots, cover, icon) and the folder for the numbered video frames.
 */
public class MediaCapture {
	static final int WIDTH = 720, HEIGHT = 900;
	/** Video frame rate; each rendered frame advances the game by exactly this much time. */
	static final int FPS = 30;

	public static void main(String[] args) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setTitle("Canyon Salvo - capturing media");
		config.setWindowedMode(WIDTH, HEIGHT);
		config.setResizable(false);
		config.useVsync(false);
		config.setForegroundFPS(0);
		config.setIdleFPS(0);
		new Lwjgl3Application(new ScriptedGame(new FileHandle(args[0]), new FileHandle(args[1])), config);
	}

	/** One turn of the planned match: both sides' launcher and flare columns. */
	static class Turn {
		final int cannon, shield, aiCannon, aiShield;

		Turn(int cannon, int shield, int aiCannon, int aiShield) {
			this.cannon = cannon;
			this.shield = shield;
			this.aiCannon = aiCannon;
			this.aiShield = aiShield;
		}

		boolean playerHits() {
			return cannon != aiShield;
		}

		boolean aiHits() {
			return aiCannon != shield;
		}

		@Override
		public String toString() {
			return "you " + cannon + "/" + shield + (playerHits() ? " hit" : " decoyed") + ", enemy " + aiCannon + "/"
					+ aiShield + (aiHits() ? " hit" : " decoyed");
		}
	}

	/** The real enemy AI, drawing from its own generator instead of the one the particle effects share. */
	static class SeededEnemyAI extends EnemyAI {
		private final RandomXS128 random;

		SeededEnemyAI(long seed) {
			random = new RandomXS128(seed);
		}

		@Override
		public int chooseShieldTile() {
			return withOwnRandom(super::chooseShieldTile);
		}

		@Override
		public int chooseCannonTile() {
			return withOwnRandom(super::chooseCannonTile);
		}

		private int withOwnRandom(IntSupplier choice) {
			Random shared = MathUtils.random;
			MathUtils.random = random;
			try {
				return choice.getAsInt();
			} finally {
				MathUtils.random = shared;
			}
		}
	}

	/**
	 * Finds a seed whose match makes a good showcase: the player wins a close game of about seven turns, and both
	 * sides decoy some missiles. Runs the same rules as {@link GameScreen}, without drawing anything.
	 */
	static long planMatch(List<Turn> turns) {
		for (long seed = 1; ; seed++) {
			turns.clear();
			RandomXS128 player = new RandomXS128(seed * 7919);
			EnemyAI ai = new SeededEnemyAI(seed);
			int health = Constants.TOTAL_HEALTH, aiHealth = Constants.TOTAL_HEALTH;
			int playerDecoyed = 0, aiDecoyed = 0;
			while (health > 0 && aiHealth > 0) {
				int cannon = player.nextInt(Constants.TOTAL_TILE);
				// Launcher and flares on different pads, so both are easy to see.
				int shield = (cannon + 1 + player.nextInt(Constants.TOTAL_TILE - 1)) % Constants.TOTAL_TILE;
				int aiCannon = ai.chooseCannonTile(), aiShield = ai.chooseShieldTile();
				Turn turn = new Turn(cannon, shield, aiCannon, aiShield);
				turns.add(turn);
				if (turn.playerHits()) aiHealth -= Constants.SINGLE_SHOT_DAMAGE;
				else playerDecoyed++;
				if (turn.aiHits()) health -= Constants.SINGLE_SHOT_DAMAGE;
				else aiDecoyed++;
				ai.observe(cannon, shield);
			}
			boolean firstTurnBothHit = turns.get(0).playerHits() && turns.get(0).aiHits();
			if (aiHealth <= 0 && health > 0 && health <= 40 && turns.size() == 7 && playerDecoyed >= 2 && aiDecoyed >= 2
					&& firstTurnBothHit) {
				return seed;
			}
		}
	}

	static class ScriptedGame extends CanyonSalvo {
		private static final float DT = 1f / FPS;
		/** Seconds a click's ripple stays visible. */
		private static final float RIPPLE_TIME = 0.4f;

		private final FileHandle mediaDir, framesDir;
		private final List<Turn> turns = new ArrayList<>();
		private long seed;

		private Input fakeInput;
		/** Pointer position in input coordinates: y grows downwards. */
		private float pointerX, pointerY;
		private boolean clicked;
		private float rippleTimer = RIPPLE_TIME;

		private final Array<Action> script = new Array<>();
		private boolean recording;
		private int frame;
		private String pendingSnap;
		private ShapeRenderer shapes;

		ScriptedGame(FileHandle mediaDir, FileHandle framesDir) {
			this.mediaDir = mediaDir;
			this.framesDir = framesDir;
		}

		@Override
		public void create() {
			super.create();
			Input realInput = Gdx.input;
			fakeInput = (Input) Proxy.newProxyInstance(Input.class.getClassLoader(), new Class<?>[] { Input.class },
					(proxy, method, args) -> {
						switch (method.getName()) {
						case "getX":
							return (int) pointerX;
						case "getY":
							return (int) pointerY;
						case "justTouched":
						case "isTouched":
						case "isButtonJustPressed":
						case "isButtonPressed":
							return clicked;
						case "isKeyJustPressed":
						case "isKeyPressed":
							return false;
						default:
							return method.invoke(realInput, args);
						}
					});
			shapes = new ShapeRenderer();
			framesDir.mkdirs();
			framesDir.emptyDirectory();
			mediaDir.child("screenshots").mkdirs();
			mediaDir.child("screenshots").emptyDirectory();

			seed = planMatch(turns);
			Gdx.app.log("capture", "Match seed " + seed);
			for (int i = 0; i < turns.size(); i++) {
				Gdx.app.log("capture", "Turn " + (i + 1) + ": " + turns.get(i));
			}
			Gdx.app.log("capture", "Back buffer " + Gdx.graphics.getBackBufferWidth() + "x"
					+ Gdx.graphics.getBackBufferHeight());
			buildScript();
		}

		// ---- Script ----

		private interface Action {
			/** Runs for one frame and returns whether it has finished. */
			boolean act(float delta);
		}

		private Action instant(Runnable runnable) {
			return delta -> {
				runnable.run();
				return true;
			};
		}

		private Action waitFor(float seconds) {
			float[] left = { seconds };
			return delta -> (left[0] -= delta) <= 0;
		}

		private Action waitUntil(BooleanSupplier condition) {
			return delta -> condition.getAsBoolean();
		}

		/** Glides the pointer to (x, y), in input coordinates. */
		private Action moveTo(float x, float y, float seconds) {
			float[] state = { 0, 0, 0 }; // elapsed, start x, start y
			boolean[] started = { false };
			return delta -> {
				if (!started[0]) {
					started[0] = true;
					state[1] = pointerX;
					state[2] = pointerY;
				}
				state[0] += delta;
				float t = seconds <= 0 ? 1 : Math.min(1, state[0] / seconds);
				pointerX = Interpolation.smooth.apply(state[1], x, t);
				pointerY = Interpolation.smooth.apply(state[2], y, t);
				return t >= 1;
			};
		}

		private Action click() {
			return instant(() -> {
				clicked = true;
				rippleTimer = 0;
			});
		}

		private Action snap(String name) {
			return instant(() -> pendingSnap = name);
		}

		private float padX(int tile) {
			return (tile + 0.5f) * tileWidth;
		}

		private float padY() {
			return screenHeight - tileHeight / 2;
		}

		/** Input y of a menu button whose centre sits at {@code fraction} of the screen height, measured upwards. */
		private float buttonY(float fraction) {
			return screenHeight * (1 - fraction);
		}

		private boolean phaseIs(String phase) {
			Screen screen = getScreen();
			return screen instanceof GameScreen && get(screen, "phase").toString().equals(phase);
		}

		private boolean flag(String name) {
			return getScreen() instanceof GameScreen && (Boolean) get(getScreen(), name);
		}

		private void buildScript() {
			float centerX = screenWidth / 2;

			// How to Play, for a screenshot only; it's skipped in the video.
			script.add(moveTo(centerX, buttonY(0.41f), 0));
			script.add(click());
			script.add(waitFor(0.4f));
			script.add(snap("02-how-to-play"));
			script.add(moveTo(centerX, buttonY(0.09f), 0));
			script.add(click());
			script.add(waitFor(0.1f));

			// The video: menu, then one full match.
			script.add(moveTo(screenWidth * 0.78f, screenHeight * 0.8f, 0));
			script.add(instant(() -> recording = true));
			script.add(waitFor(1f));
			script.add(moveTo(centerX + screenWidth * 0.12f, buttonY(0.52f), 0.7f));
			script.add(waitFor(0.3f));
			script.add(snap("01-main-menu"));
			script.add(click());
			script.add(waitUntil(() -> getScreen() instanceof GameScreen));
			script.add(instant(() -> set(getScreen(), "enemyAI", new SeededEnemyAI(seed))));

			// Screenshot turns: the first decoy, and a later turn where one missile is decoyed while the other is still
			// on its way in. (Once both land the turn ends and the board clears, so that's the moment to catch.)
			int decoyTurn = -1, interceptTurn = -1;
			for (int i = 0; i < turns.size(); i++) {
				Turn turn = turns.get(i);
				if (turn.playerHits() != turn.aiHits()) {
					if (decoyTurn < 0) decoyTurn = i;
					else interceptTurn = i;
				}
			}

			for (int i = 0; i < turns.size(); i++) {
				Turn turn = turns.get(i);
				script.add(waitFor(0.25f));
				script.add(moveTo(padX(turn.cannon), padY(), 0.55f));
				script.add(waitFor(0.15f));
				script.add(click());
				script.add(waitFor(0.35f));
				if (i == 0) script.add(snap("03-place-flares"));
				script.add(moveTo(padX(turn.shield), padY(), 0.5f));
				script.add(waitFor(0.15f));
				script.add(click());
				// Let the game handle the click before checking flags, which still hold last turn's values until then.
				script.add(waitFor(0.05f));
				if (i == 0) {
					script.add(waitFor(0.55f));
					script.add(snap("04-missiles-in-flight"));
				}
				if (i == decoyTurn) {
					script.add(waitUntil(() -> flag("playerFlaresFired") || flag("aiFlaresFired")));
					script.add(waitFor(0.25f));
					script.add(snap("05-flares"));
				}
				if (i == interceptTurn) {
					script.add(waitUntil(() -> flag("missileDone") || flag("aiMissileDone")));
					script.add(waitFor(0.08f));
					script.add(snap("06-intercept"));
				}
				script.add(waitUntil(() -> !phaseIs("FIRING")));
			}

			script.add(waitUntil(() -> getScreen() instanceof MainMenuScreen));
			script.add(moveTo(screenWidth * 0.8f, screenHeight * 0.25f, 0.6f));
			script.add(waitFor(0.4f));
			script.add(snap("07-victory"));
			script.add(waitFor(1.6f));
			script.add(instant(() -> recording = false));
		}

		// ---- Frame loop ----

		@Override
		public void render() {
			// The backend restores its own input before every frame.
			Gdx.input = fakeInput;
			clicked = false;
			while (script.notEmpty() && script.first().act(DT)) {
				script.removeIndex(0);
			}
			if (script.isEmpty()) {
				CoverArt.renderAll(this, mediaDir);
				Gdx.app.log("capture", "Saved " + frame + " video frames to " + framesDir.path());
				Gdx.app.exit();
				return;
			}

			getScreen().render(DT);
			if (pendingSnap != null) {
				saveFrame(mediaDir.child("screenshots/" + pendingSnap + ".png"), 6);
				pendingSnap = null;
			}
			drawPointer();
			if (recording) {
				saveFrame(framesDir.child(String.format("frame_%05d.png", frame++)), 1);
			}
		}

		/** A touch-style pointer, so viewers can see where each click lands. Not drawn in screenshots. */
		private void drawPointer() {
			rippleTimer += DT;
			float x = pointerX, y = screenHeight - pointerY, radius = screenHeight * 0.018f;
			Gdx.gl.glEnable(GL20.GL_BLEND);
			Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
			shapes.setProjectionMatrix(batch.getProjectionMatrix());
			shapes.begin(ShapeRenderer.ShapeType.Filled);
			if (rippleTimer < RIPPLE_TIME) {
				float t = rippleTimer / RIPPLE_TIME;
				shapes.setColor(1, 1, 1, 0.5f * (1 - t));
				shapes.circle(x, y, radius * (1 + 2.2f * t), 32);
			}
			float pressed = rippleTimer < 0.12f ? 0.8f : 1f;
			shapes.setColor(0, 0, 0, 0.55f);
			shapes.circle(x, y, radius * 1.18f * pressed, 32);
			shapes.setColor(1, 1, 1, 0.9f);
			shapes.circle(x, y, radius * pressed, 32);
			shapes.end();
		}

		// ---- Helpers ----

		/** Saves whatever is currently bound for drawing (the screen or a frame buffer) as an opaque PNG. */
		static void saveFrame(FileHandle file, int width, int height, int compression) {
			byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true);
			// Blending leaves partial alpha in the frame buffer; screenshots must be fully opaque.
			for (int i = 3; i < pixels.length; i += 4) {
				pixels[i] = (byte) 255;
			}
			Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
			BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
			PixmapIO.writePNG(file, pixmap, compression, false);
			pixmap.dispose();
		}

		private void saveFrame(FileHandle file, int compression) {
			saveFrame(file, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight(), compression);
		}

		private static Field field(Object target, String name) {
			try {
				Field field = target.getClass().getDeclaredField(name);
				field.setAccessible(true);
				return field;
			} catch (NoSuchFieldException e) {
				throw new IllegalStateException("GameScreen no longer has a field named " + name, e);
			}
		}

		static Object get(Object target, String name) {
			try {
				return field(target, name).get(target);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}

		static void set(Object target, String name, Object value) {
			try {
				field(target, name).set(target, value);
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public void dispose() {
			shapes.dispose();
			super.dispose();
		}
	}
}
