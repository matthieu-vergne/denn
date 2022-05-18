package ia.window;

import static ia.window.TerrainPanel.Drawer.*;
import static java.lang.Math.*;
import static java.util.stream.Collectors.*;
import static javax.swing.SwingUtilities.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.border.Border;

import ia.Measure;
import ia.Measure.AggregatingCollector;
import ia.Measure.Decorator;
import ia.agent.Agent;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Program;
import ia.terrain.Position;
import ia.terrain.Terrain;
import ia.terrain.TerrainInteractor;
import ia.window.TerrainPanel.Drawer;

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
		int parentWidth = parentAvailableWidth(this);
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

	public static AttractorsPanel on(Terrain terrain, NeuralNetwork.Factory networkFactory) {
		class Context {
			AttractorsPanel attractorsPanel;
			Supplier<Stream<Position>> attractorFilterPositions = () -> Stream.empty();
			Function<Position, Color> attractorFilter = position -> {
				throw new IllegalStateException("Should not be called");
			};
			boolean shouldBeComputing = false;
			public Map<Position, Integer> countsMap;
			public List<Integer> countsList;
			public AtomicReference<Collection<Position>> updated;
			public int max;
		}
		Context ctx = new Context();

		var backgroundCollector = Measure.Collector.ofDuration();
		var attractorsCollector = Measure.Collector.ofDuration();
		var feedBackgroundDuration = Drawer.measureDuration().feeding(backgroundCollector);
		var feedAttractorsDuration = Drawer.measureDuration().feeding(attractorsCollector);

		var computingDuration = Measure.of(AttractorsPanel.<Drawer>supplierDuration()).feeding(System.out::print);
		var drawingDuration = Drawer.measureDuration().feeding(System.out::println);
		List<AggregatingCollector<Duration>> collectors = List.of(backgroundCollector, attractorsCollector);
		Supplier<Boolean> shouldStop = () -> !ctx.shouldBeComputing;
		invokeLater(logDurations(shouldStop, collectors));
		Supplier<Drawer> drawerSupplier = new Supplier<Drawer>() {
			@Override
			public Drawer get() {
				// TODO Optimize when drawing large surface
				// TODO Draw only last updates if max unchanged
				Supplier<Drawer> computing1 = computingDuration.from(() -> legacy(ctx));
				Supplier<Drawer> computing2 = computingDuration.from(() -> layers(terrain, ctx));
				Supplier<Drawer> computing3 = computingDuration.from(() -> updates(terrain, ctx));
				return drawingDuration.from(computing3.get());
			}

			private Drawer updates(Terrain terrain, Context ctx) {
				// TODO Exploit JPanel.repaint(...) ?
				// TODO int max = ctx.max;
				// TODO Drawer backgroundDrawer = filler(Color.WHITE);
				Collection<Position> updated = ctx.updated.getAndSet(new HashSet<Position>());
				Drawer updatedDrawer = Drawer.forEachPosition(updated.stream(),
						ctx.attractorFilter.andThen(Drawer::filler));
				return updatedDrawer;
			}

			private Drawer layers(Terrain terrain, Context ctx) {
				int[] max = { 0 };
				Set<Position> remainingPositions = new HashSet<Position>(terrain.allPositions().collect(toList()));
				HashMap<Integer, Collection<Position>> layers = ctx.countsMap.entrySet().stream().collect(//
						HashMap<Integer, Collection<Position>>::new, //
						(map, entry) -> {
							Integer count = entry.getValue();
							max[0] = max(max[0], count);
							Position position = entry.getKey();
							remainingPositions.remove(position);
							map.computeIfAbsent(count, k -> new LinkedList<>()).add(position);
						}, //
						(map1, map2) -> map1.putAll(map2)//
				);
				Drawer emptyDrawer = Drawer.filler(Color.WHITE);
				Drawer emptySpace = Drawer.forEachPosition(remainingPositions.stream(), p -> emptyDrawer);
				return Stream.concat(Stream.of(emptySpace), layers.entrySet().stream()//
						.map(entry -> {
							Integer count = entry.getKey();
							Drawer drawer = Drawer.filler(countToColor(Color.RED, max[0], count));
							Collection<Position> positions = entry.getValue();
							return Drawer.forEachPosition(positions.stream(), p -> drawer);
						}))//
						.collect(toCompositeDrawer());
			}

			private Drawer legacy(Context ctx) {
				Drawer backgroundDrawer = filler(Color.WHITE);
				Drawer attractorsDrawer = Drawer.forEachPosition(ctx.attractorFilterPositions.get(),
						ctx.attractorFilter.andThen(Drawer::filler));
//				backgroundDrawer = feedBackgroundDuration.from(backgroundDrawer);
//				attractorsDrawer = feedAttractorsDuration.from(attractorsDrawer);
				return backgroundDrawer.then(attractorsDrawer);
			}
		};
		List<Consumer<Double>> progressListeners = new LinkedList<>();
		Consumer<Program> tasker = program -> {
			int maxStartPositions = terrain.width() * terrain.height();
			int maxRunsPerStartPosition = 1;
			int maxIterationsPerRun = terrain.width() + terrain.height();

			// TODO Replace by Map<Position, Integer>?
			JobsContext context = createMapContext(terrain, Color.RED, maxStartPositions);

			ctx.attractorFilterPositions = context.attractorFilterPositions;
			ctx.attractorFilter = context.attractorFilter;
			ctx.countsMap = context.countsMap;
			ctx.countsList = context.countsList;
			ctx.updated = context.updated;
			ctx.max = context.max;

			Jobs jobs = createComputingContext(ctx.attractorsPanel, terrain, networkFactory, program, maxStartPositions,
					maxRunsPerStartPosition, maxIterationsPerRun, context, () -> ctx.shouldBeComputing,
					progressListeners);

			ctx.shouldBeComputing = true;
			invokeLater(jobs.firstJob());
		};
		Runnable computerStopper = () -> ctx.shouldBeComputing = false;
		ctx.attractorsPanel = new AttractorsPanel(terrain, drawerSupplier, progressListeners, tasker, computerStopper);
		return ctx.attractorsPanel;
	}

	private static <T> Decorator<Supplier<T>, Duration> supplierDuration() {
		return (runnable, collector) -> {
			return () -> {
				Instant start = Instant.now();
				T drawer = runnable.get();
				collector.collect(Duration.between(start, Instant.now()));
				return drawer;
			};
		};
	}

	private static Runnable logDurations(Supplier<Boolean> shouldStop,
			List<AggregatingCollector<Duration>> collectors) {
		return new Runnable() {

			@Override
			public void run() {
				if (shouldStop.get()) {
					return;
				}
				long total = collectors.stream()//
						.map(AggregatingCollector<Duration>::value)//
						.mapToLong(Duration::toMillis)//
						.sum();
				if (total > 0) {
					System.out.println(collectors.stream()//
							.map(AggregatingCollector<Duration>::value)//
							.map(duration -> {
								long millis = duration.toMillis();
								long ratio = 100 * millis / total;
								return duration + " " + ratio + "%";
							})//
							.collect(Collectors.joining(" | ")));
				}
				invokeLater(this);
			}
		};
	}

	private static final Runnable UNDEFINED_RUNNABLE = () -> {
		throw new IllegalStateException("Undefined runnable");
	};

	private static class JobsContext {
		Iterator<Position> startPositionIterator;
		Consumer<Position> countIncrementer;
		Supplier<Stream<Position>> attractorFilterPositions;
		Function<Position, Color> attractorFilter;
		Map<Position, Integer> countsMap;
		public List<Integer> countsList;
		public int max;
		public AtomicReference<Collection<Position>> updated;
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
		ctx.max = 0;
		ctx.updated = new AtomicReference<>(new HashSet<>());
		ctx.countIncrementer = position -> {
			int count = counts.computeIfAbsent(position, p -> 0);
			count++;
			ctx.max = max(ctx.max, count);
			ctx.updated.updateAndGet(positions -> {
				positions.add(position);
				return positions;
			});
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
		ctx.countsMap = counts;
		ctx.attractorFilter = position -> {
			int count = countReader.apply(position);
			return countToColor(colorRef, ctx.max, count);
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
		ctx.countsList = counts;
		ctx.attractorFilter = position -> {
			int count = countReader.apply(position);
			return countToColor(colorRef, max[0], count);
		};

		return ctx;
	}

	private static Color countToColor(Color colorRef, int max, int count) {
		int opacity = 255 * count / max;
		return new Color(colorRef.getRed(), colorRef.getGreen(), colorRef.getBlue(), opacity);
	}

	private static Jobs createComputingContext(AttractorsPanel attractorsPanel, Terrain terrain,
			NeuralNetwork.Factory networkFactory, Program program, int maxStartPositions, int maxRunsPerStartPosition,
			int maxIterationsPerRun, JobsContext jobsContext, Supplier<Boolean> computingSemaphore,
			List<Consumer<Double>> progressListeners) {
		int maxIterations = maxStartPositions * maxRunsPerStartPosition * maxIterationsPerRun;

		Jobs jobs = new Jobs();
		jobs.startPosition = jobsContext.startPositionIterator.next();
		jobs.prepareIterations = stopIfRequested(computingSemaphore, () -> {
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
			for (Consumer<Double> listener : progressListeners) {
				listener.accept((double) jobs.totalIterations / maxIterations);
			}
			if (attractorsPanel.isVisible()) {
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
