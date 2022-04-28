package ia.terrain;

import static java.util.Objects.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import ia.agent.Agent;

public class Terrain {

	private final int width;
	private final int height;

	private Terrain(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public static Terrain createWithSize(int width, int height) {
		return new Terrain(width, height);
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public Position minPosition() {
		return Position.at(0, 0);
	}

	public Position maxPosition() {
		return Position.at(width - 1, height - 1);
	}

	public Position centerPosition() {
		return Position.at(width / 2, height / 2);
	}

	private final Map<Agent, Position> agentsPosition = new HashMap<>();

	public Stream<Agent> agents() {
		return new ArrayList<>(agentsPosition.keySet()).stream();
	}

	public void placeAgent(Agent agent, Position position) {
		requireNonNull(agent, "No agent provided");
		requireNonNull(position, "No position provided");
		if (!isFreeFor(agent, position)) {
			throw new IllegalArgumentException("Unavailable position " + position);
		}
		agentsPosition.put(agent, position);
	}

	public boolean isFreeFor(Agent agent, Position position) {
		requireNonNull(agent, "No agent provided");
		requireNonNull(position, "No position provided");
		Optional<Agent> agentThere = getAgentAt(position);
		return agentThere.isEmpty() || agentThere.get().equals(agent);
	}

	public Optional<Agent> getAgentAt(Position position) {
		requireNonNull(position, "No position provided");
		if (position.restrict(minPosition(), maxPosition()).equals(position)) {
			return agentsPosition.entrySet().stream().filter(entry -> entry.getValue().equals(position))
					.<Agent>map(entry -> entry.getKey()).findFirst();
		} else {
			throw new IllegalArgumentException("Invalid position " + position);
		}
	}

	public Position getAgentPosition(Agent agent) {
		requireNonNull(agent, "No agent provided");
		Position position = agentsPosition.get(agent);
		if (position == null) {
			throw new NoSuchElementException("Unknown agent " + agent);
		}
		return position;
	}

	public int agentsCount() {
		return agentsPosition.size();
	}

	public Stream<Position> freePositions() {
		return xStream().flatMap(x -> //
		yStream().flatMap(y -> //
		Stream.of(Position.at(x, y))//
		)//
		).filter(position -> this.getAgentAt(position).isEmpty());
	}

	private Stream<Integer> yStream() {
		return IntStream.range(0, height).mapToObj(y -> y);
	}

	private Stream<Integer> xStream() {
		return IntStream.range(0, width).mapToObj(x -> x);
	}

	public Position removeAgent(Agent agent) {
		Position position = agentsPosition.remove(agent);
		if (position == null) {
			throw new IllegalArgumentException("Unknown agent " + agent);
		}
		return position;
	}

}
