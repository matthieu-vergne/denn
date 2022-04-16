package ia.agent.adn;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import ia.agent.NeuralNetwork.Builder.BuilderStep;

public enum Operation {
	CREATE_WITH_FIXED_SIGNAL(signal -> builder -> builder.createNeuronWithFixedSignal(signal)), //
	CREATE_WITH_WEIGHTED_SUM_FUNCTION(signal -> builder -> builder.createNeuronWithWeightedSumFunction(signal)), //
	CREATE_WITH_SUM_FUNCTION(signal -> builder -> builder.createNeuronWithSumFunction()), //
	CREATE_WITH_MIN_FUNCTION(signal -> builder -> builder.createNeuronWithMinFunction()), //
	CREATE_WITH_MAX_FUNCTION(signal -> builder -> builder.createNeuronWithMaxFunction()), //
	MOVE_TO(signal -> builder -> builder.moveTo(signal.intValue())), //
	READ_SIGNAL_FROM(signal -> builder -> builder.readSignalFrom(signal.intValue())), //
	SET_DX(index -> builder -> builder.setDXAt(index.intValue())), //
	SET_DY(index -> builder -> builder.setDYAt(index.intValue())),//
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

	public static Optional<Operation> deserialize(byte bits) {
		int normalizedBits = ((bits % values().length) + values().length) % values().length;
		return Stream.of(Operation.values())//
				.filter(op -> op.serialize() == normalizedBits)//
				.findAny();
	}

}
