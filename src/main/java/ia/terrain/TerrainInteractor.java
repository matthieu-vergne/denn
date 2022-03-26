package ia.terrain;

import static java.util.stream.Collectors.*;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import ia.agent.Agent;
import ia.agent.adn.Chromosome;
import ia.agent.adn.Mutator;
import ia.agent.adn.Reproducer;

public interface TerrainInteractor {
	public Runnable on(Terrain terrain);

	default TerrainInteractor then(TerrainInteractor nextInteractor) {
		TerrainInteractor previousInteractor = this;
		return terrain -> {
			Runnable previousAction = previousInteractor.on(terrain);
			Runnable nextAction = nextInteractor.on(terrain);
			return () -> {
				previousAction.run();
				nextAction.run();
			};
		};
	}

	public static TerrainInteractor moveAgents() {
		return terrain -> {
			Position minPosition = terrain.minPosition();
			Position maxPosition = terrain.maxPosition();
			return () -> {
				terrain.agents().forEach(agent -> {
					Position currentPosition = terrain.getAgentPosition(agent);
					Position wantedPosition = agent.decideNextPosition(currentPosition);
					Position validPosition = wantedPosition.restrict(minPosition, maxPosition);
					if (terrain.isFreeFor(agent, validPosition)) {
						terrain.placeAgent(agent, validPosition);
					}
				});
			};
		};
	}

	public static TerrainInteractor killAgents(BiPredicate<Terrain, Agent> selector) {
		return terrain -> {
			Predicate<Agent> filter = (Predicate<Agent>) agent1 -> selector.test(terrain, agent1);
			return () -> {
				List<Agent> killed = terrain.agents().filter(filter).collect(toList());
				for (Agent agent2 : killed) {
					terrain.removeAgent(agent2);
				}
			};
		};
	}

	public static TerrainInteractor keepAgents(BiPredicate<Terrain, Agent> selector) {
		return killAgents(selector.negate());
	}

	public static TerrainInteractor reproduceAgents(Reproducer reproducer, Mutator mutator, int agentsLimit, Random random) {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		return terrain -> {
			int agentsMax = terrain.width() * terrain.height();
			if (agentsLimit > agentsMax) {
				IllegalArgumentException tooHighLimit = new IllegalArgumentException("Agents limit must be at most " + agentsMax);
				tooHighLimit.setStackTrace(stackTrace);
				throw tooHighLimit;
			}
			return () -> {
				List<Agent> parents = terrain.agents().collect(toList());
				Iterator<Position> freeRandomPosition = searchFreePositions(terrain, random);
				while (terrain.agentsCount() < agentsLimit && freeRandomPosition.hasNext()) {
					Agent parent1 = parents.get(random.nextInt(parents.size()));
					Agent parent2 = parents.get(random.nextInt(parents.size()));
					Chromosome chromosome1 = parent1.chromosome();
					Chromosome chromosome2 = parent2.chromosome();
					Chromosome chromosomeChild = reproducer.reproduce(chromosome1, chromosome2);
					chromosomeChild = mutator.mutate(chromosomeChild);
					Agent child = Agent.create(chromosomeChild);
					terrain.placeAgent(child, freeRandomPosition.next());
				}
			};
		};
	}

	private static Iterator<Position> searchFreePositions(Terrain terrain, Random random) {
		List<Position> freePositions = terrain.freePositions().collect(toList());
		Collections.shuffle(freePositions, random);
		Iterator<Position> freeRandomPosition = freePositions.iterator();
		return freeRandomPosition;
	}

	public static TerrainInteractor dispatchAgentRandomly(Random random) {
		return terrain -> {
			return () -> {
				List<Agent> agents = terrain.agents().collect(toList());
				for (Agent agent : agents) {
					terrain.removeAgent(agent);
				}
				Iterator<Position> freeRandomPosition = searchFreePositions(terrain, random);
				for (Agent agent : agents) {
					terrain.placeAgent(agent, freeRandomPosition.next());
				}
			};
		};
	}

	public static class Condition {
		public static BiPredicate<Terrain, Agent> withXAbove(int xLimit) {
			return (terrain, agent) -> terrain.getAgentPosition(agent).x > xLimit;
		}

		public static BiPredicate<Terrain, Agent> withXBelow(int xLimit) {
			return (terrain, agent) -> terrain.getAgentPosition(agent).x < xLimit;
		}

		public static BiPredicate<Terrain, Agent> withYAbove(int yLimit) {
			return (terrain, agent) -> terrain.getAgentPosition(agent).y > yLimit;
		}

		public static BiPredicate<Terrain, Agent> withYBelow(int yLimit) {
			return (terrain, agent) -> terrain.getAgentPosition(agent).y < yLimit;
		}
	}
}
