package fr.vergne.denn.window;

import java.awt.Component;
import java.awt.Graphics;

import javax.swing.Icon;

public class NoIcon implements Icon {
	@Override
	public int getIconWidth() {
		return 0;
	}

	@Override
	public int getIconHeight() {
		return 0;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		// Nothing to paint
	}
}
