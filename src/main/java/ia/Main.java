package ia;

import static ia.terrain.TerrainInteractor.*;
import static ia.terrain.TerrainInteractor.Condition.*;
import static java.lang.Math.*;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import ia.agent.Agent;
import ia.agent.Neural.Builder;
import ia.agent.NeuralNetwork;
import ia.agent.NeuralNetwork.Builder.Rand;
import ia.agent.NeuralNetwork.NeuralFunction;
import ia.agent.adn.Mutator;
import ia.agent.adn.Program;
import ia.agent.adn.Reproducer;
import ia.terrain.Position;
import ia.terrain.Terrain;
import ia.terrain.TerrainInteractor.Condition;
import ia.window.AgentColorizer;
import ia.window.Window;
import ia.window.Window.Button;

public class Main {

	public static void main(String[] args) {
		Random random = new Random(0);
		Rand rand = random::nextDouble;

		Terrain terrain = Terrain.createWithSize(100, 100);
		int cellSize = 7;

		Supplier<Builder<NeuralNetwork>> basicBuilderGenerator = () -> createNetworkBuilder(FunctionDecorator.IDENTITY,
				rand);
		NeuralNetwork.Factory basicFactory = new NeuralNetwork.Factory(basicBuilderGenerator, null);
		Supplier<Builder<NeuralNetwork>> loggedBuilderGenerator = () -> createNetworkBuilder(FunctionDecorator.LOGGED,
				rand);
		NeuralNetwork.Factory loggedFactory = new NeuralNetwork.Factory(loggedBuilderGenerator, null);
		BiConsumer<Program, Position> placer = (program, position) -> {
			terrain.placeAgent(Agent.createFromProgram(basicFactory, program), position);
		};
		BiConsumer<Program, Position> placerLogged = (program, position) -> {
			terrain.placeAgent(Agent.createFromProgram(loggedFactory, program), position);
		};
		Supplier<Program> nonMover = () -> {
			return createPerceptrons(//
					0, 0, 0, //
					0, 0, 0//
			);
		};
		Supplier<Program> downRightMover = () -> {
			return createPerceptrons(//
					0, 0, 1, //
					0, 0, 1//
			);
		};
		Supplier<Program> upLeftMover = () -> {
			return createPerceptrons(//
					0, 0, -1, //
					0, 0, -1//
			);
		};
		Function<Position, Program> positionMover = position -> {
			return createPerceptrons(//
					-1, 0, position.x, //
					0, -1, position.y//
			);
		};
		Supplier<Program> centerMover = () -> {
			return createPerceptrons(//
					-1, 0, terrain.width() / 2, //
					0, -1, terrain.height() / 2//
			);
		};
		Supplier<Program> cornerMover = () -> {
			return createPerceptrons(//
					1, 0, -terrain.width() / 2, //
					0, 1, -terrain.height() / 2//
			);
		};
		Supplier<Program> randomMover = () -> {
			return createPerceptrons(//
					0, 0, -1, 2, 0, //
					0, 0, -1, 0, 2//
			);
		};
		Program program = nonMover.get();
		placer.accept(program, terrain.minPosition());
		placer.accept(program, terrain.maxPosition());
		placer.accept(program, Position.at(40, 40));
		placer.accept(program, Position.at(40, 60));
		placer.accept(program, Position.at(60, 40));
		placer.accept(program, Position.at(60, 60));
//		placer.accept(downRightMover.get(), terrain.minPosition());
//		placer.accept(upLeftMover.get(), terrain.maxPosition());
//		placer.accept(positionMover.apply(Position.at(10, 90)), Position.at(5, 5));
//		placer.accept(positionMover.apply(Position.at(80, 10)), Position.at(6, 6));
//		terrain.placeAgent(Agent.create(factory.moveRandomly()), Position.at(3, 3));
		int agentsLimit = 1000;

		Reproducer reproducer = Reproducer.onRandomCodes(random);
		Mutator mutator = Mutator.onWeights(random, 0.0001);

		AgentColorizer agentColorizer = AgentColorizer.pickingOnBehaviour(terrain, basicFactory);
		Button.Action logPopulation = () -> {
			int remaining = terrain.agentsCount();
			int percent = 100 * remaining / agentsLimit;
			System.out.println("Remains " + remaining + " (" + percent + "%)");
		};
		int[] iterationCount = { 0 };
		Button.Action countIteration = () -> {
			System.out.println("Iteration " + (++iterationCount[0]));
		};
		Button.Action wait = () -> {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException cause) {
				throw new RuntimeException(cause);
			}
		};

		Condition.OnPosition onLeft = withXBelow((terrain.width() - 1) / 2);
		Condition.OnPosition onRight = onLeft.negate();
		Condition.OnPosition onTop = withYBelow((terrain.height() - 1) / 2);
		Condition.OnPosition onBottom = onTop.negate();
		Condition.OnPosition inLowX = withXBelow((terrain.width() - 1) / 3);
		Condition.OnPosition inVeryLowX = withXBelow((terrain.width() - 1) / 10);
		Condition.OnPosition inHighX = withXAbove((terrain.width() - 1) * 2 / 3);
		Condition.OnPosition inVeryHighX = withXAbove((terrain.width() - 1) * 9 / 10);
		Condition.OnPosition inLowY = withYBelow((terrain.height() - 1) / 3);
		Condition.OnPosition inVeryLowY = withYBelow((terrain.height() - 1) / 10);
		Condition.OnPosition inHighY = withYAbove((terrain.height() - 1) * 2 / 3);
		Condition.OnPosition inVeryHighY = withYAbove((terrain.height() - 1) * 9 / 10);
		Condition.OnPosition inXCenter = inLowX.or(inHighX).negate();
		Condition.OnPosition inYCenter = inLowY.or(inHighY).negate();
		Condition.OnPosition inCenter = inXCenter.and(inYCenter);
		Condition.OnPosition atBorders = inVeryLowX.or(inVeryHighX).or(inVeryLowY).or(inVeryHighY);
		Condition.OnPosition atTopLeft = inVeryLowX.and(inVeryLowY);
		Condition.OnPosition atTopRight = inVeryHighX.and(inVeryLowY);
		Condition.OnPosition atBottomLeft = inVeryLowX.and(inVeryHighY);
		Condition.OnPosition atBottomRight = inVeryHighX.and(inVeryHighY);
		Condition.OnPosition atCorners = atTopLeft.or(atTopRight).or(atBottomLeft).or(atBottomRight);
		Condition.OnPosition closeToCenter = closeTo(terrain.centerPosition(), terrain.width() / 10,
				terrain.width() * 2 / 10, random);
		int safeDistance = 30;
		int xMax = terrain.width() - 1;
		int yMax = terrain.height() - 1;
		Condition.OnPosition closeToTopLeft = closeTo(Position.at(0, 0), safeDistance);
		Condition.OnPosition closeToTopRight = closeTo(Position.at(xMax, 0), safeDistance);
		Condition.OnPosition closeToBottomRight = closeTo(Position.at(xMax, yMax), safeDistance);
		Condition.OnPosition closeToBottomLeft = closeTo(Position.at(0, yMax), safeDistance);
		Condition.OnPosition closeToCorners = closeToTopLeft.or(closeToTopRight).or(closeToBottomLeft)
				.or(closeToBottomRight);

		Condition.OnPosition selectionCriterion = inBand(Position.at(20, 20), Position.at(20, 80), 5, 30, random);
		Map<Position, Double> survivalRates = estimateSuccessRates(terrain, selectionCriterion);
		int survivalArea = survivalRates.size();
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

		Button.Action move = moveAgents().on(terrain);
		Button.Action reproduce = reproduceAgents(basicFactory, reproducer, mutator, agentsLimit, random).on(terrain);
		Button.Action fill = fillAgents(basicFactory, nonMover.get()).on(terrain);
		Button.Action dispatch = dispatchAgentRandomly(random).on(terrain);
		Button.Action select = keepAgents(selectionCriterion).on(terrain).then(terrain::optimize).then(logPopulation)
				.then(logSurvivalCoverage).then(logSurvivalSuccess);
		int terrainSize = max(terrain.width(), terrain.height());
		Button.Action iterate = countIteration.then(select).then(wait).then(reproduce).then(dispatch)
				.then(move.times(terrainSize));
		int compositeActionsPerSecond = 100;
		Window window = Window.create(terrain, cellSize, agentColorizer, compositeActionsPerSecond, //
				List.of(//
						Button.create("Move", move), //
						Button.create("x10", move.times(10)), //
						Button.create("x100", move.times(100)), //
						Button.create("Reproduce", reproduce), //
						Button.create("Fill", fill), //
						Button.create("Dispatch", dispatch), //
						Button.create("Select", select), //
						Button.create("Iterate", iterate), //
						Button.create("x1000", iterate.times(1000))//
				));

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

	private static ia.agent.NeuralNetwork.Builder createNetworkBuilder(UnaryOperator<NeuralFunction> functionDecorator,
			Rand random) {
		return new NeuralNetwork.Builder(random) {
			@Override
			public NeuralNetwork.Builder createNeuronWith(NeuralFunction function) {
				return super.createNeuronWith(functionDecorator.apply(function));
			}
		};
	}

	private static Program createPerceptrons(//
			double x2Dx, double y2Dx, double c2Dx, //
			double x2Dy, double y2Dy, double c2Dy) {
		return createPerceptrons(//
				x2Dx, y2Dx, c2Dx, 0, 0, //
				x2Dy, y2Dy, c2Dy, 0, 0//
		);
	}

	private static Program createPerceptrons(//
			double x2Dx, double y2Dx, double c2Dx, double ra2Dx, double rb2Dx, //
			double x2Dy, double y2Dy, double c2Dy, double ra2Dy, double rb2Dy) {
		// TODO Use layer definitions with weights
		Program.Builder builder = new Program.Builder();
		int index = 0;

		int x = index++;
		int y = index++;

		int c = index++;
		builder.createNeuronWithFixedSignal(1.0);

		int ra = index++;
		builder.createNeuronWithRandomSignal();
		int rb = index++;
		builder.createNeuronWithRandomSignal();

		// X -> DX
		int xDx = index++;
		builder.createNeuronWithWeightedSumFunction(x2Dx).moveTo(xDx).readSignalFrom(x);//
		// Y -> DX
		int yDx = index++;
		builder.createNeuronWithWeightedSumFunction(y2Dx).moveTo(yDx).readSignalFrom(y);//
		// C -> DX
		int cDx = index++;
		builder.createNeuronWithWeightedSumFunction(c2Dx).moveTo(cDx).readSignalFrom(c);//
		// Ra -> DX
		int raDx = index++;
		builder.createNeuronWithWeightedSumFunction(ra2Dx).moveTo(raDx).readSignalFrom(ra);//
		// Rb -> DX
		int rbDx = index++;
		builder.createNeuronWithWeightedSumFunction(rb2Dx).moveTo(rbDx).readSignalFrom(rb);//
		// X -> DY
		int xDy = index++;
		builder.createNeuronWithWeightedSumFunction(x2Dy).moveTo(xDy).readSignalFrom(x);//
		// Y -> DY
		int yDy = index++;
		builder.createNeuronWithWeightedSumFunction(y2Dy).moveTo(yDy).readSignalFrom(y);//
		// C -> DY
		int cDy = index++;
		builder.createNeuronWithWeightedSumFunction(c2Dy).moveTo(cDy).readSignalFrom(c);//
		// Ra -> DY
		int raDy = index++;
		builder.createNeuronWithWeightedSumFunction(ra2Dy).moveTo(raDy).readSignalFrom(ra);//
		// Rb -> DY
		int rbDy = index++;
		builder.createNeuronWithWeightedSumFunction(rb2Dy).moveTo(rbDy).readSignalFrom(rb);//

		int dx = index++;
		builder.createNeuronWithSumFunction().setDXAt(dx).moveTo(dx)//
				.readSignalFrom(xDx).readSignalFrom(yDx).readSignalFrom(cDx).readSignalFrom(raDx).readSignalFrom(rbDx);

		int dy = index++;
		builder.createNeuronWithSumFunction().setDYAt(dy).moveTo(dy)//
				.readSignalFrom(xDy).readSignalFrom(yDy).readSignalFrom(cDy).readSignalFrom(raDy).readSignalFrom(rbDy);

		return builder.build();
	}

	private static interface FunctionDecorator {
		static UnaryOperator<NeuralFunction> IDENTITY = UnaryOperator.identity();
		static UnaryOperator<NeuralFunction> LOGGED = function -> {
			System.out.println("+ N" + function.hashCode());
			return inputs -> {
				Double output = function.compute(inputs);
				System.out.println("N" + function.hashCode() + " < " + inputs);
				System.out.println("N" + function.hashCode() + " > " + output);
				return output;
			};
		};
	}

}
