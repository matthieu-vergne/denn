package ia.window;

import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Collection;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.function.Function;

import ia.terrain.Position;
import ia.terrain.Terrain;

public class MouseOnTerrain {
	private Collection<Consumer<Position>> moveListeners = new LinkedList<>();

	public void listenMove(Consumer<Position> listener) {
		moveListeners.add(listener);
	}

	public void moveTo(Position position) {
		for (Consumer<Position> listener : moveListeners) {
			listener.accept(position);
		}
	}

	private Collection<Consumer<Position>> clickListeners = new LinkedList<>();

	public void listenClick(Consumer<Position> listener) {
		clickListeners.add(listener);
	}

	public void clickAt(Position position) {
		for (Consumer<Position> listener : clickListeners) {
			listener.accept(position);
		}
	}

	private Collection<Runnable> exitListeners = new LinkedList<>();

	public void listenExit(Runnable listener) {
		exitListeners.add(listener);
	}

	public void exit() {
		for (Runnable listener : exitListeners) {
			listener.run();
		}
	}

	static interface TerrainPanelListener extends MouseListener, MouseMotionListener {
	}

	public TerrainPanelListener terrainPanelListener(Terrain terrain, TerrainPanel terrainPanel) {
		Function<Point, Position> positioner = point -> Position.at(//
				point.x * terrain.width() / terrainPanel.getWidth(), //
				point.y * terrain.height() / terrainPanel.getHeight()//
		);
		return new TerrainPanelListener() {

			@Override
			public void mouseEntered(MouseEvent event) {
				moveTo(positioner.apply(event.getPoint()));
			}

			@Override
			public void mouseExited(MouseEvent event) {
				exit();
			}

			@Override
			public void mouseMoved(MouseEvent event) {
				moveTo(positioner.apply(event.getPoint()));
			}

			@Override
			public void mousePressed(MouseEvent event) {
				// Nothing to do
			}

			@Override
			public void mouseDragged(MouseEvent event) {
				// Nothing to do
			}

			@Override
			public void mouseReleased(MouseEvent event) {
				// Nothing to do
			}

			@Override
			public void mouseClicked(MouseEvent event) {
				clickAt(positioner.apply(event.getPoint()));
			}
		};
	}
}
