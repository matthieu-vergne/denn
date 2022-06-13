package fr.vergne.denn.agent;

import java.util.function.Supplier;

import fr.vergne.denn.utils.Position;

public interface Neural<T> {

	public interface Builder<T> {

		Builder<T> createNeuronWithFixedSignal(double signal);

		Builder<T> createNeuronWithWeightedSumFunction(double weight);

		Builder<T> createNeuronWithRandomSignal();

		Builder<T> createNeuronWithSumFunction();

		Builder<T> createNeuronWithMinFunction();

		Builder<T> createNeuronWithMaxFunction();

		Builder<T> moveTo(int neuronIndex);

		Builder<T> readSignalFrom(int neuronIndex);

		Builder<T> setDXAt(int neuronIndex);

		Builder<T> setDYAt(int neuronIndex);

		T build();
	}

	public static interface Factory<T> {

		T moveDownRight();

		T moveUpLeft();

		T moveToward(Position position);

		public static <T> Factory<T> on(Supplier<Builder<T>> builderGenerator) {
			return new Factory<T>() {

				@Override
				public T moveDownRight() {
					return builderGenerator.get()//
							.createNeuronWithFixedSignal(1.0)//
							.setDXAt(2)//
							.createNeuronWithFixedSignal(1.0)//
							.setDYAt(3)//
							.build();
				}

				@Override
				public T moveUpLeft() {
					return builderGenerator.get()//
							.createNeuronWithFixedSignal(-1.0)//
							.setDXAt(2)//
							.createNeuronWithFixedSignal(-1.0)//
							.setDYAt(3)//
							.build();
				}

				@Override
				public T moveToward(Position position) {
					return builderGenerator.get()//
							// targetX
							.createNeuronWithFixedSignal(position.x())//
							// diffX
							.createNeuronWithWeightedSumFunction(-1.0)//
							.moveTo(3)//
							.readSignalFrom(0)//
							.createNeuronWithSumFunction()//
							.moveTo(4)//
							.readSignalFrom(3)//
							.readSignalFrom(2)//
							// boundX - lower bound
							.createNeuronWithFixedSignal(-1.0)//
							.createNeuronWithMaxFunction()//
							.moveTo(6)//
							.readSignalFrom(5)//
							.readSignalFrom(4)//
							// boundX - upper bound
							.createNeuronWithFixedSignal(1.0)//
							.createNeuronWithMinFunction()//
							.moveTo(8)//
							.readSignalFrom(7)//
							.readSignalFrom(6)//
							// DX
							.setDXAt(8)//

							// targetY
							.createNeuronWithFixedSignal(position.y())//
							// diffY
							.createNeuronWithWeightedSumFunction(-1.0)//
							.moveTo(10)//
							.readSignalFrom(1)//
							.createNeuronWithSumFunction()//
							.moveTo(11)//
							.readSignalFrom(10)//
							.readSignalFrom(9)//
							// boundY - lower bound
							// TODO Reuse -1 from boundX
							.createNeuronWithFixedSignal(-1.0)//
							.createNeuronWithMaxFunction()//
							.moveTo(13)//
							.readSignalFrom(12)//
							.readSignalFrom(11)//
							// boundY - upper bound
							// TODO Reuse 1 from boundX
							.createNeuronWithFixedSignal(1.0)//
							.createNeuronWithMinFunction()//
							.moveTo(15)//
							.readSignalFrom(14)//
							.readSignalFrom(13)//
							// DY
							.setDYAt(15)//

							.build();
				}
			};
		}
	}
}