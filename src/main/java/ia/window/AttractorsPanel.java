package ia.window;

import static ia.window.TerrainPanel.Drawer.*;
import static java.lang.Math.*;
import static javax.swing.SwingUtilities.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
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
	private final List<Consumer<Double>> progressListeners;
	private final Consumer<Program> tasker;
	private final Runnable computerStopper;

	private AttractorsPanel(Terrain terrain, Supplier<Drawer> drawerSupplier, List<Consumer<Double>> progressListeners,
			Consumer<Program> tasker, Runnable computerStopper) {
		super(terrain, drawerSupplier);
		this.terrain = terrain;
		this.progressListeners = progressListeners;
		this.tasker = tasker;
		this.computerStopper = computerStopper;
	}

	@Override
	public Dimension getPreferredSize() {
		int parentWidth = Utils.parentAvailableDimension(this).width;
		return new Dimension(parentWidth, parentWidth * terrain.height() / terrain.width());
	}
	
	public void startComputingAttractors(Program program) {
		tasker.accept(program);
	}

	public void stopComputingAttractors() {
		computerStopper.run();
	}

	public void listenComputingProgress(Consumer<Double> listener) {
		progressListeners.add(listener);
	}

	record Settings(//
			Supplier<Integer> maxStartPositions, //
			Supplier<Integer> maxRunsPerStartPosition, //
			Supplier<Integer> maxIterationsPerRun, //
			Supplier<Integer> runAutoStopThreshold, //
			Supplier<ColorFocus> colorFocus//
	) {
	}

	public static AttractorsPanel on(Terrain terrain, NeuralNetwork.Factory networkFactory, Settings settings) {
		class Context {
			AttractorsPanel attractorsPanel;
			Function<Position, Color> attractorColorizer = position -> {
				throw new IllegalStateException("Should not be called");
			};
			boolean shouldBeComputing = false;
			public Predicate<Position> hasAttractor;
		}
		Context ctx = new Context();

		Drawer backgroundDrawer = filler(Color.WHITE);
		Supplier<Drawer> drawerSupplier = () -> dCtx -> {
			Position.Bounds componentClipBounds = Position.Bounds.from(dCtx.graphics().getClipBounds());
			Position.Bounds terrainClipBounds = dCtx.pixelToTerrain().convert(componentClipBounds);
			Stream<Position> terrainClipPositions = terrainClipBounds.allPositions();
			// TODO Redraw all on scale update (min/max)
			// TODO Optimize large surface drawing
			// TODO Extract processing out of Swing context
			Drawer requestedDrawer = Drawer.forEachPosition(terrainClipPositions, position -> {
				if (ctx.hasAttractor.test(position)) {
					Color color = ctx.attractorColorizer.apply(position);
					return backgroundDrawer.then(filler(color));
				} else {
					return backgroundDrawer;
				}
			});
			requestedDrawer.draw(dCtx);
		};
		List<Consumer<Double>> progressListeners = new LinkedList<>();
		Consumer<Program> tasker = program -> {
			int maxStartPositions = settings.maxStartPositions.get();
			int maxRunsPerStartPosition = settings.maxRunsPerStartPosition.get();
			int maxIterationsPerRun = settings.maxIterationsPerRun.get();
			int runAutoStopThreshold = settings.runAutoStopThreshold.get();
			ColorFocus colorFocus = settings.colorFocus.get();

			JobsContext context = createMapContext(terrain, Color.RED, colorFocus);

			ctx.hasAttractor = context.hasAttractor;
			ctx.attractorColorizer = context.attractorColorizer;

			Jobs jobs = createComputingContext(ctx.attractorsPanel, terrain, networkFactory, program, maxStartPositions,
					maxRunsPerStartPosition, maxIterationsPerRun, context, () -> ctx.shouldBeComputing,
					progressListeners, runAutoStopThreshold);

			ctx.shouldBeComputing = true;
			invokeLater(jobs.firstJob());
		};
		Runnable computerStopper = () -> ctx.shouldBeComputing = false;
		ctx.attractorsPanel = new AttractorsPanel(terrain, drawerSupplier, progressListeners, tasker, computerStopper);
		return ctx.attractorsPanel;
	}

	private static final Runnable UNDEFINED_RUNNABLE = () -> {
		throw new IllegalStateException("Undefined runnable");
	};

	private static class JobsContext {
		Iterator<Position> startPositionIterator;
		Consumer<Position> countIncrementer;
		Function<Position, Color> attractorColorizer;
		Map<Position, Integer> counts;
		public Terrain terrain;
		public Predicate<Position> hasAttractor;
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
		public Position previousPosition;
		public int noMoveCount;

		Runnable firstJob() {
			return prepareIterations;
		}
	}

	private static JobsContext createMapContext(Terrain terrain, Color colorRef, ColorFocus colorFocus) {
		JobsContext ctx = new JobsContext();

		ctx.terrain = terrain;

		ctx.counts = new HashMap<>();

		Function<Position, Integer> countReader = position -> {
			return ctx.counts.computeIfAbsent(position, p -> 0);
		};

		Supplier<Integer> minReader = colorFocus.resolve(ctx);
		int[] max = { 0 };
		ctx.countIncrementer = position -> {
			int count = ctx.counts.computeIfAbsent(position, p -> 0);
			count++;
			max[0] = max(max[0], count);
			ctx.counts.put(position, count);
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
				if (position.x() < maxPosition.x()) {
					next = position.move(1, 0);
				} else if (position.y() < maxPosition.y()) {
					next = position.move(-position.x(), 1);
				} else {
					next = null;
				}
				return position;
			}
		};
		List<Position> startPositions = new LinkedList<>();
		while (ctx.startPositionIterator.hasNext()) {
			startPositions.add(ctx.startPositionIterator.next());
		}
		Collections.shuffle(startPositions);
		ctx.startPositionIterator = startPositions.iterator();

		ctx.hasAttractor = ctx.counts::containsKey;
		ctx.attractorColorizer = position -> {
			int count = countReader.apply(position);
			int min = minReader.get();
			if (min == max[0]) {
				return colorRef;
			} else {
				return countToColor(colorRef, max[0] - min, count - min);
			}
		};

		return ctx;
	}

	private static Color countToColor(Color colorRef, int max, int count) {
		int opacity = 255 * count / max;
		return new Color(colorRef.getRed(), colorRef.getGreen(), colorRef.getBlue(), opacity);
	}

	private static Jobs createComputingContext(AttractorsPanel attractorsPanel, Terrain terrain,
			NeuralNetwork.Factory networkFactory, Program program, int maxStartPositions, int maxRunsPerStartPosition,
			int maxIterationsPerRun, JobsContext jCtx, Supplier<Boolean> computingSemaphore,
			List<Consumer<Double>> progressListeners, int runAutoStopThreshold) {
		int maxIterations = maxStartPositions * maxRunsPerStartPosition * maxIterationsPerRun;

		Jobs jobs = new Jobs();
		jobs.startPosition = jCtx.startPositionIterator.next();
		jobs.prepareIterations = stopIfRequested(computingSemaphore, () -> {
			jobs.clone = Agent.createFromProgram(networkFactory, program);
			terrain.placeAgent(jobs.clone, jobs.startPosition);
			jobs.previousPosition = jobs.startPosition;
			jobs.noMoveCount = 0;
			invokeLater(jobs.iterate);
		});
		Button.Action iterationAction = TerrainInteractor.moveAgents().on(terrain);
		jobs.iterate = stopIfRequested(computingSemaphore, () -> {
			iterationAction.execute();

			Position position = terrain.getAgentPosition(jobs.clone);
			if (position.equals(jobs.previousPosition)) {
				jobs.noMoveCount++;
			} else {
				jobs.previousPosition = position;
				jobs.noMoveCount = 0;
			}

			if (jobs.noMoveCount == runAutoStopThreshold) {
				jobs.totalIterations += maxIterationsPerRun - jobs.iteration;
				jobs.iteration = maxIterationsPerRun;
			} else {
				jobs.totalIterations++;
				jobs.iteration++;
			}

			if (jobs.iteration >= maxIterationsPerRun) {
				invokeLater(jobs.nextRun);
			} else {
				invokeLater(jobs.iterate);
			}
		});
		jobs.nextRun = stopIfRequested(computingSemaphore, () -> {
			Position lastPosition = terrain.removeAgent(jobs.clone);
			jCtx.countIncrementer.accept(lastPosition);
			for (Consumer<Double> listener : progressListeners) {
				listener.accept((double) jobs.totalIterations / maxIterations);
			}
			if (attractorsPanel.isVisible()) {
				// TODO if min/max updated, repaint all
				attractorsPanel.repaint(lastPosition);
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
			if (!jCtx.startPositionIterator.hasNext()) {
				for (Consumer<Double> listener : progressListeners) {
					listener.accept(1.0);
				}
				return;
			} else {
				jobs.startPosition = jCtx.startPositionIterator.next();
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

	public static enum ColorFocus {
		ZERO_MAX(jCtx -> () -> 0), //
		MIN_MAX(jCtx -> () -> {
			int terrainSize = jCtx.terrain.width() * jCtx.terrain.height();
			if (jCtx.counts.size() < terrainSize) {
				return 0;
			} else {
				return jCtx.counts.values().stream().mapToInt(i -> i).min().getAsInt();
			}
		}),///
		;

		private final Function<JobsContext, Supplier<Integer>> resolver;

		private ColorFocus(Function<JobsContext, Supplier<Integer>> resolver) {
			this.resolver = resolver;
		}

		Supplier<Integer> resolve(JobsContext ctx) {
			return resolver.apply(ctx);
		}
	}
}
