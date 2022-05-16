package ia;

import static ia.terrain.TerrainInteractor.*;
import static java.lang.Math.*;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import ia.agent.Agent;
import ia.agent.Neural.Builder;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Mutator;
import ia.agent.adn.Program;
import ia.agent.adn.Reproducer;
import ia.terrain.Position;
import ia.terrain.Terrain;
import ia.terrain.TerrainInteractor.Condition;
import ia.window.AgentColorizer;
import ia.window.Window;
import ia.window.Button;

public class Main {

	public static void main(String[] args) {
		Random random = new Random(0);

		Terrain terrain = Terrain.createWithSize(100, 100);

		Supplier<Builder<NeuralNetwork>> networkBuilderGenerator = () -> new NeuralNetwork.Builder(random::nextDouble);
		NeuralNetwork.Factory networkFactory = new NeuralNetwork.Factory(networkBuilderGenerator, random);
		Function<Program, Agent> agentGenerator = program -> Agent.createFromProgram(networkFactory, program);
		Program.Factory programFactory = new Program.Factory();
		initializeAgents(terrain, programFactory, agentGenerator);
		int agentsLimit = 1000;

		Condition.OnPosition selectionCriterion = new Condition.OnPosition.Factory(terrain, random)
				.surviveUntil(terrain.width() / 10)//
				.dieFrom(terrain.width() * 2 / 10)//
				.fromCenter();
		Reproducer reproducer = Reproducer.onRandomCodes(random);
		Mutator mutator = Mutator.onWeights(random, 0.001);

		Map<Position, Double> survivalRates = estimateSuccessRates(terrain, selectionCriterion);
		List<List<Button>> buttons = createButtons(random, terrain, networkFactory, programFactory, agentsLimit,
				selectionCriterion, survivalRates, reproducer, mutator);
		AgentColorizer agentColorizer = AgentColorizer.pickingOnBehaviour(terrain, networkFactory);
		int compositeActionsPerSecond = 100;
		int cellSize = 7;
		// TODO Display network topography
		// TODO Allow manual agent placement
		Window window = Window.create(terrain, cellSize, agentColorizer, compositeActionsPerSecond, //
				buttons, networkFactory);

		float transparency = 0.3f;
		Color safeColor = new Color(0.0f, 1.0f, 0.0f, transparency);
		Color surviveColor = new Color(1.0f, 1.0f, 0.0f, transparency);
		Color deathColor = new Color(1.0f, 0.0f, 0.0f, transparency);
		window.addFilter(position -> {
			double rate = survivalRates.get(position);
			if (rate == 1.0) {
				return safeColor;
			}
			if (rate == 0.0) {
				return deathColor;
			}
			return surviveColor;
		});
	}

	private static List<List<Button>> createButtons(Random random, Terrain terrain,
			NeuralNetwork.Factory networkFactory, Program.Factory programFactory, int agentsLimit,
			Condition.OnPosition selectionCriterion, Map<Position, Double> survivalRates, Reproducer reproducer,
			Mutator mutator) {
		Button.Action logPopulation = () -> {
			int remaining = terrain.agentsCount();
			int percent = 100 * remaining / agentsLimit;
			System.out.println("Remains " + remaining + " (" + percent + "%)");
		};

		int[] iterationCount = { 0 };
		Button.Action countIteration = () -> {
			System.out.println("Iteration " + (++iterationCount[0]));
		};

		int survivalArea = (int) survivalRates.values().stream().mapToDouble(d -> d).sum();
		Button.Action logSurvivalCoverage = () -> {
			int remaining = terrain.agentsCount();
			int percent = 100 * remaining / survivalArea;
			System.out.println("Survival area coverage " + percent + "%");
		};

		int survivalTarget = min(agentsLimit, survivalArea);
		Button.Action logSurvivalSuccess = () -> {
			int remaining = terrain.agentsCount();
			int percent = 100 * remaining / survivalTarget;
			System.out.println("Survival success " + percent + "%");
		};

		Button.Action wait = () -> {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException cause) {
				throw new RuntimeException(cause);
			}
		};

		Button.Action move = moveAgents().on(terrain);
		Button.Action reproduce = reproduceAgents(networkFactory, reproducer, mutator, agentsLimit, random).on(terrain);
		Button.Action fill = fillAgents(networkFactory, programFactory.nonMover()).on(terrain);
		Button.Action dispatch = dispatchAgentRandomly(random).on(terrain);
		Button.Action select = keepAgents(selectionCriterion).on(terrain).then(terrain::optimize).then(logPopulation)
				.then(logSurvivalCoverage).then(logSurvivalSuccess);
		int terrainSize = max(terrain.width(), terrain.height());
		Button.Action iterate = countIteration.then(select).then(wait).then(reproduce).then(dispatch)
				.then(move.times(terrainSize));

		return List.of(//
				List.of(//
						Button.create("Move", move), //
						Button.create("x10", move.times(10)), //
						Button.create("x100", move.times(100)) //
				), //
				List.of(//
						Button.create("Reproduce", reproduce), //
						Button.create("Fill", fill) //
				), //
				List.of(//
						Button.create("Dispatch", dispatch) //
				), //
				List.of(//
						Button.create("Select", select) //
				), //
				List.of(//
						Button.create("Iterate", iterate), //
						Button.create("x10", iterate.times(10)), //
						Button.create("x100", iterate.times(100)), //
						Button.create("x1000", iterate.times(1000))//
				)//
		);
	}

	private static void initializeAgents(Terrain terrain, Program.Factory programFactory,
			Function<Program, Agent> agentGenerator) {
		BiConsumer<Program, Position> placer = (program, position) -> {
			terrain.placeAgent(agentGenerator.apply(program), position);
		};
		Program mainProgram = programFactory.nonMover();
		placer.accept(mainProgram, terrain.minPosition());
		placer.accept(mainProgram, terrain.maxPosition());
		placer.accept(programFactory.centerMover(terrain),
				Position.at(terrain.width() * 4 / 10, terrain.height() * 4 / 10));
		placer.accept(mainProgram, Position.at(terrain.width() * 4 / 10, terrain.height() * 6 / 10));
		placer.accept(mainProgram, Position.at(terrain.width() * 6 / 10, terrain.height() * 4 / 10));
		placer.accept(mainProgram, Position.at(terrain.width() * 6 / 10, terrain.height() * 6 / 10));
	}

	private static Map<Position, Double> estimateSuccessRates(Terrain terrain, Condition.OnPosition positionTrial) {
		Map<Position, Double> successRates = new HashMap<Position, Double>();
		for (int x = 0; x < terrain.width(); x++) {
			for (int y = 0; y < terrain.height(); y++) {
				Position position = Position.at(x, y);
				Double successRate = estimateSuccessRate(() -> positionTrial.test(position));
				successRates.put(position, successRate);
			}
		}
		return successRates;
	}

	private static Double estimateSuccessRate(Supplier<Boolean> trial) {
		int attempts = 1000;
		long survived = repeat(trial, attempts);
		return (double) survived / attempts;
	}

	private static long repeat(Supplier<Boolean> trial, int attempts) {
		return IntStream.range(0, attempts).mapToObj(i -> trial.get()).filter(b -> b).count();
	}

}
