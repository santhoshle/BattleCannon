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

import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

/** Main menu, also shown after a game ends with that game's result. */
public class MainMenuScreen extends ScreenAdapter {

	private final CanyonSalvo game;
	private final String result;
	private final MenuButton play, howToPlay, quit;

	/** @param result outcome of the previous game, or null when there is none. */
	public MainMenuScreen(CanyonSalvo game, String result) {
		this.game = game;
		this.result = result;
		play = new MenuButton(game, result == null ? "Play" : "Play Again", game.screenHeight * 0.52f);
		howToPlay = new MenuButton(game, "How to Play", game.screenHeight * 0.41f);
		// A web page cannot close itself, so the browser build has no Quit button.
		quit = Gdx.app.getType() == ApplicationType.WebGL ? null : new MenuButton(game, "Quit", game.screenHeight * 0.30f);
	}

	@Override
	public void render(float delta) {
		game.batch.begin();
		game.drawBackground();

		game.drawText(game.title, "Canyon Salvo", Color.GOLD, Align.center, game.screenHeight * 0.92f);
		if (result != null) {
			game.drawText(game.font, result, Color.GOLD, Align.center, game.screenHeight * 0.68f);
		}
		play.draw(game);
		howToPlay.draw(game);
		if (quit != null) {
			quit.draw(game);
		}

		game.batch.end();

		if (play.isClicked(game)) {
			game.setScreen(new GameScreen(game));
		} else if (howToPlay.isClicked(game)) {
			game.setScreen(new HowToPlayScreen(game));
		} else if ((quit != null && quit.isClicked(game)) || Gdx.input.isKeyJustPressed(Keys.BACK)) {
			Gdx.app.exit();
		}
	}
}
