package ia.agent.adn;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import ia.agent.NeuralNetwork.Builder2.BuilderStep;

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

	public static byte[] serializeAll(List<Code> codes) {
		ByteBuffer writeBuffer = ByteBuffer.allocate(codes.size() * Code.SIZE);
		codes.stream().map(Code::serialize).forEach(writeBuffer::put);
		return writeBuffer.array();
	}

	public static List<Code> deserializeAll(byte[] chromosomeBytes) {
		ByteBuffer readBuffer = ByteBuffer.wrap(chromosomeBytes);
		List<Code> decodedCodes = new LinkedList<>();
		while (readBuffer.remaining() >= Code.SIZE) {
			byte[] bytes = new byte[Code.SIZE];
			readBuffer.get(bytes);
			decodedCodes.add(Code.deserialize(bytes));
		}
		return decodedCodes;
	}
}
