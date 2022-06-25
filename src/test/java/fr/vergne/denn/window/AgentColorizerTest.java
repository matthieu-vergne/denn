package fr.vergne.denn.window;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.params.provider.Arguments.*;

import java.awt.Color;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import fr.vergne.denn.agent.Agent;
import fr.vergne.denn.agent.Neural.Builder;
import fr.vergne.denn.agent.NeuralNetwork;
import fr.vergne.denn.agent.adn.Program;
import fr.vergne.denn.agent.adn.Program.Factory;
import fr.vergne.denn.terrain.Terrain;

class AgentColorizerTest {
	private static final int TEST_ITERATIONS = 100;
	private static final BiFunction<Terrain, NeuralNetwork.Factory, AgentColorizer> TESTED_COLORIZER //
			= AgentColorizer::pickingOnAttractors;

	static Stream<Integer> terrainSizes() {
		// Only odd size of terrain to have an exact center
		// It simplifies the computation of the center color
		return Stream.of(11, 21, 101);// TODO test 1001 once we have better performances
	}

	static Stream<Arguments> programColors() {
		return terrainSizes().map(terrainSize -> {
			Terrain terrain = Terrain.createWithSize(terrainSize, terrainSize);
			BiFunction<Factory, Terrain, Program> upLeft = program("up left", Factory::upLeftMover);
			BiFunction<Factory, Terrain, Program> upRight = program("up right", Factory::upRightMover);
			BiFunction<Factory, Terrain, Program> downRight = program("down right", Factory::downRightMover);
			BiFunction<Factory, Terrain, Program> downLeft = program("down left", Factory::downLeftMover);
			BiFunction<Factory, Terrain, Program> nonMover = program("non mover", Factory::nonMover);
			BiFunction<Factory, Terrain, Program> random = program("random", Factory::randomMover);
			BiFunction<Factory, Terrain, Program> center = program("center", (factory) -> factory.centerMover(terrain));
			Supplier<Color> orange = color("orange", Color.ORANGE);
			Supplier<Color> red = color("red", Color.RED);
			Supplier<Color> blue = color("blue", Color.BLUE);
			Supplier<Color> cyan = color("cyan", Color.CYAN);
			Supplier<Color> black = color("black", Color.BLACK);
			Supplier<Color> white = color("white", Color.WHITE);
			Supplier<Color> average = color("average color", average(List.of(red, orange, cyan, blue)));
			return Stream.of(//
					arguments(terrain, upLeft, orange), //
					arguments(terrain, upRight, red), //
					arguments(terrain, downRight, blue), //
					arguments(terrain, downLeft, cyan), //
					arguments(terrain, nonMover, black), //
					arguments(terrain, random, white), //
					arguments(terrain, center, average) //
			// FIXME Add bands (up, down, left, right)
			);
		}).flatMap(s -> s);
	}

	private static Color average(List<Supplier<Color>> cornerColors) {
		int red = average(cornerColors, Color::getRed);
		int green = average(cornerColors, Color::getGreen);
		int blue = average(cornerColors, Color::getBlue);
		Color averageColor = new Color(red, green, blue);
		return averageColor;
	}

	private static int average(List<Supplier<Color>> cornerColors, ToIntFunction<Color> channel) {
		return (int) cornerColors.stream().map(Supplier::get).mapToInt(channel).average().getAsDouble();
	}

	@ParameterizedTest
	@MethodSource("programColors")
	void testSpecificProgramLeadsToSpecificColor(Terrain terrain,
			BiFunction<Program.Factory, Terrain, Program> agentProgrammer, Supplier<Color> colorSupplier) {
		Color color = colorSupplier.get();
		NeuralNetwork.Factory networkFactory = createNetworkFactory();
		AgentColorizer colorizer = TESTED_COLORIZER.apply(terrain, networkFactory);

		Program.Factory programFactory = new Program.Factory();
		Program agentProgram = agentProgrammer.apply(programFactory, terrain);
		Agent agent = Agent.createFromProgram(networkFactory, agentProgram);
		Set<Color> colors = IntStream.range(0, TEST_ITERATIONS)//
				.mapToObj(i -> colorizer.colorize(agent))//
				.collect(toSet());

		assertThat(colors, hasItem(color));
	}

	@ParameterizedTest
	@MethodSource("programColors")
	void testSpecificProgramLeadsToNoOtherColor(Terrain terrain,
			BiFunction<Program.Factory, Terrain, Program> agentProgrammer, Supplier<Color> colorSupplier) {
		Color color = colorSupplier.get();
		NeuralNetwork.Factory networkFactory = createNetworkFactory();
		AgentColorizer colorizer = TESTED_COLORIZER.apply(terrain, networkFactory);

		Program.Factory programFactory = new Program.Factory();
		Program agentProgram = agentProgrammer.apply(programFactory, terrain);
		Agent agent = Agent.createFromProgram(networkFactory, agentProgram);
		Set<Color> colors = IntStream.range(0, TEST_ITERATIONS)//
				.mapToObj(i -> colorizer.colorize(agent))//
				.filter(col -> !col.equals(color))//
				.collect(toSet());

		assertThat(colors, is(emptySet()));
	}

	private NeuralNetwork.Factory createNetworkFactory() {
		Random random = new Random(0);
		Supplier<Builder<NeuralNetwork>> generator = () -> new NeuralNetwork.Builder(random::nextDouble);
		NeuralNetwork.Factory networkFactory = new NeuralNetwork.Factory(generator, random);
		return networkFactory;
	}

	private static BiFunction<Program.Factory, Terrain, Program> program(String name,
			Function<Program.Factory, Program> agentProgrammer) {
		return new BiFunction<Program.Factory, Terrain, Program>() {

			@Override
			public Program apply(Program.Factory factory, Terrain terrain) {
				return agentProgrammer.apply(factory);
			}

			@Override
			public String toString() {
				return name;
			}
		};
	}

	private static Supplier<Color> color(String name, Color color) {
		return new Supplier<Color>() {
			@Override
			public Color get() {
				return color;
			}

			@Override
			public String toString() {
				return name;
			}
		};
	}
}
