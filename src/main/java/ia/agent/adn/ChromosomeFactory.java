package ia.agent.adn;

import static ia.agent.NeuralNetwork.Builder.*;

import java.awt.Color;
import java.util.Random;
import java.util.function.Supplier;

import ia.agent.NeuralNetwork.Neuron;
import ia.terrain.Position;
import ia.terrain.Terrain;
import ia.window.Colorizer;

public class ChromosomeFactory {
	private final Random random;

	public ChromosomeFactory(Random random) {
		this.random = random;
	}

	public Chromosome stay() {
		return new ChromosomeBuilder()//
				.set(neuronDX(), Neuron.withoutInputs(0.0), Colorizer.onAlpha(128))//
				.set(neuronDY(), Neuron.withoutInputs(0.0), Colorizer.onAlpha(128))//
				.build();
	}

	public Chromosome moveUp() {
		return new ChromosomeBuilder()//
				.set(neuronDX(), Neuron.withoutInputs(0.0), Colorizer.onAlpha(128))//
				.set(neuronDY(), Neuron.withoutInputs(-1.0), Colorizer.onRed(0))//
				.build();
	}

	public Chromosome moveUpLeft() {
		return new ChromosomeBuilder()//
				.set(neuronDX(), Neuron.withoutInputs(-1.0), Colorizer.onBlue(255))//
				.set(neuronDY(), Neuron.withoutInputs(-1.0), Colorizer.onRed(0))//
				.build();
	}

	public Chromosome moveUpRight() {
		return new ChromosomeBuilder()//
				.set(neuronDX(), Neuron.withoutInputs(1.0), Colorizer.onBlue(0))//
				.set(neuronDY(), Neuron.withoutInputs(-1.0), Colorizer.onRed(0))//
				.build();
	}

	public Chromosome moveRight() {
		return new ChromosomeBuilder()//
				.set(neuronDX(), Neuron.withoutInputs(1.0), Colorizer.onBlue(0))//
				.set(neuronDY(), Neuron.withoutInputs(0.0), Colorizer.onAlpha(128))//
				.build();
	}

	public Chromosome moveLeft() {
		return new ChromosomeBuilder()//
				.set(neuronDX(), Neuron.withoutInputs(-1.0), Colorizer.onBlue(255))//
				.set(neuronDY(), Neuron.withoutInputs(0.0), Colorizer.onAlpha(128))//
				.build();
	}

	public Chromosome moveDown() {
		return new ChromosomeBuilder()//
				.set(neuronDX(), Neuron.withoutInputs(0.0), Colorizer.onAlpha(128))//
				.set(neuronDY(), Neuron.withoutInputs(1.0), Colorizer.onRed(255))//
				.build();
	}

	public Chromosome moveDownLeft() {
		return new ChromosomeBuilder()//
				.set(neuronDX(), Neuron.withoutInputs(-1.0), Colorizer.onBlue(255))//
				.set(neuronDY(), Neuron.withoutInputs(1.0), Colorizer.onRed(255))//
				.build();
	}

	public Chromosome moveDownRight() {
		return new ChromosomeBuilder()//
				.set(neuronDX(), Neuron.withoutInputs(1.0), Colorizer.onBlue(0))//
				.set(neuronDY(), Neuron.withoutInputs(1.0), Colorizer.onRed(255))//
				.build();
	}

	public Chromosome moveRandomly() {
		return new ChromosomeBuilder()//
				.set(neuronDX(), Neuron.withoutInputs(randomThreeStatesSignalSource()),
						Colorizer.fromColor(Color.GREEN))//
				.set(neuronDY(), Neuron.withoutInputs(randomThreeStatesSignalSource()),
						Colorizer.fromColor(Color.GREEN))//
				.build();
	}

	public Chromosome moveToward(Position position, Terrain terrain) {
		Position maxPosition = terrain.maxPosition();
		int blue = (-255 * position.x / maxPosition.x) + 255;
		int red = 255 * position.y / maxPosition.y;
		return new ChromosomeBuilder()//
				.set(neuron("targetX"), Neuron.withoutInputs(position.x), Colorizer.onBlue(blue))//
				.set(neuron("targetY"), Neuron.withoutInputs(position.y), Colorizer.onRed(red))//
				.set(neuron("diffX"), asDiffOf(neuron("targetX"), neuronX()), Colorizer.off())//
				.set(neuron("diffY"), asDiffOf(neuron("targetY"), neuronY()), Colorizer.off())//
				.set(neuron("boundX"), asBounded(neuron("diffX"), -1.0, 1.0), Colorizer.off())//
				.set(neuron("boundY"), asBounded(neuron("diffY"), -1.0, 1.0), Colorizer.off())//
				.set(neuronDX(), as(neuron("boundX")), Colorizer.off())//
				.set(neuronDY(), as(neuron("boundY")), Colorizer.off())//
				.build();
	}

	private Supplier<Double> randomThreeStatesSignalSource() {
		return () -> (double) random.nextInt(3) - 1;
	}
}
