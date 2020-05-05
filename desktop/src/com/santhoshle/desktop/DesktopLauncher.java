package com.santhoshle.desktop;

import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.santhoshle.CFMainMenu;

public class DesktopLauncher {
	public static void main (String[] arg) {
		LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
		config.title = "Cannon Fodder";
		config.width = 480;
		config.height = 600;
		new LwjglApplication(new CFMainMenu(), config);
	}
}
