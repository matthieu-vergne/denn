package ia.agent;

import static java.util.Objects.*;

import ia.agent.adn.Chromosome;
import ia.agent.adn.Code;
import ia.terrain.Move;
import ia.terrain.Position;

public class Agent {

	private final Chromosome chromosome;
	private final NeuralNetwork neuralNetwork;

	private Agent(Chromosome chromosome, NeuralNetwork neuralNetwork) {
		this.chromosome = requireNonNull(chromosome, "No chromosome provided");
		this.neuralNetwork = requireNonNull(neuralNetwork, "No neural network provided");
	}

	public static Agent create(Chromosome chromosome) {
		return new Agent(chromosome, new NeuralNetwork.Factory(null).decode(Code.deserializeAll(chromosome.bytes())));
	}

	public Position decideNextPosition(Position position) {
		neuralNetwork.setInputs(position);
		neuralNetwork.fire();
		Move move = neuralNetwork.output();
		return position.move(move);
	}

	public Chromosome chromosome() {
		return chromosome;
	}

}
