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
import com.badlogic.gdx.utils.Align;

/** Explains the rules, reached from the main menu. */
public class HowToPlayScreen extends ScreenAdapter {

	private static final String[] RULES = {
			"Each turn, tap a launch pad to place your missile launcher, then tap a pad for your flare dispenser.",
			"Both sides fire at the same time. Your missile flies straight up its column.",
			"If the enemy's flares guard that column, your missile is decoyed and destroyed. Otherwise it hits for "
					+ Constants.SINGLE_SHOT_DAMAGE + " damage.",
			"Guard the column you think the enemy will fire down. The enemy learns your habits, so mix it up!",
			"The first side to run out of health loses." };

	private final CanyonSalvo game;
	private final MenuButton back;

	public HowToPlayScreen(CanyonSalvo game) {
		this.game = game;
		back = new MenuButton(game, "Back", game.screenHeight * 0.09f);
	}

	@Override
	public void render(float delta) {
		float gap = game.screenHeight * 0.022f;
		float panelTop = game.screenHeight * 0.83f, panelBottom = game.screenHeight * 0.17f;

		game.batch.begin();
		game.drawBackground();
		game.drawPanel(0, panelBottom, game.screenWidth, panelTop - panelBottom);

		game.drawText(game.font, "How to Play", Color.GOLD, Align.center, game.screenHeight * 0.92f);
		float y = panelTop - gap;
		for (String rule : RULES) {
			y -= game.drawText(rule, Align.left, y) + gap;
		}
		back.draw(game);
		game.batch.end();

		if (back.isClicked(game) || Gdx.input.isKeyJustPressed(Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Keys.BACK)) {
			game.setScreen(new MainMenuScreen(game, null));
		}
	}
}
