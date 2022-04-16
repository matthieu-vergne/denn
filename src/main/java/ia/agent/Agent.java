package ia.agent;

import static java.util.Objects.*;

import ia.agent.adn.Chromosome;
import ia.agent.adn.Program;
import ia.terrain.Move;
import ia.terrain.Position;

public class Agent {

	private final Chromosome chromosome;
	private final NeuralNetwork neuralNetwork;

	private Agent(Chromosome chromosome, NeuralNetwork neuralNetwork) {
		this.chromosome = requireNonNull(chromosome, "No chromosome provided");
		this.neuralNetwork = requireNonNull(neuralNetwork, "No neural network provided");
	}

	public static Agent createFromChromosome(Chromosome chromosome) {
		byte[] bytes = chromosome.bytes();
		Program program = Program.deserialize(bytes);
		return new Agent(chromosome, new NeuralNetwork.Factory(null).execute(program));
	}

	public static Agent createFromProgram(Program program) {
		return createFromChromosome(new Chromosome(program.serialize()));
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
