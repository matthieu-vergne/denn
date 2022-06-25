package fr.vergne.denn.agent;

import static java.util.Objects.*;

import fr.vergne.denn.agent.NeuralNetwork.AgentNetwork;
import fr.vergne.denn.agent.adn.Chromosome;
import fr.vergne.denn.agent.adn.Program;
import fr.vergne.denn.utils.Position;

public class Agent {

	private final Chromosome chromosome;
	private final AgentNetwork network;

	private Agent(Chromosome chromosome, NeuralNetwork neuralNetwork) {
		this.chromosome = requireNonNull(chromosome, "No chromosome provided");
		this.network = requireNonNull(neuralNetwork.forAgent(), "No neural network provided");
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
		network.setPosition(position);
		network.fire();
		Position.Move move = network.getMove();
		return position.move(move);
	}

	public Chromosome chromosome() {
		return chromosome;
	}

}
