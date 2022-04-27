package ia.agent.adn;

import static java.util.stream.Collectors.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

@FunctionalInterface
public interface Mutator {

	Chromosome mutate(Chromosome chromosome);

	static Mutator createWithoutMutation() {
		return chromosomeChild -> chromosomeChild;
	}

	static Mutator onBits(Random random, double probabilityPerBit) {
		return chromosome -> new Chromosome(mutateBits(chromosome.bytes(), random, probabilityPerBit));
	}

	static Mutator onWeights(Random random, double probabilityPerBit) {
		return chromosome -> {
			byte[] bytes = chromosome.bytes();
			Program program = Program.deserialize(bytes);
			List<Code> mutatedCodes = program.codes().stream()//
					.map(code -> {
						if (!code.operation().equals(Operation.CREATE_WITH_WEIGHTED_SUM_FUNCTION)) {
							return code;
						}
						
						ByteBuffer weightBuffer = ByteBuffer.allocate(Double.BYTES);
						Double weight = code.value();
						byte[] weightBytes = weightBuffer.putDouble(weight).flip().array();
						byte[] mutatedBytes = mutateBits(weightBytes, random, probabilityPerBit);
						double mutatedWeight = weightBuffer.put(mutatedBytes).flip().getDouble();
						return new Code(code.operation(), mutatedWeight);
					})//
					.collect(toList());
			byte[] mutatedBytes = new Program(mutatedCodes).serialize();

			return new Chromosome(mutatedBytes);
		};
	}

	static byte[] mutateBits(byte[] bytes, Random random, double probabilityPerBit) {
		BitSet bitSet = BitSet.valueOf(bytes);
		IntStream.range(0, bytes.length * Byte.SIZE).forEach(index -> {
			if (random.nextDouble() < probabilityPerBit) {
				bitSet.flip(index);
			}
		});
		return Arrays.copyOf(bitSet.toByteArray(), bytes.length);
	}
}
