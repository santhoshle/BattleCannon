package com.santhoshle;

import java.util.Random;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;

public class CFMainMenu extends ApplicationAdapter {
	SpriteBatch screen;
	Texture background, cannon, aiCannon, bullet, shield, mount;
	float screenWidth, screenHeight;
	float blockWeight, blockHeight;

	int playerHealth, aiHealth;

	Rectangle bulletCollider, aiBulletCollider, shieldCollider, aiShieldCollider;
	Rectangle playerBorder, aiBorder;

	int cannonX, shieldX, aiCannonX, aiShieldX, state;
	boolean destroyBullet, destroyAIBullet;

	float bulletY, aiBulletY, velocity;
	BitmapFont font, title;
	GlyphLayout layout = new GlyphLayout();

	@Override
	public void create() {
		screen = new SpriteBatch();
		background = new Texture("background.jpg");
		cannon = new Texture("Cannon.png");
		aiCannon = new Texture("AICannon.png");
		bullet = new Texture("bullet.png");
		shield = new Texture("shield.png");
		mount = new Texture("mount.png");

		screenHeight = Gdx.graphics.getHeight();
		screenWidth = Gdx.graphics.getWidth();
		blockHeight = Gdx.graphics.getHeight() / 7;
		blockWeight = Gdx.graphics.getWidth() / Constants.TOTAL_TILE;

		bulletCollider = new Rectangle();
		aiBulletCollider = new Rectangle();
		shieldCollider = new Rectangle();
		aiShieldCollider = new Rectangle();
		playerBorder = new Rectangle();
		aiBorder = new Rectangle();

		velocity = screenHeight / 120f;

		FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("Ricasso.ttf"));
		FreeTypeFontParameter parameter = new FreeTypeFontParameter();
		parameter.size = (int) (14f * Gdx.graphics.getDensity());
		font = generator.generateFont(parameter);
		font.setColor(Color.RED);

		FreeTypeFontGenerator titleGenerator = new FreeTypeFontGenerator(Gdx.files.internal("Soul_Calibur.ttf"));
		FreeTypeFontParameter titleParameter = new FreeTypeFontParameter();
		titleParameter.size = (int) (72f * Gdx.graphics.getDensity());
		title = titleGenerator.generateFont(titleParameter);
		title.setColor(Color.BROWN);

		initializeGame();
	}

	@Override
	public void render() {
		screen.begin();
		screen.draw(background, 0, 0, screenWidth, screenHeight);

		float bx = 0, by = 0, bw = blockWeight, bh = blockHeight;
		float ox = 0, oy = screenHeight - blockHeight, ow = blockWeight, oh = blockHeight;

		playerBorder.set(bx, -2, screenWidth, 2);
		aiBorder.set(ox, screenHeight, screenWidth, 2);

		/** Template remove from **/
		/** Template remove end **/

		if (state == Constants.STATE_START_ACTION) {
			layout.setText(font, "You : " + playerHealth, Color.RED, screenWidth, Align.left, true);
			font.draw(screen, layout, 0, screenHeight * 0.2f);

			layout.setText(font, "Enemy : " + aiHealth, Color.RED, screenWidth, Align.right, true);
			font.draw(screen, layout, 0, screenHeight * 0.8f);
		}

		if (state <= Constants.STATE_PLACE_CANNON) {
			if (aiHealth == 0 && playerHealth == 0) {
				layout.setText(font, "It is a draw", Color.RED, screenWidth, Align.center, true);
				font.draw(screen, layout, 0, screenHeight * 0.7f);
				initializeGame();
			} else if (aiHealth == 0) {
				layout.setText(font, "You Won", Color.RED, screenWidth, Align.center, true);
				font.draw(screen, layout, 0, screenHeight * 0.7f);
				initializeGame();
			} else if (playerHealth == 0) {
				layout.setText(font, "You Lose", Color.RED, screenWidth, Align.center, true);
				font.draw(screen, layout, 0, screenHeight * 0.7f);
				initializeGame();
			}
		}

		if (state == Constants.STATE_PLACE_AI) {
			System.out.println("placed ai");
			aiCannonX = getRandomNumberInRange();
			aiShieldX = getRandomNumberInRange();
			state = 4;
		}

		if (state == Constants.STATE_START_GAME) {
			layout.setText(title, "Battle Cannon", Color.BROWN, screenWidth, Align.center, true);
			title.draw(screen, layout, 0, screenHeight * 0.75f);

            layout.setText(font, "Click to start the Game", Color.RED, screenWidth, Align.center, true);
			font.draw(screen, layout, 0, screenHeight * 0.5f);
		}

		for (int i = 0; i < Constants.TOTAL_TILE; i++) {
			screen.draw(mount, bx, by, bw, bh);

			if (state >= Constants.STATE_GAP) {
				System.out.println("intial");
				screen.draw(mount, ox, oy, ow, oh);
				if (aiCannonX == i) {
					screen.draw(aiCannon, ox, oy, ow, oh);
					if (state == Constants.STATE_START_ACTION && !destroyAIBullet) {
						screen.draw(bullet, ox, aiBulletY, ow, oh);
						aiBulletCollider.set(ox, aiBulletY, ow, oh);
						aiBulletY -= velocity;
					}
				}
				if (aiShieldX == i) {
					screen.draw(shield, ox, oy, ow, oh);
					aiShieldCollider.set(ox, oy, ow, oh);
				}
				state = 5;
			}

			if (state > Constants.STATE_PLACE_CANNON && cannonX == i) {
				screen.draw(cannon, bx, by, bw, bh);
				if (state == Constants.STATE_START_ACTION && !destroyBullet) {
					screen.draw(bullet, bx, bulletY, bw, bh);
					bulletCollider.set(bx, bulletY, bw, bh);
					bulletY += velocity;
				}
			}

			if (state > Constants.STATE_PLACE_SHIELD && shieldX == i) {
				screen.draw(shield, bx, by, bw, bh);
				shieldCollider.set(bx, by, bw, bh);
			}

			// Increment
			bx += bw;
			ox += bw;
		}
		
		if (state == Constants.STATE_PLACE_SHIELD) {
            layout.setText(font, "Place the shield in squared area", Color.RED, screenWidth, Align.center, true);
			font.draw(screen, layout, 0, screenHeight * 0.2f);
		} else if (state == Constants.STATE_PLACE_CANNON) {
            layout.setText(font, "Place the cannon in squared area", Color.RED, screenWidth, Align.center, true);
			font.draw(screen, layout, 0, screenHeight * 0.2f);			
		}

		if (Gdx.input.justTouched()) {
			if (state == Constants.STATE_PLACE_SHIELD) {
				shieldX = (int) (Gdx.input.getX() / blockWeight);
				state = 3;
			} else if (state == Constants.STATE_PLACE_CANNON) {
				cannonX = (int) (Gdx.input.getX() / blockWeight);
				state = 2;
			} else if (state == Constants.STATE_START_GAME) {
				state = 1;
			}
		}

		if (state == Constants.STATE_START_ACTION) {
			if (!destroyBullet) {
				if (Intersector.overlaps(aiShieldCollider, bulletCollider)) {
					System.out.println("Destroy bullet code");
					destroyBullet = true;
				}
				if (Intersector.overlaps(bulletCollider, aiBorder)) {
					aiHealth -= Constants.SINGLE_SHOT_DAMAGE;
					destroyBullet = true;
					System.out.println("Hit player");
				}
			}

			if (!destroyAIBullet) {
				if (Intersector.overlaps(shieldCollider, aiBulletCollider)) {
					destroyAIBullet = true;
					System.out.println("Destroy ai bullet code");
				}

				if (Intersector.overlaps(aiBulletCollider, playerBorder)) {
					playerHealth -= Constants.SINGLE_SHOT_DAMAGE;
					destroyAIBullet = true;
					System.out.println("Hit ai");
				}
			}

			if (destroyBullet && destroyAIBullet) {
				System.out.println("aiHealth" + aiHealth);
				System.out.println("playerHealth " + playerHealth);
				initializeTurn();
				System.out.println("state " + state);
			}
		}

		screen.end();
	}

	@Override
	public void dispose() {
		background.dispose();
		cannon.dispose();
		aiCannon.dispose();
		bullet.dispose();
		shield.dispose();
		screen.dispose();
		font.dispose();
	}

	private int getRandomNumberInRange() {
		Random r = new Random();
		return r.nextInt((Constants.TOTAL_TILE - 1) + 1) + 0;
	}

	private void initializeTurn() {
		state = 1;
		cannonX = 0;
		shieldX = 0;
		aiCannonX = 0;
		aiShieldX = 0;
		bulletY = blockHeight;
		aiBulletY = screenHeight - (2 * blockHeight);
		destroyBullet = false;
		destroyAIBullet = false;
	}

	private void initializeGame() {
		initializeTurn();
		state = 0;
		playerHealth = 100;
		aiHealth = 100;
	}

	private void delay() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
