package ia.window;

import static java.util.stream.Collectors.*;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import ia.agent.Agent;
import ia.agent.adn.Chromosome;

public interface AgentColorizer {

	public Color colorize(Agent agent);

	public static AgentColorizer basedOnChromosome() {
		return agent -> {
			Chromosome chromosome = agent.chromosome();
			List<Colorizer> colorizers = chromosome.genes()//
					.map(gene -> gene.colorizer())//
					.collect(toList());

			int red = average(colorizers, Colorizer::red);
			int green = average(colorizers, Colorizer::green);
			int blue = average(colorizers, Colorizer::blue);
			return new Color(red, green, blue);
		};
	}

	private static int average(List<Colorizer> colorizers, Function<Colorizer, Optional<Integer>> componentExtractor) {
		double average = colorizers.stream()//
				.map(componentExtractor)//
				.filter(Optional<Integer>::isPresent)//
				.mapToInt(Optional<Integer>::get)//
				.average().orElseGet(() -> 0);
		return (int) Math.round(average);
	}

	public static Float hueValue(Color color) {
		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
		return hsb[0];
	}

}
