package ia;

import static ia.terrain.TerrainInteractor.*;
import static ia.terrain.TerrainInteractor.Condition.*;

import java.util.List;
import java.util.Random;

import ia.agent.Agent;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Chromosome;
import ia.agent.adn.Code;
import ia.agent.adn.Mutator;
import ia.agent.adn.Operation;
import ia.agent.adn.Reproducer;
import ia.terrain.Terrain;
import ia.window.AgentColorizer;
import ia.window.Window;
import ia.window.Window.Button;

public class Main {
	public static void main(String[] args) {
		Random random = new Random(0);

		Terrain terrain = Terrain.createWithSize(10, 11);
		int cellSize = 30;

		// TODO Generate codes from builder calls
		List<Code> sourceCodes = List.of(//
				new Code(Operation.CREATE_WITH_FIXED_SIGNAL, 1.0), //
				new Code(Operation.SET_DX, 2.0), //
				new Code(Operation.CREATE_WITH_FIXED_SIGNAL, 1.0), //
				new Code(Operation.SET_DY, 3.0));
		Chromosome chromosome = new Chromosome(Code.serializeAll(sourceCodes));
		List<Code> decodedCodes = Code.deserializeAll(chromosome.bytes());

		NeuralNetwork.Factory factory = new NeuralNetwork.Factory(random);
//		NeuralNetwork moveDownRightNetwork = factory.moveDownRight();
		NeuralNetwork moveDownRightNetwork = factory.decode(decodedCodes);
		Agent moveDownRightAgent = Agent.create(chromosome);

		terrain.placeAgent(moveDownRightAgent, terrain.minPosition());
//		terrain.placeAgent(Agent.create(factory.moveUpLeft()), terrain.maxPosition());
//		terrain.placeAgent(Agent.create(factory.moveToward(Position.at(1, 9))), Position.at(5, 5));
//		terrain.placeAgent(Agent.create(factory.moveToward(Position.at(8, 1))), Position.at(6, 6));
//		terrain.placeAgent(Agent.create(factory.moveRandomly()), Position.at(3, 3));
		int agentsLimit = 10;

		Reproducer reproducer = Reproducer.onRandomGenes(random);
		Mutator mutator = Mutator.withProbability(random, 0.001);

		AgentColorizer agentColorizer = AgentColorizer.basedOnChromosome();
		Window.create(terrain, cellSize, agentColorizer, //
				List.of(//
						Button.create("Next", moveAgents().on(terrain)), //
						Button.create("Select", killAgents(withXAbove((terrain.width() - 1) / 2)).on(terrain)), //
						Button.create("Gen", reproduceAgents(reproducer, mutator, agentsLimit, random).on(terrain)), //
						Button.create("Move", dispatchAgentRandomly(random).on(terrain) //
						)//
				));
	}

}
