package ia.agent.adn;

import static ia.agent.NeuralNetwork.Builder2.*;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import ia.agent.NeuralNetwork.Builder2.BuilderStep;

public enum Operation {
	CREATE_WITH_FIXED_SIGNAL(signal -> builder -> builder.createNeuronWith(fixedSignal(signal))), //
	SET_DX(index -> builder -> builder.setDXAt(neuronAt(index.intValue()))), //
	SET_DY(index -> builder -> builder.setDYAt(neuronAt(index.intValue()))),//
	;

	private final Function<Double, BuilderStep> resolver;

	private Operation(Function<Double, BuilderStep> resolver) {
		this.resolver = resolver;
	}

	public BuilderStep resolve(double arg) {
		return resolver.apply(arg);
	}

	public byte serialize() {
		int binarySearch = Arrays.binarySearch(Operation.values(), this);
		return Integer.valueOf(binarySearch).byteValue();
	}

	public static Optional<Operation> deserialize(byte bytes) {
		return Stream.of(Operation.values())//
				.filter(op -> op.serialize() == bytes)//
				.findAny();
	}
}
