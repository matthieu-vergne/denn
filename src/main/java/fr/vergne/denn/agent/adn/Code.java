package fr.vergne.denn.agent.adn;

import java.nio.ByteBuffer;

import fr.vergne.denn.agent.NeuralNetwork.Builder.BuilderStep;

public record Code(Operation operation, Double value) {
	public static final int SIZE = 1 + Double.BYTES;

	public byte[] serialize() {
		ByteBuffer buffer = ByteBuffer.allocate(SIZE);
		buffer.put(operation().serialize());
		buffer.putDouble(value());
		return buffer.array();
	}

	public BuilderStep resolve() {
		return operation().resolve(value());
	}

	public static Code deserialize(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		byte encodedOperation = buffer.get();
		Operation operation = Operation.deserialize(encodedOperation).orElseThrow(() -> {
			return new IllegalArgumentException("No operation mapped to code " + encodedOperation);
		});
		double value = buffer.getDouble();
		return new Code(operation, value);
	}

}
