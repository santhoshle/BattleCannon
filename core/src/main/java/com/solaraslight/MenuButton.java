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
import com.badlogic.gdx.math.Rectangle;

/** A labelled, clickable rectangle drawn with the game's button skin. */
class MenuButton {
	final String label;
	final Rectangle bounds;

	/** Creates a button horizontally centred on screen, with its centre at {@code centerY}. */
	MenuButton(CanyonSalvo game, String label, float centerY) {
		this.label = label;
		float width = game.screenWidth * 0.6f, height = game.screenHeight * 0.085f;
		bounds = new Rectangle((game.screenWidth - width) / 2, centerY - height / 2, width, height);
	}

	/** Whether the pointer is over the button. Input y grows downwards, so it is flipped to match drawing. */
	boolean isHovered(CanyonSalvo game) {
		return bounds.contains(Gdx.input.getX(), game.screenHeight - Gdx.input.getY());
	}

	boolean isClicked(CanyonSalvo game) {
		return Gdx.input.justTouched() && isHovered(game);
	}

	void draw(CanyonSalvo game) {
		game.drawButton(label, bounds, isHovered(game));
	}
}
