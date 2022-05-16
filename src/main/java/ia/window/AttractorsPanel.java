package ia.window;

import static ia.window.TerrainPanel.Drawer.*;
import static java.lang.Math.*;
import static javax.swing.SwingUtilities.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.border.Border;

import ia.agent.Agent;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Program;
import ia.terrain.Position;
import ia.terrain.Terrain;
import ia.terrain.TerrainInteractor;

// TODO Simplify
@SuppressWarnings("serial")
public class AttractorsPanel extends TerrainPanel {

	private final Terrain terrain;
	private final Color colorRef = Color.RED;
	private final List<Consumer<Double>> progressListeners = new LinkedList<>();
	private boolean shouldBeComputing = false;

	private Supplier<Stream<Position>> attractorFilterPositions;
	private Function<Position, Color> attractorFilter;
	private final Supplier<Drawer> drawerSupplier;
	private final Consumer<Program> tasker;

	public static AttractorsPanel on(Terrain terrain, NeuralNetwork.Factory networkFactory) {
		return new AttractorsPanel(terrain, networkFactory);
	}

	// TODO Move to static factory method
	// TODO Use super(terrain, drawerSupplier)
	private AttractorsPanel(Terrain terrain, NeuralNetwork.Factory networkFactory) {
		super(terrain);
		this.terrain = terrain;
		this.attractorFilterPositions = () -> Stream.empty();
		this.attractorFilter = position -> {
			throw new IllegalStateException("Should not be called");
		};
		this.drawerSupplier = () -> {
			// TODO Optimize when drawing all surface
			// TODO Draw only last updates if max unchanged
			Drawer backgroundDrawer = filler(Color.WHITE);
			Drawer attractorDrawer = Drawer.forEachPosition(attractorFilterPositions.get(),
					attractorFilter.andThen(Drawer::filler));
			return backgroundDrawer.then(attractorDrawer);
		};
		this.tasker = program -> {
			int maxStartPositions = terrain.width() * terrain.height();
			int maxRunsPerStartPosition = 1;
			int maxIterationsPerRun = terrain.width() + terrain.height();

			// TODO Replace by Map<Position, Integer>?
			JobsContext context = createListContext(terrain, colorRef, maxStartPositions);

			this.attractorFilterPositions = context.attractorFilterPositions;
			this.attractorFilter = context.attractorFilter;

			Jobs jobs = createComputingContext(this, terrain, networkFactory, program, maxStartPositions,
					maxRunsPerStartPosition, maxIterationsPerRun, context, () -> shouldBeComputing, progressListeners);

			this.shouldBeComputing = true;
			invokeLater(jobs.firstJob());
		};
	}

	@Override
	public Dimension getPreferredSize() {
		int parentWidth = parentAvailableWidth(this);
		return new Dimension(parentWidth, parentWidth * terrain.height() / terrain.width());
	}

	// FIXME remove
	@Deprecated
	@Override
	protected void paint(DrawContext ctx) {
		draw(ctx, drawerSupplier.get());
	}

	public void startComputingAttractors(Program program) {
		tasker.accept(program);
	}

	public void stopComputingAttractors() {
		this.shouldBeComputing = false;
	}

	public void listenComputingProgress(Consumer<Double> listener) {
		progressListeners.add(listener);
	}

	private static final Runnable UNDEFINED_RUNNABLE = () -> {
		throw new IllegalStateException("Undefined runnable");
	};

	private static class JobsContext {
		Iterator<Position> startPositionIterator;
		Consumer<Position> countIncrementer;
		Supplier<Stream<Position>> attractorFilterPositions;
		Function<Position, Color> attractorFilter;
	}

	private static class Jobs {
		Agent clone;
		int iteration = 0;
		int totalIterations = 0;
		int runIndex = 0;
		Position startPosition = null;
		Runnable prepareIterations = UNDEFINED_RUNNABLE;
		Runnable iterate = UNDEFINED_RUNNABLE;
		Runnable nextRun = UNDEFINED_RUNNABLE;
		Runnable nextStartPosition = UNDEFINED_RUNNABLE;

		Runnable firstJob() {
			return prepareIterations;
		}
	}

	private static JobsContext createMapContext(Terrain terrain, Color colorRef, int maxStartPositions) {
		Map<Position, Integer> counts = new HashMap<>();
		Function<Position, Integer> countReader = position -> {
			return counts.computeIfAbsent(position, p -> 0);
		};

		JobsContext ctx = new JobsContext();
		int[] max = { 0 };
		ctx.countIncrementer = position -> {
			int count = counts.computeIfAbsent(position, p -> 0);
			count++;
			max[0] = max(max[0], count);
			counts.put(position, count);
		};
		ctx.startPositionIterator = new Iterator<Position>() {

			Position minPosition = terrain.minPosition();
			Position maxPosition = terrain.maxPosition();
			Position next = minPosition;

			@Override
			public boolean hasNext() {
				return next != null;
			}

			@Override
			public Position next() {
				if (next == null) {
					throw new NoSuchElementException("");
				}
				Position position = next;
				if (position.x < maxPosition.x) {
					next = position.move(1, 0);
				} else if (position.y < maxPosition.y) {
					next = position.move(-position.x, 1);
				} else {
					next = null;
				}
				return position;
			}
		};

		ctx.attractorFilterPositions = () -> {
			return counts.entrySet().stream()//
					.filter(entry -> entry.getValue() > 0)//
					.map(entry -> entry.getKey());
		};
		ctx.attractorFilter = position -> {
			int count = countReader.apply(position);
			int opacity = 255 * count / max[0];
			if (opacity > 0) {
				System.out.println("Draw " + position + " with " + count + " at " + opacity);
			}
			return new Color(colorRef.getRed(), colorRef.getGreen(), colorRef.getBlue(), opacity);
		};

		return ctx;
	}

	private static JobsContext createListContext(Terrain terrain, Color colorRef, int maxStartPositions) {
		List<Integer> counts = new ArrayList<Integer>(maxStartPositions);
		IntStream.range(0, maxStartPositions).forEach(i -> counts.add(0));
		Function<Position, Integer> indexer = position -> {
			return position.x + position.y * terrain.width();
		};
		Function<Integer, Position> desindexer = index -> {
			return Position.at(index % terrain.width(), index / terrain.width());
		};
		Function<Position, Integer> countReader = position -> {
			return counts.get(indexer.apply(position));
		};

		JobsContext ctx = new JobsContext();
		int[] max = { 0 };
		ctx.countIncrementer = position -> {
			Integer index = indexer.apply(position);
			int count = counts.get(index);
			count++;
			max[0] = max(max[0], count);
			counts.set(index, count);
		};
		int[] startPositionIndex = { 0 };
		ctx.startPositionIterator = new Iterator<Position>() {

			@Override
			public boolean hasNext() {
				return startPositionIndex[0] < maxStartPositions;
			}

			@Override
			public Position next() {
				return desindexer.apply(startPositionIndex[0]++);
			}
		};

		ctx.attractorFilterPositions = () -> {
			int[] index = { -1 };
			return counts.stream().map(count -> {
				index[0]++;
				return count > 0 ? desindexer.apply(index[0]) : null;
			}).filter(position -> {
				return position != null;
			});
		};
		ctx.attractorFilter = position -> {
			int count = countReader.apply(position);
			int opacity = 255 * count / max[0];
			if (opacity > 0) {
				System.out.println("Draw " + position + " with " + count + " at " + opacity);
			}
			return new Color(colorRef.getRed(), colorRef.getGreen(), colorRef.getBlue(), opacity);
		};

		return ctx;
	}

	private static Jobs createComputingContext(AttractorsPanel attractorsPanel, Terrain terrain,
			NeuralNetwork.Factory networkFactory, Program program, int maxStartPositions, int maxRunsPerStartPosition,
			int maxIterationsPerRun, JobsContext jobsContext, Supplier<Boolean> computingSemaphore,
			List<Consumer<Double>> progressListeners) {
		int maxIterations = maxStartPositions * maxRunsPerStartPosition * maxIterationsPerRun;

		Jobs jobs = new Jobs();
		jobs.startPosition = jobsContext.startPositionIterator.next();
		jobs.prepareIterations = stopIfRequested(computingSemaphore, () -> {
			System.out.print(jobs.startPosition + "x" + jobs.runIndex);
			jobs.clone = Agent.createFromProgram(networkFactory, program);
			terrain.placeAgent(jobs.clone, jobs.startPosition);
			invokeLater(jobs.iterate);
		});
		Button.Action iterationAction = TerrainInteractor.moveAgents().on(terrain);
		jobs.iterate = stopIfRequested(computingSemaphore, () -> {
			iterationAction.execute();
			jobs.totalIterations++;
			jobs.iteration++;
			if (jobs.iteration >= maxIterationsPerRun) {
				invokeLater(jobs.nextRun);
			} else {
				invokeLater(jobs.iterate);
			}
		});
		jobs.nextRun = stopIfRequested(computingSemaphore, () -> {
			Position lastPosition = terrain.removeAgent(jobs.clone);
			jobsContext.countIncrementer.accept(lastPosition);
			System.out.println(" = " + lastPosition + " (" + (100 * jobs.totalIterations / maxIterations) + "%)");
			for (Consumer<Double> listener : progressListeners) {
				listener.accept((double) jobs.totalIterations / maxIterations);
			}
			if (attractorsPanel.isVisible()) {
				attractorsPanel.repaint();
			}
			jobs.runIndex++;
			jobs.iteration = 0;
			if (jobs.runIndex >= maxRunsPerStartPosition) {
				invokeLater(jobs.nextStartPosition);
			} else {
				invokeLater(jobs.prepareIterations);
			}
		});
		jobs.nextStartPosition = stopIfRequested(computingSemaphore, () -> {
			jobs.runIndex = 0;
			if (!jobsContext.startPositionIterator.hasNext()) {
				for (Consumer<Double> listener : progressListeners) {
					listener.accept(1.0);
				}
				return;
			} else {
				jobs.startPosition = jobsContext.startPositionIterator.next();
				invokeLater(jobs.prepareIterations);
			}
		});
		return jobs;
	}

	private static Runnable stopIfRequested(Supplier<Boolean> shouldBeComputing, Runnable runnable) {
		return () -> {
			if (!shouldBeComputing.get()) {
				return;
			}
			runnable.run();
		};
	}

	private static int parentAvailableWidth(JComponent component) {
		JComponent parent = (JComponent) component.getParent();
		Border border = parent.getBorder();
		Insets insets = border == null ? new Insets(0, 0, 0, 0) : border.getBorderInsets(parent);
		return parent.getBounds().width - insets.left - insets.right;
	}
}
