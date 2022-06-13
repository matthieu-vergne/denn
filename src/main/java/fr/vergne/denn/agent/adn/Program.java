package fr.vergne.denn.agent.adn;

import static java.util.Collections.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.UnaryOperator;

import fr.vergne.denn.agent.LayeredNetwork;
import fr.vergne.denn.agent.Neural;
import fr.vergne.denn.terrain.Terrain;
import fr.vergne.denn.utils.Position;

public record Program(List<Code> codes) {

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

	public static class Factory {

		public Factory() {
		}

		// TODO Add hidden layer
		public Program createPerceptrons(UnaryOperator<LayeredNetwork.Layer> dxWeighter,
				UnaryOperator<LayeredNetwork.Layer> dyWeighter) {
			LayeredNetwork.Programmer programmer = new LayeredNetwork.Programmer();
			LayeredNetwork.Layer inputs = programmer.layerOf().x().y().constant(1.0).rand().rand();
			return programmer//
					.setDx(programmer.sum(dxWeighter.apply(inputs)))//
					.setDy(programmer.sum(dyWeighter.apply(inputs)))//
					.program();
		}

		public Program nonMover() {
			return createPerceptrons(//
					inputs -> inputs.weighted(0, 0, 0, 0, 0), //
					inputs -> inputs.weighted(0, 0, 0, 0, 0)//
			);
		}

		public Program downMover() {
			return createPerceptrons(//
					inputs -> inputs.weighted(0, 0, 0, 0, 0), //
					inputs -> inputs.weighted(0, 0, 1, 0, 0)//
			);
		}

		public Program downLeftMover() {
			return createPerceptrons(//
					inputs -> inputs.weighted(0, 0, -1, 0, 0), //
					inputs -> inputs.weighted(0, 0, 1, 0, 0)//
			);
		}

		public Program downRightMover() {
			return createPerceptrons(//
					inputs -> inputs.weighted(0, 0, 1, 0, 0), //
					inputs -> inputs.weighted(0, 0, 1, 0, 0)//
			);
		}

		public Program upMover() {
			return createPerceptrons(//
					inputs -> inputs.weighted(0, 0, 0, 0, 0), //
					inputs -> inputs.weighted(0, 0, -1, 0, 0)//
			);
		}

		public Program upLeftMover() {
			return createPerceptrons(//
					inputs -> inputs.weighted(0, 0, -1, 0, 0), //
					inputs -> inputs.weighted(0, 0, -1, 0, 0)//
			);
		}

		public Program upRightMover() {
			return createPerceptrons(//
					inputs -> inputs.weighted(0, 0, 1, 0, 0), //
					inputs -> inputs.weighted(0, 0, -1, 0, 0)//
			);
		}

		public Program rightMover() {
			return createPerceptrons(//
					inputs -> inputs.weighted(0, 0, 1, 0, 0), //
					inputs -> inputs.weighted(0, 0, 0, 0, 0)//
			);
		}

		public Program leftMover() {
			return createPerceptrons(//
					inputs -> inputs.weighted(0, 0, -1, 0, 0), //
					inputs -> inputs.weighted(0, 0, 0, 0, 0)//
			);
		}

		public Program positionMover(Position position) {
			return createPerceptrons(//
					inputs -> inputs.weighted(-1, 0, position.x(), 0, 0), //
					inputs -> inputs.weighted(0, -1, position.y(), 0, 0)//
			);
		}

		public Program centerMover(Terrain terrain) {
			return createPerceptrons(//
					inputs -> inputs.weighted(-1, 0, terrain.width() / 2, 0, 0), //
					inputs -> inputs.weighted(0, -1, terrain.height() / 2, 0, 0)//
			);
		}

		public Program cornerMover(Terrain terrain) {
			return createPerceptrons(//
					inputs -> inputs.weighted(1, 0, -terrain.width() / 2, 0, 0), //
					inputs -> inputs.weighted(0, 1, -terrain.height() / 2, 0, 0)//
			);
		}

		public Program randomMover() {
			return createPerceptrons(//
					inputs -> inputs.weighted(0, 0, -1, 2, 0), //
					inputs -> inputs.weighted(0, 0, -1, 0, 2)//
			);
		}

	}

	public static Program noop() {
		return new Program(emptyList());
	}
}
