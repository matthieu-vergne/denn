package ia.terrain;

import static ia.utils.CollectorsUtils.*;
import static ia.utils.StreamUtils.*;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import ia.agent.Agent;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Program;
import ia.utils.Position;
import ia.window.Button.Action;

public class BrowsersFactory {

	private final NeuralNetwork.Factory networkFactory;
	private final Terrain terrain;
	private final List<Position> positionsToBrowse;

	public BrowsersFactory(//
			NeuralNetwork.Factory networkFactory, Terrain terrain) {
		this.networkFactory = networkFactory;
		this.terrain = terrain;
		this.positionsToBrowse = terrain.allPositions().collect(toShuffledList());
	}

	public Stream<Browser> browsers(Program program) {
		return cycleOver(positionsToBrowse).map(startPosition -> new Browser() {
			@Override
			public String toString() {
				return "Trial@" + startPosition;
			}

			@Override
			public Position startPosition() {
				return startPosition;
			}

			int runIndex = 0;

			@Override
			public Stream<Path> paths() {
				Browser browser = this;
				return Stream.generate(() -> {
					Agent agentForRun = Agent.createFromProgram(networkFactory, program);
					Terrain terrainForRun = Terrain.createWithSize(terrain.width(), terrain.height());
					terrainForRun.placeAgent(agentForRun, startPosition);
					Action actionForRun = TerrainInteractor.moveAgents().on(terrainForRun);
					return new Path() {
						int index = runIndex++;

						@Override
						public String toString() {
							return browser() + "P" + index;
						}

						@Override
						public Browser browser() {
							return browser;
						}

						int stepIndex = 0;
						Position lastPosition = startPosition;

						@Override
						public Stream<Step> steps() {
							Path path = this;
							return Stream.generate(() -> {
								Position positionBefore = lastPosition;
								actionForRun.execute();
								lastPosition = terrainForRun.getAgentPosition(agentForRun);
								Position positionAfter = lastPosition;
								return new Step() {
									int index = stepIndex++;

									@Override
									public String toString() {
										return path() + "S" + index + " = " + positionAfter;
									}

									@Override
									public Path path() {
										return path;
									}

									@Override
									public Position positionBefore() {
										return positionBefore;
									}

									@Override
									public Position positionAfter() {
										return positionAfter;
									}
								};
							});
						}
					};
				});
			}
		});
	}

	public interface Browser {
		Position startPosition();

		Stream<Path> paths();
	}

	public interface Path {

		Browser browser();

		Stream<Step> steps();
	}

	public interface Step {

		Path path();

		Position positionBefore();

		Position positionAfter();

		public static Predicate<Step> movesWithin(int threshold) {
			return new Predicate<Step>() {
				int noMoveCount = 0;

				@Override
				public boolean test(Step step) {
					if (step.positionAfter().equals(step.positionBefore())) {
						noMoveCount++;
					} else {
						noMoveCount = 0;
					}
					return noMoveCount < threshold;
				}
			};
		}
	}
}
