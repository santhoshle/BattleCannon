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

import com.badlogic.gdx.math.MathUtils;

/**
 * Chooses the enemy's cannon and shield tiles by learning from where the player placed theirs in earlier turns.
 * It never sees the player's choices for the current turn, so both sides still place blind.
 */
public class EnemyAI {

	/** How much of the older history survives each turn; lower values make the AI adapt faster. */
	private static final float MEMORY = 0.7f;
	/** How strongly the history outweighs a uniform random pick. */
	private static final float CONFIDENCE = 3f;

	private final float[] playerCannonHistory = new float[Constants.TOTAL_TILE];
	private final float[] playerShieldHistory = new float[Constants.TOTAL_TILE];
	private final float[] weights = new float[Constants.TOTAL_TILE];

	/** Shield the column the player is most likely to fire from. */
	public int chooseShieldTile() {
		for (int i = 0; i < weights.length; i++) {
			weights[i] = 1 + CONFIDENCE * playerCannonHistory[i];
		}
		return pickWeighted();
	}

	/** Fire down the column the player is least likely to shield. */
	public int chooseCannonTile() {
		for (int i = 0; i < weights.length; i++) {
			weights[i] = 1 / (1 + CONFIDENCE * playerShieldHistory[i]);
		}
		return pickWeighted();
	}

	/** Records the player's placements once a turn has been resolved. */
	public void observe(int playerCannonTile, int playerShieldTile) {
		for (int i = 0; i < Constants.TOTAL_TILE; i++) {
			playerCannonHistory[i] *= MEMORY;
			playerShieldHistory[i] *= MEMORY;
		}
		playerCannonHistory[playerCannonTile] += 1;
		playerShieldHistory[playerShieldTile] += 1;
	}

	/** Picks a tile at random, with chances proportional to {@link #weights}, so the AI is never fully predictable. */
	private int pickWeighted() {
		float total = 0;
		for (float weight : weights) {
			total += weight;
		}
		float roll = MathUtils.random(total);
		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];
			if (roll <= 0) {
				return i;
			}
		}
		return weights.length - 1;
	}
}
