package ia.terrain;

import static java.lang.Math.*;
import static java.util.stream.Collectors.*;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import ia.agent.Agent;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Chromosome;
import ia.agent.adn.Mutator;
import ia.agent.adn.Program;
import ia.agent.adn.Reproducer;
import ia.window.Window.Button;

public interface TerrainInteractor {
	public Button.Action on(Terrain terrain);

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
			Predicate<Agent> forKilling = agent -> selector.test(terrain, agent);
			return () -> terrain.agents().filter(forKilling).forEach(terrain::removeAgent);
		};
	}

	public static TerrainInteractor keepAgents(BiPredicate<Terrain, Agent> selector) {
		return killAgents(selector.negate());
	}

	public static TerrainInteractor reproduceAgents(NeuralNetwork.Factory networkFactory, Reproducer reproducer,
			Mutator mutator, int agentsLimit, Random random) {
		StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
		return terrain -> {
			int agentsMax = terrain.width() * terrain.height();
			if (agentsLimit > agentsMax) {
				IllegalArgumentException tooHighLimit = new IllegalArgumentException(
						"Agents limit must be at most " + agentsMax);
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
					Agent child = Agent.createFromChromosome(networkFactory, chromosomeChild);
					terrain.placeAgent(child, freeRandomPosition.next());
				}
			};
		};
	}

	public static TerrainInteractor fillAgents(NeuralNetwork.Factory networkFactory, Program program) {
		return terrain -> {
			return () -> {
				Iterator<Position> freePosition = terrain.freePositions().iterator();
				while (freePosition.hasNext()) {
					Agent clone = Agent.createFromProgram(networkFactory, program);
					terrain.placeAgent(clone, freePosition.next());
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

	public static interface Condition extends BiPredicate<Terrain, Agent> {
		@Override
		boolean test(Terrain terrain, Agent agent);

		public static interface OnPosition extends Condition {
			boolean test(Position position);

			@Override
			default boolean test(Terrain terrain, Agent agent) {
				return test(terrain.getAgentPosition(agent));
			}

			default Condition.OnPosition and(Condition.OnPosition other) {
				return position -> this.test(position) && other.test(position);
			}

			default Condition.OnPosition or(Condition.OnPosition other) {
				return position -> this.test(position) || other.test(position);
			}

			@Override
			default Condition.OnPosition negate() {
				return position -> !this.test(position);
			}
		}

		public static Condition.OnPosition withXAbove(int xLimit) {
			return position -> position.x >= xLimit;
		}

		public static Condition.OnPosition withXBelow(int xLimit) {
			return position -> position.x <= xLimit;
		}

		public static Condition.OnPosition withXIn(int xMin, int xMax) {
			return withXAbove(xMin).and(withXBelow(xMax));
		}

		public static Condition.OnPosition withYAbove(int yLimit) {
			return position -> position.y >= yLimit;
		}

		public static Condition.OnPosition withYBelow(int yLimit) {
			return position -> position.y <= yLimit;
		}

		public static Condition.OnPosition withYIn(int yMin, int yMax) {
			return withYAbove(yMin).and(withYBelow(yMax));
		}

		public static Condition.OnPosition inBand(Position p1, Position p2, int safeDistance) {
			return position -> {
				if (p1.distanceTo(position) < safeDistance) {
					return true;
				}
				if (p2.distanceTo(position) < safeDistance) {
					return true;
				}
				Projection projection = project(p1, p2, position);
				return projection.band > 0 && projection.band < 1 && abs(projection.orthogonal) < safeDistance;
			};
		}

		public static Condition.OnPosition inBand(Position p1, Position p2, int safeDistance, int deathDistance,
				Random random) {
			if (safeDistance > deathDistance) {
				throw new IllegalArgumentException("Death distance must be above safe distance");
			}
			if (deathDistance == safeDistance) {
				return inBand(p1, p2, safeDistance);
			}
			Objects.requireNonNull(random, "No random component provided");

			BiPredicate<Position, Integer> check = (position, distance) -> {
				if (p1.distanceTo(position) < distance) {// TODO Don't compute twice
					return true;
				}
				if (p2.distanceTo(position) < distance) {// TODO Don't compute twice
					return true;
				}
				Projection projection = project(p1, p2, position);// TODO Don't compute twice
				return projection.band > 0 && projection.band < 1 && abs(projection.orthogonal) < distance;
			};
			return position -> {
				return check.test(position, safeDistance)
						|| check.test(position, safeDistance + random.nextInt(deathDistance - safeDistance));
			};
		}

		static record Projection(double band, double orthogonal) {
		};

		private static Projection project(Position p1, Position p2, Position position) {
			// Compute the band vector
			double vx = p2.x - p1.x;
			double vy = p2.y - p1.y;
			// compute the orthogonal unit vector (size of 1 pixel)
			double vNorm = hypot(vx, vy);
			double wx = -vy / vNorm;
			double wy = vx / vNorm;
			// Compute the agent vector
			double ux = position.x - p1.x;
			double uy = position.y - p1.y;
			// Project the agent vector on the band vector
			// In the band, it is between 0 and 1
			double bandProjection = (ux * vx + uy * vy) / (vx * vx + vy * vy);
			// Project the agent vector on the orthogonal unit vector
			// In the band, its absolute value is below the safe distance
			double orthogonalProjection = (ux * wx + uy * wy) / (wx * wx + wy * wy);

			return new Projection(bandProjection, orthogonalProjection);
		}

		public static Condition.OnPosition closeTo(Position safePosition, double safeDistance) {
			return position -> safePosition.distanceTo(position) < safeDistance;
		}

		public static Condition.OnPosition closeTo(Position safePosition, double safeDistance, double deathDistance,
				Random random) {
			if (safeDistance > deathDistance) {
				throw new IllegalArgumentException("Death distance must be above safe distance");
			}
			if (deathDistance == safeDistance) {
				return closeTo(safePosition, safeDistance);
			}
			Objects.requireNonNull(random, "No random component provided");
			return position -> {
				double survivableDistance = safeDistance + random.nextDouble(deathDistance - safeDistance);
				return safePosition.distanceTo(position) < survivableDistance;
			};
		}
	}
}
