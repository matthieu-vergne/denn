package ia.agent.adn;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;
import java.util.stream.IntStream;

@FunctionalInterface
public interface Mutator {

	Chromosome mutate(Chromosome chromosome);

	static Mutator createWithoutMutation() {
		return chromosomeChild -> chromosomeChild;
	}

	static Mutator withProbability(Random random, double probability) {
		return chromosome -> {
			byte[] bytes = chromosome.bytes();
			BitSet bitSet = BitSet.valueOf(bytes);
			IntStream.range(0, bytes.length * Byte.SIZE).forEach(index -> {
				if (random.nextDouble() < probability) {
					bitSet.flip(index);
				}
			});
			byte[] mutatedBytes = Arrays.copyOf(bitSet.toByteArray(), bytes.length);
			return new Chromosome(mutatedBytes);
		};
	}

}
