package ia.window;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.function.BiConsumer;

import javax.swing.Icon;

public class ProgressIcon implements Icon {

	private int size;
	private double progress = 0;

	public ProgressIcon(int size) {
		this.size = size;
	}

	public void setProgress(double progress) {
		if (progress < 0 || progress > 1) {
			throw new IllegalArgumentException("Progress (" + progress + ") must be in [0;1]");
		}
		this.progress = progress;
	}

	@Override
	public int getIconWidth() {
		return size;
	}

	@Override
	public int getIconHeight() {
		return size;
	}

	@Override
	public void paintIcon(Component component, Graphics graphics, int x, int y) {
		Graphics2D graphics2d = (Graphics2D) graphics.create();

		drawProgress(graphics2d, x, y);
		if (progress < 1) {
			drawRunningMark(graphics2d, x, y);
		} else {
			drawCompletedMark(graphics2d, x, y);
		}

		graphics2d.dispose();
	}

	private void drawCompletedMark(Graphics2D graphics2d, int x, int y) {
		// Draw a check mark to show it is completed
		graphics2d.setColor(Color.GREEN);
		Polygon checkMark = new Polygon();
		BiConsumer<Integer, Integer> pointAdder = (a, b) -> {
			checkMark.addPoint(x + a * size / 8, y + b * size / 8);
		};
		pointAdder.accept(1, 4);
		pointAdder.accept(3, 7);
		pointAdder.accept(7, 2);
		pointAdder.accept(3, 5);
		graphics2d.fillPolygon(checkMark);
	}

	private void drawProgress(Graphics2D graphics2d, int x, int y) {
		// Fill the circle depending on the progress, may be slow
		graphics2d.setColor(Color.BLUE);
		graphics2d.fillArc(x, y, size - 1, size - 1, 90, (int) (-360 * progress));
	}

	private void drawRunningMark(Graphics2D graphics2d, int x, int y) {
		// Draw a moving circle to show it is still doing something
		graphics2d.setColor(Color.RED);
		double second = ((double) System.currentTimeMillis() / 1000) % 2;
		if (second > 1) {
			graphics2d.drawArc(x, y, size - 1, size - 1, 90, (int) (360 * (2 - second)));
		} else {
			graphics2d.drawArc(x, y, size - 1, size - 1, 90, (int) (-360 * second));
		}
	}
}
