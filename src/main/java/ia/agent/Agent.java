package ia.agent;

import static java.util.Objects.*;

import ia.agent.adn.Chromosome;
import ia.terrain.Position;

public class Agent {

	private final Chromosome chromosome;
	private final Brain brain;

	private Agent(Chromosome chromosome) {
		this.chromosome = requireNonNull(chromosome, "No chromosome provided");
		this.brain = chromosome.createBrain();
	}

	public static Agent create(Chromosome chromosome) {
		return new Agent(chromosome);
	}

	public Position decideNextPosition(Position position) {
		return position.move(brain.decideMoveFrom(position));
	}

	public Chromosome chromosome() {
		return chromosome;
	}

}
