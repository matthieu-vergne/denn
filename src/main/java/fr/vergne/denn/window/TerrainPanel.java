package fr.vergne.denn.window;

import static fr.vergne.denn.window.TerrainPanel.DrawerFactory.*;
import static java.lang.Math.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.JPanel;

import fr.vergne.denn.Measure;
import fr.vergne.denn.Measure.Feeder;
import fr.vergne.denn.agent.Agent;
import fr.vergne.denn.terrain.Terrain;
import fr.vergne.denn.utils.Position;
import fr.vergne.denn.utils.Position.Bounds;

// TODO Allow interactions to modify the agents
@SuppressWarnings("serial")
public class TerrainPanel extends JPanel {

	private final Terrain terrain;
	private final Supplier<Drawer> drawerSupplier;

	public TerrainPanel(Terrain terrain, Supplier<Drawer> drawerSupplier) {
		this.terrain = terrain;
		this.drawerSupplier = drawerSupplier;
	}

	@Override
	public boolean isOpaque() {
		return true;
	}

	@Override
	public boolean isOptimizedDrawingEnabled() {
		return true;
	}

	public Position.Conversion pixelToTerrain() {
		Position minPixel = Position.ORIGIN;
		Position maxPixel = Position.at(this.getWidth() - 1, this.getHeight() - 1);
		Position.Bounds pixelBounds = Position.Bounds.between(minPixel, maxPixel);
		return Position.Conversion.createFromBounds(pixelBounds, terrain.bounds());
	}

	public void repaint(Position.Bounds bounds) {
		bounds.allPositions().forEach(this::repaint);
	}

	public void repaint(Position position) {
		repaint(cellBounds(position, pixelToTerrain().reverse()).paintable().rectangle());
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		paint(DrawContext.create(terrain, this, graphics).atComponent());
	}

	private void paint(DrawContext ctx) {
		draw(ctx, drawerSupplier.get());
	}

	public static interface Drawer {
		void draw(DrawContext ctx);

		default Drawer then(Drawer that) {
			return ctx -> {
				this.draw(ctx);
				that.draw(ctx);
			};
		}

		public static Drawer forEachPosition(Stream<Position> positions, Function<Position, Drawer> drawerFactory) {
			return ctx -> {
				positions.forEach(position -> {
					Drawer cellDrawer = drawerFactory.apply(position);
					DrawContext cellCtx = ctx.atCell(position);
					cellDrawer.draw(cellCtx);
				});
			};
		}

		public static Drawer forEachAgent(Stream<Agent> agents, Function<Agent, Drawer> drawerFactory) {
			return ctx -> {
				agents.forEach(agent -> {
					Drawer cellDrawer = drawerFactory.apply(agent);
					DrawContext cellCtx = ctx.atCell(ctx.terrain().getAgentPosition(agent));
					cellDrawer.draw(cellCtx);
				});
			};
		}

		public static Drawer filler(Color color) {
			return ctx -> {
				Rectangle bounds = ctx.graphics.getClipBounds();
				ctx.graphics.setColor(color);
				ctx.graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
			};
		}

		public static Collector<Drawer, ?, Drawer> toCompositeDrawer() {
			Collector<Drawer, ?, Drawer> collector = new Collector<Drawer, List<Drawer>, Drawer>() {

				@Override
				public Supplier<List<Drawer>> supplier() {
					return LinkedList<Drawer>::new;
				}

				@Override
				public BiConsumer<List<Drawer>, Drawer> accumulator() {
					return (list, drawer) -> list.add(drawer);
				}

				@Override
				public BinaryOperator<List<Drawer>> combiner() {
					return (list1, list2) -> {
						list1.addAll(list2);
						return list1;
					};
				}

				@Override
				public Function<List<Drawer>, Drawer> finisher() {
					return list -> ctx -> list.forEach(drawer -> drawer.draw(ctx));
				}

				@Override
				public Set<Characteristics> characteristics() {
					return Collections.emptySet();
				}
			};
			return collector;
		}

		public static Feeder<Drawer, Duration> measureDuration() {
			return Measure.of((drawer, collector) -> {
				return ctx -> {
					Instant start = Instant.now();
					drawer.draw(ctx);
					collector.collect(Duration.between(start, Instant.now()));
				};
			});
		}
	}

	public static enum PointerRenderer {
		THIN_SQUARE(position -> thinSquareDrawer(Color.BLACK, position), 0), //
		THICK_SQUARE(position -> thickSquareDrawer(Color.BLACK, 10, position), 0), //
		TARGET(position -> targetDrawer(Color.BLACK, 5, position), 1),//
		;

		private final Function<Position, Drawer> resolver;
		private final int radiusFix;

		private PointerRenderer(Function<Position, Drawer> resolver, int radiusFix) {
			this.resolver = resolver;
			this.radiusFix = radiusFix;
		}

		Drawer createDrawer(Position position) {
			return resolver.apply(position);
		}

		/**
		 * Drawing often occurs with single pixel strokes, but not always. With thicker
		 * strokes, we may draw a bigger shape. For instance, a stroke of 3 pixels draws
		 * 1 extra pixel out of the requested surface. This method returns this extra
		 * pixels that might be drawn due to these thicker strokes.
		 * 
		 * @return the radius fix to apply
		 */
		// TODO Retrieve stroke thickness from the drawing process
		int extraStroke() {
			return radiusFix;
		}
	}

	public static record Pointer(Position position, PointerRenderer renderer) {

		public Drawer createDrawer() {
			return renderer().createDrawer(position());
		}
	}

	private void draw(DrawContext ctx, Drawer drawer) {
		drawer.draw(ctx);
	}

	record DrawContext(Graphics2D graphics, Terrain terrain, Position.Bounds componentBounds) {

		public Position.Conversion pixelToTerrain() {
			return Position.Conversion.createFromBounds(componentBounds, terrain.bounds());
		}

		public Position.Conversion terrainToPixel() {
			return Position.Conversion.createFromBounds(terrain.bounds(), componentBounds);
		}

		public static DrawContext create(Terrain terrain, JComponent component, Graphics graphics) {
			Graphics2D graphics2D = (Graphics2D) graphics;
			Position minPixel = Position.at(0, 0);
			Position maxPixel = Position.at(component.getWidth() - 1, component.getHeight() - 1);
			return new DrawContext(graphics2D, terrain, Position.Bounds.between(minPixel, maxPixel));
		}

		public DrawContext onGraphics(Graphics2D graphics2D) {
			return new DrawContext(graphics2D, terrain, componentBounds);
		}

		public DrawContext atComponent() {
			Bounds paintBounds = componentBounds.paintable();
			return onGraphics((Graphics2D) graphics.create(0, 0, paintBounds.width(), paintBounds.height()));
		}

		public DrawContext atCell(Position position) {
			Bounds paintBounds = cellBounds(position, this.terrainToPixel()).paintable();
			return onGraphics((Graphics2D) graphics.create(paintBounds.min().x(), paintBounds.min().y(),
					paintBounds.width(), paintBounds.height()));
		}
	}

	static class DrawerFactory {

		public static Drawer thinSquareDrawer(Color color, Position position) {
			return ctx -> {
				Position.Bounds cell = cellBounds(position, ctx.terrainToPixel());
				ctx.graphics().setColor(color);
				ctx.graphics().drawRect(cell.min().x(), cell.min().y(), cell.width(), cell.height());
			};
		}

		public static Drawer thickSquareDrawer(Color color, int borderSize, Position position) {
			return ctx -> {
				Position.Bounds cell = cellBounds(position, ctx.terrainToPixel());
				for (int i = 0; i < borderSize; i++) {
					Color rectColor = new Color(color.getRed(), color.getGreen(), color.getBlue(),
							255 * (borderSize - i) / borderSize);
					ctx.graphics().setColor(rectColor);
					ctx.graphics().drawRect(cell.min().x() - i, cell.min().y() - i, cell.width() + 2 * i,
							cell.height() + 2 * i);
				}
			};
		}

		public static Drawer targetDrawer(Color color, int extraRadius, Position position) {
			return ctx -> {
				Position.Conversion terrainToPixel = ctx.terrainToPixel();
				Bounds cell = cellBounds(position, terrainToPixel);
				double cellSize = max(cell.width(), cell.height());
				int diameter = (int) round(hypot(cellSize, cellSize)) + 2 * extraRadius;
				int midWidth = (int) round(cell.width() / 2);
				int midHeight = (int) round(cell.height() / 2);
				Position pixel = terrainToPixel.convert(position);
				int centerX = pixel.x() + midWidth;
				int centerY = pixel.y() + midHeight;
				int minX = centerX - diameter / 2;
				int maxX = minX + diameter;
				int minY = centerY - diameter / 2;
				int maxY = minY + diameter;
				ctx.graphics().setColor(color);
				ctx.graphics().setStroke(new BasicStroke(3));
				ctx.graphics().drawOval(minX, minY, diameter, diameter);
				ctx.graphics().setStroke(new BasicStroke(1));
				ctx.graphics().drawLine(minX, centerY, maxX, centerY);
				ctx.graphics().drawLine(centerX, minY, centerX, maxY);
			};
		}
	}
	
	private static Position.Bounds cellBounds(Position positionOnTerrain, Position.Conversion terrainToPixel) {
		Position topLeft = terrainToPixel.convert(positionOnTerrain);
		Position bottomRight = terrainToPixel.convert(positionOnTerrain.move(1, 1)).move(-1, -1);
		return Position.Bounds.between(topLeft, bottomRight);
	}
}
