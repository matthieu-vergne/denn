package ia.terrain;

import static ia.utils.CollectorsUtils.*;
import static ia.utils.StreamUtils.*;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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

		return iteratorFrom(tasks.prepare);
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
