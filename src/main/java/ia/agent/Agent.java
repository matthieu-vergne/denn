package ia.agent;

import static java.util.Objects.*;

import ia.agent.adn.Chromosome;
import ia.agent.adn.Program;
import ia.utils.Position;

public class Agent {

	private final Chromosome chromosome;
	private final NeuralNetwork neuralNetwork;

	private Agent(Chromosome chromosome, NeuralNetwork neuralNetwork) {
		this.chromosome = requireNonNull(chromosome, "No chromosome provided");
		this.neuralNetwork = requireNonNull(neuralNetwork, "No neural network provided");
	}

	public static Agent createFromChromosome(NeuralNetwork.Factory networkFactory, Chromosome chromosome) {
		byte[] bytes = chromosome.bytes();
		Program program = Program.deserialize(bytes);
		return new Agent(chromosome, networkFactory.execute(program));
	}

	public static Agent createFromProgram(NeuralNetwork.Factory networkFactory, Program program) {
		return createFromChromosome(networkFactory, new Chromosome(program.serialize()));
	}

	public Position decideNextPosition(Position position) {
		neuralNetwork.setInputs(position);
		neuralNetwork.fire();
		Position.Move move = neuralNetwork.output();
		return position.move(move);
	}

	public Chromosome chromosome() {
		return chromosome;
	}

}
