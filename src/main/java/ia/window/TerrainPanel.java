package ia.window;

import static java.lang.Math.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
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

import ia.agent.Agent;
import ia.terrain.Position;
import ia.terrain.Terrain;

@SuppressWarnings("serial")
public class TerrainPanel extends JPanel {

	private final Terrain terrain;
	private final Supplier<Drawer> drawerSupplier;

	public TerrainPanel(Terrain terrain, Supplier<Drawer> drawerSupplier) {
		this.terrain = terrain;
		this.drawerSupplier = drawerSupplier;
	}

	// TODO Remove
	@Deprecated
	public TerrainPanel(Terrain terrain) {
		this(terrain, null);
	}

	@Override
	public void paint(Graphics graphics) {
		paint(DrawContext.create(terrain, this, graphics).atComponent());
	}

	protected void paint(DrawContext ctx) {
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
				ctx.graphics.fillRect(0, 0, bounds.width, bounds.height);
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
	}

	public static enum PointerRenderer {
		THIN_SQUARE(drawerFactory -> drawerFactory.thinSquareDrawer(Color.BLACK)), //
		THICK_SQUARE(drawerFactory -> drawerFactory.thickSquareDrawer(Color.BLACK, 10)), //
		TARGET(drawerFactory -> drawerFactory.targetDrawer(Color.BLACK, 5)),//
		;

		private final Function<DrawerFactory, BiConsumer<Integer, Integer>> resolver;

		private PointerRenderer(Function<DrawerFactory, BiConsumer<Integer, Integer>> resolver) {
			this.resolver = resolver;
		}

		BiConsumer<Integer, Integer> createDrawer(DrawerFactory drawerFactory) {
			return resolver.apply(drawerFactory);
		}
	}

	public static record Pointer(Position position, PointerRenderer renderer) {
	}

	// TODO Privatize this method
	protected void draw(DrawContext ctx, Drawer drawer) {
		drawer.draw(ctx);
	}

	record DrawContext(Graphics2D graphics, Terrain terrain, int componentWidth, int componentHeight, double cellWidth,
			double cellHeight) {

		public static DrawContext create(Terrain terrain, JComponent component, Graphics graphics) {
			Graphics2D graphics2D = (Graphics2D) graphics;
			Rectangle componentBounds = component.getBounds();
			// TODO Scale with panel bounds, but don't draw out of graphics
			int componentWidth = componentBounds.width;
			int componentHeight = componentBounds.height;
			double cellWidth = (double) componentWidth / terrain.width();
			double cellHeight = (double) componentHeight / terrain.height();
			return new DrawContext(//
					graphics2D, terrain, //
					componentWidth, componentHeight, //
					cellWidth, cellHeight//
			);
		}

		public DrawContext onGraphics(Graphics2D graphics2D) {
			return new DrawContext(graphics2D, terrain, componentWidth, componentHeight, cellWidth, cellHeight);
		}

		public DrawContext atComponent() {
			return onGraphics((Graphics2D) graphics.create(0, 0, componentWidth, componentWidth));
		}

		public DrawContext atCell(Position position) {
			int x = (int) (position.x * cellWidth);
			int y = (int) (position.y * cellHeight);
			return onGraphics((Graphics2D) graphics.create(x, y, (int) cellWidth, (int) cellHeight));
		}
	}

	static class DrawerFactory {
		private final DrawContext ctx;

		public DrawerFactory(DrawContext ctx) {
			this.ctx = ctx;
		}

		public BiConsumer<Integer, Integer> thinSquareDrawer(Color color) {
			return (x, y) -> {
				ctx.graphics().setColor(color);
				ctx.graphics().drawRect(x, y, (int) ctx.cellWidth(), (int) ctx.cellHeight());
			};
		}

		public BiConsumer<Integer, Integer> thickSquareDrawer(Color color, int radius) {
			return (x, y) -> {
				for (int i = 0; i < radius; i++) {
					Color rectColor = new Color(color.getRed(), color.getGreen(), color.getBlue(),
							255 * (radius - i) / radius);
					ctx.graphics().setColor(rectColor);
					ctx.graphics().drawRect(x - i, y - i, (int) ctx.cellWidth() + 2 * i,
							(int) ctx.cellHeight() + 2 * i);
				}
			};
		}

		public BiConsumer<Integer, Integer> targetDrawer(Color color, int extraRadius) {
			double cellSize = max(ctx.cellWidth(), ctx.cellHeight());
			int diameter = (int) round(hypot(cellSize, cellSize)) + 2 * extraRadius;
			int midWidth = (int) round(ctx.cellWidth() / 2);
			int midHeight = (int) round(ctx.cellHeight() / 2);
			return (x, y) -> {
				int centerX = x + midWidth;
				int centerY = y + midHeight;
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
}
