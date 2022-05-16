package ia.terrain;

import static java.lang.Math.*;
import static java.util.stream.Collectors.*;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

import ia.agent.Agent;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Chromosome;
import ia.agent.adn.Mutator;
import ia.agent.adn.Program;
import ia.agent.adn.Reproducer;
import ia.window.Button;

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

			public static class Factory {
				private final Terrain terrain;
				private final Random random;
				private final Optional<Double> safeDistance;
				private final Optional<Double> deathDistance;

				public Factory(Terrain terrain, Random random) {
					this(terrain, random, Optional.empty(), Optional.empty());
				}

				private Factory(Terrain terrain, Random random, Optional<Double> safeDistance,
						Optional<Double> deathDistance) {
					this.terrain = Objects.requireNonNull(terrain, "No terrain provided");
					this.random = Objects.requireNonNull(random, "No random computer provided");
					if (safeDistance.isPresent() && deathDistance.isPresent()
							&& safeDistance.get() > deathDistance.get()) {
						throw new IllegalArgumentException("Safe distance (" + safeDistance
								+ ") cannot be lower than death distance (" + deathDistance + ")");
					}
					this.safeDistance = safeDistance;
					this.deathDistance = deathDistance;
				}

				private Supplier<Double> limitDistanceSupplier() {
					double safeDistance = this.safeDistance.orElse(0.0);
					double deathDistance = this.deathDistance.orElse(safeDistance);
					if (safeDistance == deathDistance) {
						return () -> safeDistance;
					} else {
						double randomDistance = deathDistance - safeDistance;
						return () -> safeDistance + random.nextDouble(randomDistance);
					}
				}

				public Factory surviveUntil(double safeDistance) {
					return new Factory(terrain, random, Optional.of(safeDistance), deathDistance);
				}

				public Factory dieFrom(double deathDistance) {
					return new Factory(terrain, random, safeDistance, Optional.of(deathDistance));
				}

				public OnPosition fromPosition(Position safePosition) {
					Supplier<Double> limitDistance = limitDistanceSupplier();
					return position -> safePosition.distanceTo(position) <= limitDistance.get();
				}

				public OnPosition fromTopLeft() {
					return fromPosition(Position.at(0, 0));
				}

				public OnPosition fromTopRight() {
					return fromPosition(Position.at(terrain.width() - 1, 0));
				}

				public OnPosition fromBottomLeft() {
					return fromPosition(Position.at(0, terrain.height() - 1));
				}

				public OnPosition fromBottomRight() {
					return fromPosition(Position.at(terrain.width() - 1, terrain.height() - 1));
				}

				public OnPosition fromCorners() {
					return fromTopLeft().or(fromTopRight()).or(fromBottomLeft()).or(fromBottomRight());
				}

				public OnPosition fromCenter() {
					return fromPosition(terrain.centerPosition());
				}

				public OnPosition fromBand(Position p1, Position p2) {
					Supplier<Double> limitDistance = limitDistanceSupplier();
					return position -> {
						double distance = limitDistance.get();
						if (p1.distanceTo(position) <= distance) {
							return true;
						}
						if (p2.distanceTo(position) <= distance) {
							return true;
						}
						Projection projection = project(p1, p2, position);
						return projection.band >= 0 && projection.band <= 1 && abs(projection.orthogonal) <= distance;
					};
				}

				private static record Projection(double band, double orthogonal) {
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

				public OnPosition fromX(int x) {
					return fromBand(Position.at(x, 0), Position.at(x, terrain.height() - 1));
				}

				public OnPosition fromY(int y) {
					return fromBand(Position.at(0, y), Position.at(terrain.width() - 1, y));
				}

				public OnPosition fromLeft() {
					return fromX(0);
				}

				public OnPosition fromRight() {
					return fromX(terrain.width() - 1);
				}

				public OnPosition fromTop() {
					return fromY(0);
				}

				public OnPosition fromBottom() {
					return fromY(terrain.height() - 1);
				}

				public OnPosition fromXCenter() {
					return fromX(terrain.centerPosition().x);
				}

				public OnPosition fromYCenter() {
					return fromY(terrain.centerPosition().y);
				}

				public OnPosition fromBorders() {
					return fromLeft().or(fromRight()).or(fromBottom()).or(fromTop());
				}
			}
		}
	}
}
