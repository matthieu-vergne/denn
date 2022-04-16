package ia.window;

import static java.lang.Math.*;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.UnaryOperator;

import ia.agent.Agent;
import ia.agent.NeuralNetwork;
import ia.agent.Neural.Builder;
import ia.agent.adn.Program;

public interface AgentColorizer {

	public Color colorize(Agent agent);

	// TODO New colorizer based on structure?
	public static AgentColorizer basedOnChromosome() {
		return agent -> {
			byte[] chromosomeBytes = agent.chromosome().bytes();
			int chromosomeBitsCount = chromosomeBytes.length * Byte.SIZE;
			int rgbaBitsCount = Integer.SIZE;

			// Adapt bits to avoid long sequences of 0 or 1.
			// It avoids fully transparent/white colors.
			boolean[] invert = { false };
			UnaryOperator<Boolean> adapter = bit -> {
				invert[0] = !invert[0];
				return bit ^ invert[0];
			};

			BitSet chromosomeBits = BitSet.valueOf(chromosomeBytes);
			BitSet rgbaBits = new BitSet(rgbaBitsCount);
			double indexConversionFactor = (double) (chromosomeBitsCount - 1) / (rgbaBitsCount - 1);
			for (int rgbaIndex = 0; rgbaIndex < rgbaBitsCount; rgbaIndex++) {
				int chromosomeIndex = (int) (indexConversionFactor * rgbaIndex);
				boolean bit = chromosomeBits.get(chromosomeIndex);
				rgbaBits.set(rgbaIndex, adapter.apply(bit));
			}

			byte[] rgbaBytes = Arrays.copyOf(rgbaBits.toByteArray(), rgbaBitsCount);
			int rgba = ByteBuffer.wrap(rgbaBytes).getInt();
			return new Color(rgba, true);
		};
	}

	public static AgentColorizer basedOnChromosome2() {
		return agent -> {
			byte[] chromosomeBytes = agent.chromosome().bytes();
			IntBuffer intBuffer = ByteBuffer.wrap(chromosomeBytes).asIntBuffer();
			int rgba = 0;
			while (intBuffer.remaining() > 0) {
				rgba = rgba ^ intBuffer.get();
			}
			;
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
			List<ColorFunction> colors = new LinkedList<>();
			Color xColor = new Color(255, 0, 0, 1);
			Color yColor = new Color(0, 255, 0, 1);
			Color fixedColor = new Color(0, 0, 255, 1);
			colors.add(inputs -> xColor);
			colors.add(inputs -> yColor);
			Builder<Color> colorBuilder = new Builder<Color>() {

				private int currentIndex = 0;
				private int dXIndex = 0;
				private int dYIndex = 0;

				@Override
				public Builder<Color> createNeuronWithFixedSignal(double signal) {
					colors.add(inputs -> {
						int red = fixedColor.getRed();
						int green = fixedColor.getGreen();
						int blue = fixedColor.getBlue();
						int alpha = (int) (fixedColor.getAlpha() * signal) % 256;
						return new Color(red, green, blue, alpha);
					});
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithWeightedSumFunction(double weight) {
					colors.add(inputs -> {
						int red = 0;
						int green = 0;
						int blue = 0;
						int alpha = 0;
						for (Color color : inputs) {
							red = max(red, color.getRed());
							green = max(green, color.getGreen());
							blue = max(blue, color.getBlue());
							alpha = alpha + color.getAlpha();
						}
						alpha /= inputs.size();
						alpha *= weight;
						alpha = min(alpha, 255);
						return new Color(red, green, blue, alpha);
					});
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithSumFunction() {
					return createNeuronWithWeightedSumFunction(1.0);
				}

				private final Comparator<Color> minAlpha = (c1, c2) -> c1.getAlpha() - c2.getAlpha();

				@Override
				public Builder<Color> createNeuronWithMinFunction() {
					colors.add(inputs -> inputs.stream().sorted(minAlpha).findFirst().orElse(Color.BLACK));
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithMaxFunction() {
					colors.add(inputs -> inputs.stream().sorted(minAlpha.reversed()).findFirst().orElse(Color.BLACK));
					return this;
				}

				@Override
				public Builder<Color> moveTo(int neuronIndex) {
					currentIndex = neuronIndex;
					return this;
				}

				@Override
				public Builder<Color> readSignalFrom(int neuronIndex) {
					// TODO Auto-generated method stub
					return this;
				}

				@Override
				public Builder<Color> setDXAt(int neuronIndex) {
					dXIndex = neuronIndex;
					return this;
				}

				@Override
				public Builder<Color> setDYAt(int neuronIndex) {
					// TODO Auto-generated method stub
					return this;
				}

				@Override
				public Color build() {
					// TODO Auto-generated method stub
					return null;
				}

			};
			program.executeOn(colorBuilder);
			return colorBuilder.build();
		};
	}
}
