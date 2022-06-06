package ia.terrain;

import static ia.utils.CollectorsUtils.*;
import static ia.utils.StreamUtils.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ia.agent.Agent;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Program;
import ia.utils.Position;
import ia.window.Button;

public class AttractorsComputer {

	private final NeuralNetwork.Factory networkFactory;
	private final Terrain terrain;
	private final List<Position> positionsToBrowse;
	private final int maxStartPositions;
	private final int maxRunsPerStartPosition;
	private final int maxIterationsPerRun;
	private final int runAutoStopThreshold;

	public AttractorsComputer(//
			NeuralNetwork.Factory networkFactory, //
			Terrain terrain, //
			int maxStartPositions, //
			int maxRunsPerStartPosition, //
			int maxIterationsPerRun, //
			int runAutoStopThreshold//
	) {
		this.networkFactory = networkFactory;
		this.terrain = terrain;
		// TODO Allow to filter positions
		this.positionsToBrowse = terrain.allPositions().collect(toShuffledList());
		this.maxStartPositions = maxStartPositions;
		this.maxRunsPerStartPosition = maxRunsPerStartPosition;
		this.maxIterationsPerRun = maxIterationsPerRun;
		this.runAutoStopThreshold = runAutoStopThreshold;
	}

	static interface ComputingTask {
		Optional<ComputingTask> executeAndReturnNextTask();
	}

	private static class ComputingContext {
		Program program;
		Agent clone;
		Iterator<Position> startPositionIterator;
		Position startPosition;
		Position previousPosition;
		// TODO Decorrelate actions from buttons
		Button.Action iterationAction;
		int iteration;
		int runIndex;
		int noMoveCount;
		Consumer<Position> attractorListener;
	}

	private static class ComputingTasks {
		ComputingTask prepare;
		ComputingTask iterate;
		ComputingTask checkNext;
	}

	public Iterator<Runnable> prepareTasks(Program program, Consumer<Position> attractorListener) {
		ComputingContext ctx = new ComputingContext();
		ctx.program = program;
		ctx.startPositionIterator = cycleOver(positionsToBrowse).limit(maxStartPositions).iterator();
		ctx.startPosition = ctx.startPositionIterator.next();
		ctx.iterationAction = TerrainInteractor.moveAgents().on(terrain);
		ctx.attractorListener = attractorListener;

		ComputingTasks tasks = new ComputingTasks();
		tasks.prepare = () -> prepare(ctx, tasks);
		tasks.iterate = () -> iterate(ctx, tasks);
		tasks.checkNext = () -> checkNext(ctx, tasks);

		// TODO Refine as streams
		// Method called trials(), returns Stream<Trial>
		Stream.of(Trial.fake()).flatMap(trial -> {
			Position startPosition = trial.startPosition();
			Stream<Run> runs = trial.runs();
			return runs;
		}).flatMap(run -> {
			Trial trial = run.trial();
			Stream<Position> path = run.path();// each position
			run.path().skip(100).findFirst();// attractor after 100 iterations, but execute all
			run.path().limit(100);// steps to reach attractor after 100 iterations, no execution
			Stream<Iteration> iterations = run.iterations();
			return iterations;
		}).map(iteration -> {
			Run run = iteration.run();
			Position positionBefore = iteration.positionBefore();
			Position positionAfter = iteration.positionAfter();
			int index = iteration.index();
			return positionAfter;
		});
		
		Stream<Trial> trials = Stream.of(Trial.fake());
		// User side
		Map<Run, Position> attractors = new HashMap<>();
		class Context2 {
			Run previousRun = null;
			Position previousPosition = null;
			int noMoveCount = 0;
		}
		Context2 ctx2 = new Context2();
		trials.limit(5)// try 5 start positions
				.flatMap(trial -> trial.runs().limit(10))// 10 runs per start position
				.flatMap(run -> run.iterations().limit(100))// 100 iterations per run
				.filter(iteration -> {
					// Ignore last iterations of the run if 10 stable positions
					return !(iteration.run().equals(ctx2.previousRun) && ctx2.noMoveCount >= 10);
				})
				.forEach(iteration -> {
					Position position = iteration.positionAfter();
					if (iteration.run().equals(ctx2.previousRun) && position.equals(ctx2.previousPosition)) {
						ctx2.noMoveCount++;
					} else {
						attractors.put(iteration.run(), position);
						ctx2.previousPosition = position;
						ctx2.previousRun = iteration.run();
						ctx2.noMoveCount = 0;
					}
				});
		// TODO Check laziness
		// TODO Use other name than "run"
		// TODO Rename executions methods "run"
		// TODO Check performance when compute + simulate
		return iteratorFrom(tasks.prepare);
	}

	interface Trial {
		static Trial fake() {
			return null;
		}

		Position startPosition();

		Stream<Run> runs();
	}

	interface Run {

		Stream<Position> path();

		Stream<Iteration> iterations();

		Trial trial();
	}

	interface Iteration {

		Position nextPosition();

		Position positionBefore();

		Position positionAfter();

		int index();

		Run run();
	}

	private Optional<ComputingTask> prepare(ComputingContext ctx, ComputingTasks tasks) {
		ctx.clone = Agent.createFromProgram(networkFactory, ctx.program);
		terrain.placeAgent(ctx.clone, ctx.startPosition);
		ctx.previousPosition = ctx.startPosition;
		ctx.noMoveCount = 0;
		return Optional.of(tasks.iterate);
	}

	private Optional<ComputingTask> iterate(ComputingContext ctx, ComputingTasks tasks) {
		ctx.iterationAction.execute();

		Position position = terrain.getAgentPosition(ctx.clone);
		if (position.equals(ctx.previousPosition)) {
			ctx.noMoveCount++;
		} else {
			ctx.previousPosition = position;
			ctx.noMoveCount = 0;
		}

		if (ctx.noMoveCount == runAutoStopThreshold) {
			ctx.iteration = maxIterationsPerRun;
		} else {
			ctx.iteration++;
		}

		return Optional.of(tasks.checkNext);
	}

	private Optional<ComputingTask> checkNext(ComputingContext ctx, ComputingTasks tasks) {
		// Next iteration
		if (ctx.iteration < maxIterationsPerRun) {
			return Optional.of(tasks.iterate);
		}

		// Next run
		Position lastPosition = terrain.removeAgent(ctx.clone);
		ctx.attractorListener.accept(lastPosition);
		ctx.runIndex++;
		ctx.iteration = 0;
		if (ctx.runIndex < maxRunsPerStartPosition) {
			return Optional.of(tasks.prepare);
		}

		// Next start position
		ctx.runIndex = 0;
		if (ctx.startPositionIterator.hasNext()) {
			ctx.startPosition = ctx.startPositionIterator.next();
			return Optional.of(tasks.prepare);
		}

		// Finished
		return Optional.empty();
	}

	private Iterator<Runnable> iteratorFrom(ComputingTask firstTask) {
		return new Iterator<Runnable>() {
			Optional<ComputingTask> nextTask = Optional.of(firstTask);

			@Override
			public Runnable next() {
				return nextTask.map(task -> (Runnable) () -> nextTask = task.executeAndReturnNextTask()).get();
			}

			@Override
			public boolean hasNext() {
				return nextTask.isPresent();
			}
		};
	}
}
