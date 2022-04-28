package ia;

import static ia.terrain.TerrainInteractor.*;
import static ia.terrain.TerrainInteractor.Condition.*;

import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import ia.agent.Agent;
import ia.agent.Neural.Builder;
import ia.agent.NeuralNetwork;
import ia.agent.NeuralNetwork.NeuralFunction;
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
		int cellSize = 50;

		// TODO Iterate smoothly
		Supplier<Builder<NeuralNetwork>> basicBuilderGenerator = () -> createNetworkBuilder(FunctionDecorator.IDENTITY);
		NeuralNetwork.Factory basicFactory = new NeuralNetwork.Factory(basicBuilderGenerator, null);
		Supplier<Builder<NeuralNetwork>> loggedBuilderGenerator = () -> createNetworkBuilder(FunctionDecorator.LOGGED);
		NeuralNetwork.Factory loggedFactory = new NeuralNetwork.Factory(loggedBuilderGenerator, null);
		BiConsumer<Program, Position> placer = (program, position) -> {
			terrain.placeAgent(Agent.createFromProgram(basicFactory, program), position);
		};
		BiConsumer<Program, Position> placerLogged = (program, position) -> {
			terrain.placeAgent(Agent.createFromProgram(loggedFactory, program), position);
		};
		Function<Position, Program> positionMover = position -> {
			return createPerceptrons(-1, 0, position.x, 0, -1, position.y);
		};
		Supplier<Program> downRightMover = () -> {
			return createPerceptrons(0, 0, 1, 0, 0, 1);
		};
		Supplier<Program> upLeftMover = () -> {
			return createPerceptrons(0, 0, -1, 0, 0, -1);
		};
		placer.accept(downRightMover.get(), terrain.minPosition());
		placer.accept(upLeftMover.get(), terrain.maxPosition());
		placer.accept(positionMover.apply(Position.at(1, 9)), Position.at(5, 5));
		placer.accept(positionMover.apply(Position.at(8, 1)), Position.at(6, 6));
//		terrain.placeAgent(Agent.create(factory.moveRandomly()), Position.at(3, 3));
		int agentsLimit = 10;

		Reproducer reproducer = Reproducer.onRandomCodes(random);
		Mutator mutator = Mutator.onWeights(random, 0.01);

		AgentColorizer agentColorizer = AgentColorizer.pickingOnWeights();
		Button.Action moveAction = moveAgents().on(terrain);
		Button.Action selectAction = keepAgents(withXIn((terrain.width() - 1) / 3, (terrain.width() - 1) * 2 / 3))
				.on(terrain);
		Button.Action fillAction = reproduceAgents(basicFactory, reproducer, mutator, agentsLimit, random).on(terrain);
		Button.Action dispatchAction = dispatchAgentRandomly(random).on(terrain);
		Button.Action iterateAction = selectAction.then(fillAction.then(dispatchAction.then(moveAction.repeat(10))));
		int iterateStepsPerSecond = 5;
		Window.create(terrain, cellSize, agentColorizer, iterateStepsPerSecond, //
				List.of(//
						Button.create("Move", moveAction), //
						Button.create("Select", selectAction), //
						Button.create("Fill", fillAction), //
						Button.create("Dispatch", dispatchAction), //
						Button.create("Iterate", iterateAction)//
				));
	}

	private static ia.agent.NeuralNetwork.Builder createNetworkBuilder(
			UnaryOperator<NeuralFunction> functionDecorator) {
		return new NeuralNetwork.Builder() {
			@Override
			public NeuralNetwork.Builder createNeuronWith(NeuralFunction function) {
				return super.createNeuronWith(functionDecorator.apply(function));
			}
		};
	}

	private static Program createPerceptrons(//
			double x2Dx, double y2Dx, double c2Dx, //
			double x2Dy, double y2Dy, double c2Dy) {
		Program.Builder builder = new Program.Builder();
		int index = 0;

		int x = index++;
		int y = index++;

		int c = index++;
		builder.createNeuronWithFixedSignal(1.0);

		// X -> DX
		int xDx = index++;
		builder.createNeuronWithWeightedSumFunction(x2Dx).moveTo(xDx).readSignalFrom(x);//
		// Y -> DX
		int yDx = index++;
		builder.createNeuronWithWeightedSumFunction(y2Dx).moveTo(yDx).readSignalFrom(y);//
		// C -> DX
		int cDx = index++;
		builder.createNeuronWithWeightedSumFunction(c2Dx).moveTo(cDx).readSignalFrom(c);//
		// X -> DY
		int xDy = index++;
		builder.createNeuronWithWeightedSumFunction(x2Dy).moveTo(xDy).readSignalFrom(x);//
		// Y -> DY
		int yDy = index++;
		builder.createNeuronWithWeightedSumFunction(y2Dy).moveTo(yDy).readSignalFrom(y);//
		// C -> DY
		int cDy = index++;
		builder.createNeuronWithWeightedSumFunction(c2Dy).moveTo(cDy).readSignalFrom(c);//

		int dx = index++;
		builder.createNeuronWithSumFunction().setDXAt(dx).moveTo(dx)//
				.readSignalFrom(xDx).readSignalFrom(yDx).readSignalFrom(cDx);

		int dy = index++;
		builder.createNeuronWithSumFunction().setDYAt(dy).moveTo(dy)//
				.readSignalFrom(xDy).readSignalFrom(yDy).readSignalFrom(cDy);

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
