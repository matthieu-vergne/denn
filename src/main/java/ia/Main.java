package ia;

import static ia.terrain.TerrainInteractor.*;
import static ia.terrain.TerrainInteractor.Condition.*;

import java.util.List;
import java.util.Random;

import ia.agent.Agent;
import ia.agent.Neural;
import ia.agent.Neural.Factory;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Mutator;
import ia.agent.adn.Program;
import ia.agent.adn.Reproducer;
import ia.terrain.Position;
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
		Factory<Program> programFactory = Neural.Factory.on(Program.Builder::new);
		terrain.placeAgent(Agent.createFromProgram(programFactory.moveDownRight()), terrain.minPosition());
		terrain.placeAgent(Agent.createFromProgram(programFactory.moveUpLeft()), terrain.maxPosition());
		terrain.placeAgent(Agent.createFromProgram(programFactory.moveToward(Position.at(1, 9))), Position.at(5, 5));
		terrain.placeAgent(Agent.createFromProgram(programFactory.moveToward(Position.at(8, 1))), Position.at(6, 6));
//		terrain.placeAgent(Agent.create(factory.moveRandomly()), Position.at(3, 3));
		int agentsLimit = 10;

		Reproducer reproducer = Reproducer.onRandomGenes(random);
		Mutator mutator = Mutator.withProbability(random, 0.01);

		AgentColorizer agentColorizer = AgentColorizer.basedOnChromosome2();
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
