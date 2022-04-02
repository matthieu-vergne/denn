package ia.window;

import java.awt.Color;
import java.nio.ByteBuffer;

import ia.agent.Agent;
import ia.agent.adn.Chromosome;

public interface AgentColorizer {

	public Color colorize(Agent agent);

	public static AgentColorizer basedOnChromosome() {
		return agent -> {
			Chromosome chromosome = agent.chromosome();
			ByteBuffer buffer = ByteBuffer.wrap(chromosome.bytes());
			int rgb = 0;
			while (buffer.remaining() >= Integer.BYTES) {
				rgb += buffer.getInt();
			}
			return new Color(rgb);
		};
	}

	public static Float hueValue(Color color) {
		float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
		return hsb[0];
	}

}
