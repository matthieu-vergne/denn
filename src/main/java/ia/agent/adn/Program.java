package ia.agent.adn;

import static java.util.Collections.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import ia.agent.Neural;

public class Program {

	private final List<Code> codes;

	public Program(List<Code> codes) {
		this.codes = codes;
	}

	public List<Code> codes() {
		return codes;
	}

	public void executeOn(Neural.Builder<?> builder) {
		this.codes.stream().map(Code::resolve).forEach(step -> step.apply(builder));
	}

	public byte[] serialize() {
		ByteBuffer writeBuffer = ByteBuffer.allocate(codes.size() * Code.SIZE);
		codes.stream().map(Code::serialize).forEach(writeBuffer::put);
		return writeBuffer.array();
	}

	public static Program deserialize(byte[] bytes) {
		ByteBuffer readBuffer = ByteBuffer.wrap(bytes);
		List<Code> codes = new LinkedList<>();
		while (readBuffer.remaining() >= Code.SIZE) {
			byte[] codeBytes = new byte[Code.SIZE];
			readBuffer.get(codeBytes);
			codes.add(Code.deserialize(codeBytes));
		}
		return new Program(codes);
	}

	public static class Builder implements Neural.Builder<Program> {
		private final List<Code> codes = new LinkedList<>();
		
		@Override
		public Builder createNeuronWithRandomSignal() {
			codes.add(new Code(Operation.CREATE_WITH_RANDOM_SIGNAL, 0.0));
			return this;
		}

		@Override
		public Builder createNeuronWithFixedSignal(double signal) {
			codes.add(new Code(Operation.CREATE_WITH_FIXED_SIGNAL, signal));
			return this;
		}

		@Override
		public Builder createNeuronWithWeightedSumFunction(double weight) {
			codes.add(new Code(Operation.CREATE_WITH_WEIGHTED_SUM_FUNCTION, weight));
			return this;
		}

		@Override
		public Builder createNeuronWithSumFunction() {
			codes.add(new Code(Operation.CREATE_WITH_SUM_FUNCTION, 0.0));
			return this;
		}

		@Override
		public Builder createNeuronWithMinFunction() {
			codes.add(new Code(Operation.CREATE_WITH_MIN_FUNCTION, 0.0));
			return this;
		}

		@Override
		public Builder createNeuronWithMaxFunction() {
			codes.add(new Code(Operation.CREATE_WITH_MAX_FUNCTION, 0.0));
			return this;
		}

		@Override
		public Builder moveTo(int neuronIndex) {
			codes.add(new Code(Operation.MOVE_TO, (double) neuronIndex));
			return this;
		}

		@Override
		public Builder readSignalFrom(int neuronIndex) {
			codes.add(new Code(Operation.READ_SIGNAL_FROM, (double) neuronIndex));
			return this;
		}

		@Override
		public Builder setDXAt(int neuronIndex) {
			codes.add(new Code(Operation.SET_DX, (double) neuronIndex));
			return this;
		}

		@Override
		public Builder setDYAt(int neuronIndex) {
			codes.add(new Code(Operation.SET_DY, (double) neuronIndex));
			return this;
		}

		@Override
		public Program build() {
			return new Program(new ArrayList<>(codes));
		}

	}

	public static Program noop() {
		return new Program(emptyList());
	}
}
