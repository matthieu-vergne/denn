package ia.window;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import ia.agent.Agent;
import ia.agent.Neural.Builder;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Program;
import ia.terrain.Move;
import ia.terrain.Position;
import ia.terrain.Terrain;

// TODO Support simple case: fixed network + weights-based color
// TODO Can we do something with wave function collapse? https://www.procjam.com/tutorials/wfc/
// TODO Can we do something with dimensions reduction? https://en.wikipedia.org/wiki/Dimensionality_reduction
// TODO Can we do something with non-linear dimensions reduction? https://en.wikipedia.org/wiki/Nonlinear_dimensionality_reduction
public interface AgentColorizer {

	public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

	public Color colorize(Agent agent);

	// TODO New colorizer based on structure?
	public static AgentColorizer pickingOnChromosome() {
		return agent -> {
			// Adapt bits to avoid long sequences of 0 or 1.
			// It avoids fully transparent/white colors.
			boolean[] invert = { false };
			UnaryOperator<Boolean> bitSwitcher = bit -> {
				invert[0] = !invert[0];
				return bit ^ invert[0];
			};

			int rgba = pickBits(agent.chromosome().bytes(), Integer.SIZE, bitSwitcher).getInt();
			return new Color(rgba, true);
		};
	}

	public static AgentColorizer loopingOnChromosome() {
		return agent -> {
			byte[] chromosomeBytes = agent.chromosome().bytes();
			IntBuffer intBuffer = ByteBuffer.wrap(chromosomeBytes).asIntBuffer();
			int rgba = 0;
			while (intBuffer.remaining() > 0) {
				rgba = rgba ^ intBuffer.get();
			}
			return new Color(rgba, true);
		};
	}

	public static AgentColorizer basedOnStructure() {
		return agent -> {
			byte[] chromosomeBytes = agent.chromosome().bytes();
			Program program = Program.deserialize(chromosomeBytes);
			interface ColorFunction {
				Color compute(List<Color> inputs);
			}
			List<ColorFunction> colorFunctions = new LinkedList<>();
			Color xColor = new Color(255, 0, 0);
			Color yColor = new Color(0, 255, 0);
			colorFunctions.add(inputs -> xColor);
			colorFunctions.add(inputs -> yColor);
			Map<Integer, List<Integer>> indexesMap = new LinkedHashMap<>();
			Builder<Color> colorBuilder = new Builder<Color>() {

				private int currentIndex = 0;
				private int dXIndex = 0;
				private int dYIndex = 1;

				@Override
				public Builder<Color> createNeuronWithFixedSignal(double signal) {
					// TODO Use hue
					colorFunctions.add(inputs -> new Color(Color.HSBtoRGB((float) signal, 1.0f, 1.0f)));
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithRandomSignal() {
					colorFunctions.add(inputs -> Color.MAGENTA);
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithWeightedSumFunction(double weight) {
					colorFunctions.add(inputs -> inputs.stream()//
							.reduce(colorAccumulator((value1, value2) -> normalizeChannel(value1 + value2)))//
							.map(colorAdapter(value -> normalizeChannel((int) (value * weight))))//
							.orElse(TRANSPARENT));
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithSumFunction() {
					return createNeuronWithWeightedSumFunction(1.0);
				}

				@Override
				public Builder<Color> createNeuronWithMinFunction() {
					colorFunctions.add(inputs -> {
						return inputs.stream().reduce(colorAccumulator(Math::min)).orElse(TRANSPARENT);
					});
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithMaxFunction() {
					colorFunctions
							.add(inputs -> inputs.stream().reduce(colorAccumulator(Math::max)).orElse(TRANSPARENT));
					return this;
				}

				@Override
				public Builder<Color> moveTo(int neuronIndex) {
					currentIndex = neuronIndex;
					return this;
				}

				@Override
				public Builder<Color> readSignalFrom(int neuronIndex) {
					indexesMap.computeIfAbsent(currentIndex, k -> new LinkedList<>()).add(neuronIndex);
					return this;
				}

				@Override
				public Builder<Color> setDXAt(int neuronIndex) {
					dXIndex = neuronIndex;
					return this;
				}

				@Override
				public Builder<Color> setDYAt(int neuronIndex) {
					dYIndex = neuronIndex;
					return this;
				}

				@Override
				public Color build() {
					List<Color> colors = new ArrayList<>(colorFunctions.size());
					for (int neuronIndex = 0; neuronIndex < colorFunctions.size(); neuronIndex++) {
						colors.add(TRANSPARENT);
					}
					int size = colors.size();
					UnaryOperator<Integer> indexNormalizer = index -> normalize(index, size);

					for (int neuronIndex = 0; neuronIndex < colorFunctions.size(); neuronIndex++) {
						List<Integer> inputIndexes = indexesMap.computeIfAbsent(neuronIndex, k -> emptyList());
						List<Color> inputColors = inputIndexes == null //
								? emptyList()//
								: inputIndexes.stream().map(indexNormalizer).map(colors::get).collect(toList());
						Color neuronColor = colorFunctions.get(neuronIndex).compute(inputColors);
						colors.set(neuronIndex, neuronColor);
					}

					Integer dXIndex = indexNormalizer.apply(this.dXIndex);
					Integer dYIndex = indexNormalizer.apply(this.dYIndex);
					Color dXColor = colors.get(dXIndex);
					Color dYColor = colors.get(dYIndex);
					Color color = List.of(dXColor, dYColor).stream().reduce(colorAccumulator((a, b) -> (a + b) / 2))
							.orElse(TRANSPARENT);
					return color;
				}

			};
			program.executeOn(colorBuilder);
			return colorBuilder.build();
		};
	}

	public static AgentColorizer pickingOnWeights() {
		return agent -> {
			Builder<Color> colorBuilder = new Builder<Color>() {

				private final List<Double> weights = new LinkedList<>();

				@Override
				public Builder<Color> createNeuronWithFixedSignal(double signal) {
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithRandomSignal() {
					return this;
				}
				
				@Override
				public Builder<Color> createNeuronWithWeightedSumFunction(double weight) {
					weights.add(weight);
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithSumFunction() {
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithMinFunction() {
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithMaxFunction() {
					return this;
				}

				@Override
				public Builder<Color> moveTo(int neuronIndex) {
					return this;
				}

				@Override
				public Builder<Color> readSignalFrom(int neuronIndex) {
					return this;
				}

				@Override
				public Builder<Color> setDXAt(int neuronIndex) {
					return this;
				}

				@Override
				public Builder<Color> setDYAt(int neuronIndex) {
					return this;
				}

				@Override
				public Color build() {
					if (weights.isEmpty()) {
						return Color.BLACK;
					}

					ByteBuffer buffer = weights.stream().collect(//
							() -> ByteBuffer.allocate(Double.BYTES * weights.size()), //
							ByteBuffer::putDouble, //
							(buffer1, buffer2) -> {
								buffer1.put(buffer2.flip());
							});
					int rgba = pickBits(buffer.array(), Integer.SIZE, UnaryOperator.identity()).getInt();
					return new Color(rgba, false);
				}
			};
			byte[] chromosomeBytes = agent.chromosome().bytes();
			Program program = Program.deserialize(chromosomeBytes);
			program.executeOn(colorBuilder);
			Color color = colorBuilder.build();
			return color;
		};
	}

	public static AgentColorizer pickingOnBehaviour(Terrain terrain, NeuralNetwork.Factory networkFactory) {
		boolean[] bitsMemory = new boolean[4];
		Runnable bitsMemoryReset = () -> {
			Arrays.fill(bitsMemory, false);
		};
		Function<BiFunction<Move, Boolean, Boolean>, Function<Move, Boolean>> previouser = new Function<BiFunction<Move, Boolean, Boolean>, Function<Move, Boolean>>() {
			private int index = 0;

			@Override
			public Function<Move, Boolean> apply(BiFunction<Move, Boolean, Boolean> function) {
				int previousIndex = index++;
				return move -> {
					Boolean bit = function.apply(move, bitsMemory[previousIndex]);
					bitsMemory[previousIndex] = bit;
					return bit;
				};
			}
		};
		Function<Move, Boolean> redBit = previouser.apply((move, previous) -> {
			int dX = move.dX();
			return dX > 0 ? true //
					: dX < 0 ? false //
							: previous;
		});
		Function<Move, Boolean> greenBit = previouser.apply((move, previous) -> {
			int dY = move.dY();
			return dY > 0 ? true //
					: dY < 0 ? false //
							: previous;
		});
		Function<Move, Boolean> blueBit = previouser.apply((move, previous) -> {
			int dX = move.dX();
			int dY = move.dY();
			return dX > dY ? true //
					: dX < dY ? false //
							: previous;
		});
		Function<Move, Boolean> alphaBit = previouser.apply((move, previous) -> {
			int dX = move.dX();
			int dY = move.dY();
			return dX == dY;
		});

		int redMinIndex = 0;
		int greenMinIndex = Byte.SIZE;
		int blueMinIndex = Byte.SIZE * 2;
		int alphaMinIndex = Byte.SIZE * 3;

		BitSet bits = new BitSet(4 * Byte.SIZE);

		AgentColorizer agentColorizer = agent -> {
			byte[] bytes = agent.chromosome().bytes();
			Program program = Program.deserialize(bytes);
			NeuralNetwork network = networkFactory.execute(program);

			int stepX = (terrain.width() - 1) / (Byte.SIZE - 1);
			int stepY = (terrain.height() - 1) / (Byte.SIZE - 1);

			bits.clear();
			bitsMemoryReset.run();
			int x = 0;
			int y = 0;
			for (int i = 0; i < Byte.SIZE; i++) {
				network.setInputs(Position.at(x, y));
				network.fire();
				Move move = network.output();

				bits.set(redMinIndex + i, redBit.apply(move));
				bits.set(greenMinIndex + i, greenBit.apply(move));
				bits.set(blueMinIndex + i, blueBit.apply(move));
				bits.set(alphaMinIndex + i, alphaBit.apply(move));

				x += stepX;
				y += stepY;
			}

			ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
			buffer.mark();
			buffer.put(bits.toByteArray());
			buffer.reset();
			int rgba = buffer.getInt();

			return new Color(rgba);
		};

		WeakHashMap<Agent, Color> cache = new WeakHashMap<Agent, Color>();
		AgentColorizer cachedColorizer = agent -> cache.computeIfAbsent(agent, agentColorizer::colorize);

		return cachedColorizer;
	}

	private static String stringOf(Color color) {
		return Arrays.toString(color.getComponents(null));
	}

	private static String stringOf(Collection<Color> colors) {
		return colors.stream().map(AgentColorizer::stringOf).collect(joining(" "));
	}

	private static BinaryOperator<Color> colorAccumulator(BinaryOperator<Integer> channelAccumulator) {
		return (c1, c2) -> new Color(//
				channelAccumulator.apply(c1.getRed(), c2.getRed()), //
				channelAccumulator.apply(c1.getGreen(), c2.getGreen()), //
				channelAccumulator.apply(c1.getBlue(), c2.getBlue())//
		);
	}

	private static UnaryOperator<Color> colorAdapter(UnaryOperator<Integer> channelAdapter) {
		return c -> new Color(//
				channelAdapter.apply(c.getRed()), //
				channelAdapter.apply(c.getGreen()), //
				channelAdapter.apply(c.getBlue())//
		);
	}

	private static int normalize(int value, int size) {
		return (((int) value % size) + size) % size;
	}

	private static int normalizeChannel(int value) {
		return normalize(value, 256);
	}

	private static ByteBuffer pickBits(byte[] inputBytes, int outputBitsCount, UnaryOperator<Boolean> bitAdapter) {
		if (inputBytes.length == 0) {
			throw new IllegalArgumentException("No bytes to pick from");
		}
		int inputBitsCount = inputBytes.length * Byte.SIZE;
		BitSet inputBits = BitSet.valueOf(inputBytes);
		BitSet outputBits = new BitSet(outputBitsCount);
		double indexConversionFactor = (double) (inputBitsCount - 1) / (outputBitsCount - 1);
		for (int outputBitIndex = 0; outputBitIndex < outputBitsCount; outputBitIndex++) {
			int inputBitIndex = (int) (indexConversionFactor * outputBitIndex);
			boolean bit = inputBits.get(inputBitIndex);
			outputBits.set(outputBitIndex, bitAdapter.apply(bit));
		}

		byte[] outputBytes = Arrays.copyOf(outputBits.toByteArray(), outputBitsCount);
		return ByteBuffer.wrap(outputBytes);
	}
}
