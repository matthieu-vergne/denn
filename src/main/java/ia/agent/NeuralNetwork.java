package ia.agent;

import static ia.agent.NeuralNetwork.Builder.*;
import static java.util.stream.Collectors.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.DoubleStream;

import ia.agent.adn.Code;
import ia.terrain.Move;
import ia.terrain.Position;

public interface NeuralNetwork {

	void setInputs(Position position);

	void fire();

	Move output();

	public static record NeuronPair(Object input, Object output) {
	};

	public static interface Neuron2 {
		void fire(List<Double> inputs);

		double signal();

		public static Neuron2 onInputsFunction(Function<List<Double>, Double> function) {
			return new Neuron2() {
				private double signal;

				@Override
				public void fire(List<Double> inputs) {
					signal = function.apply(inputs);
				}

				@Override
				public double signal() {
					return signal;
				}
			};
		}

		public static Neuron2 onSignalSupplier(Supplier<Double> supplier) {
			return onInputsFunction(inputs -> supplier.get());
		}

		public static Neuron2 onFixedSignal(double signal) {
			return new Neuron2() {
				@Override
				public void fire(List<Double> inputs) {
					// Nothing to compute
				}

				@Override
				public double signal() {
					return signal;
				}
			};
		}
	}

	// TODO Remove X/Y in favor of general inputs?
	// TODO Remove DX/DY in favor of letting read signal of any neuron?
	public static class Builder {
		private final List<Neuron2> neurons = new LinkedList<>();
		private final Map<Integer, List<Integer>> inputsMap = new HashMap<>();
		private int currentNeuronIndex = 0;
		private Integer dXIndex = null;
		private Integer dYIndex = null;

		public Builder() {
			Function<List<Double>, Double> noFunctionYet = inputs -> {
				throw new IllegalStateException("Reserved neuron not replaced yet");
			};
			createNeuronWith(noFunctionYet);// Reserve index for X
			createNeuronWith(noFunctionYet);// Reserve index for Y
		}

		public Builder createNeuronWith(Function<List<Double>, Double> function) {
			Neuron2 neuron = Neuron2.onInputsFunction(function);
			neurons.add(neuron);
			int neuronIndex = neurons.size() - 1;
			inputsMap.put(neuronIndex, new LinkedList<>());
			return this;
		}

		public Builder moveTo(IndexRetriever indexRetriever) {
			currentNeuronIndex = indexRetriever.indexFrom(this);
			return this;
		}

		public Builder readSignalFrom(IndexRetriever indexRetriever) {
			int neuronIndex = indexRetriever.indexFrom(this);
			inputsMap.get(currentNeuronIndex).add(neuronIndex);
			return this;
		}

		public Builder setDXAt(IndexRetriever indexRetriever) {
			this.dXIndex = indexRetriever.indexFrom(this);
			return this;
		}

		public Builder setDYAt(IndexRetriever indexRetriever) {
			this.dYIndex = indexRetriever.indexFrom(this);
			return this;
		}

		public NeuralNetwork build() {
			List<Neuron2> neurons = snapshot(this.neurons);
			Map<Integer, List<Integer>> inputsMap = snapshot(this.inputsMap);
			return new NeuralNetwork() {

				@Override
				public void setInputs(Position position) {
					neurons.set(0, Neuron2.onFixedSignal(position.x));
					neurons.set(1, Neuron2.onFixedSignal(position.y));
				}

				@Override
				public void fire() {
					for (int neuronIndex = 0; neuronIndex < neurons.size(); neuronIndex++) {
						Neuron2 neuron = neurons.get(neuronIndex);
						List<Double> inputSignals = inputsMap.get(neuronIndex).stream()//
								.map(inputIndex -> neurons.get(inputIndex))//
								.map(inputNeuron -> inputNeuron.signal())//
								.collect(toList());
						neuron.fire(inputSignals);
					}
				}

				@Override
				public Move output() {
					return Move.create(readCoordDelta(dXIndex), readCoordDelta(dYIndex));
				}

				private Integer readCoordDelta(Integer optionalIndex) {
					return Optional.ofNullable(optionalIndex)//
							.map(index -> (int) Math.round(neurons.get(index).signal()))//
							.orElse(0);
				}
			};
		}

		private Map<Integer, List<Integer>> snapshot(Map<Integer, List<Integer>> inputs) {
			return inputs.entrySet().stream()//
					.collect(toMap(//
							entry -> entry.getKey(), //
							entry -> new ArrayList<>(entry.getValue())));
		}

		private ArrayList<Neuron2> snapshot(List<Neuron2> neurons) {
			return new ArrayList<>(neurons);
		}

		// TODO Rename NeuronRetriever once old builder is removed
		public interface IndexRetriever {
			int indexFrom(Builder builder);
		}

		public static IndexRetriever currentNeuron() {
			return builder -> builder.currentNeuronIndex;
		}

		public static IndexRetriever firstNeuron() {
			return builder -> 0;
		}

		public static IndexRetriever previousNeuron() {
			return relativeNeuron(-1);
		}

		public static IndexRetriever nextNeuron() {
			return relativeNeuron(1);
		}

		public static IndexRetriever lastNeuron() {
			return builder -> builder.neurons.size() - 1;
		}

		public static IndexRetriever relativeNeuron(int relativeIndex) {
			return builder -> (builder.currentNeuronIndex + relativeIndex) % builder.neurons.size();
		}

		public static IndexRetriever neuronAt(int absoluteIndex) {
			return builder -> absoluteIndex % builder.neurons.size();
		}

		public static IndexRetriever xNeuron() {
			return builder -> 0;
		}

		public static IndexRetriever yNeuron() {
			return builder -> 1;
		}

		public static Function<List<Double>, Double> fixedSignal(double signal) {
			return inputs -> signal;
		}

		public static Function<List<Double>, Double> suppliedSignal(Supplier<Double> signalSupplier) {
			return inputs -> signalSupplier.get();
		}

		public static Function<List<Double>, Double> streamFunction(Function<DoubleStream, Double> function) {
			return inputs -> function.apply(inputs.stream().mapToDouble(d -> d));
		}

		public static Function<List<Double>, Double> sumFunction() {
			return streamFunction(inputs -> inputs.sum());
		}

		public static Function<List<Double>, Double> weightFunction(double weight) {
			return streamFunction(inputs -> inputs.sum() * weight);
		}

		public static Function<List<Double>, Double> minFunction() {
			return streamFunction(inputs -> inputs.min().orElse(0));
		}

		public static Function<List<Double>, Double> maxFunction() {
			return streamFunction(inputs -> inputs.max().orElse(0));
		}

		public static interface BuilderStep {
			void apply(Builder builder);
		}
	}

	public static class Factory {

		private final Random random;

		public Factory(Random random) {
			this.random = random;
		}

		public NeuralNetwork moveToward(Position position) {
			return new NeuralNetwork.Builder()//
					// targetX
					.createNeuronWith(fixedSignal(position.x))//
					// diffX
					.createNeuronWith(weightFunction(-1))//
					.moveTo(lastNeuron())//
					.readSignalFrom(xNeuron())//
					.createNeuronWith(sumFunction())//
					.moveTo(lastNeuron())//
					.readSignalFrom(relativeNeuron(-1))//
					.readSignalFrom(relativeNeuron(-2))//
					// boundX - lower bound
					.createNeuronWith(fixedSignal(-1))//
					.createNeuronWith(maxFunction())//
					.moveTo(lastNeuron())//
					.readSignalFrom(relativeNeuron(-1))//
					.readSignalFrom(relativeNeuron(-2))//
					// boundX - upper bound
					.createNeuronWith(fixedSignal(1))//
					.createNeuronWith(minFunction())//
					.moveTo(lastNeuron())//
					.readSignalFrom(relativeNeuron(-1))//
					.readSignalFrom(relativeNeuron(-2))//
					// DX
					.setDXAt(lastNeuron())//

					// targetY
					.createNeuronWith(fixedSignal(position.y))//
					// diffY
					.createNeuronWith(weightFunction(-1))//
					.moveTo(lastNeuron())//
					.readSignalFrom(yNeuron())//
					.createNeuronWith(sumFunction())//
					.moveTo(lastNeuron())//
					.readSignalFrom(relativeNeuron(-1))//
					.readSignalFrom(relativeNeuron(-2))//
					// boundY - lower bound
					// TODO Reuse -1 from boundX
					.createNeuronWith(fixedSignal(-1))//
					.createNeuronWith(maxFunction())//
					.moveTo(lastNeuron())//
					.readSignalFrom(relativeNeuron(-1))//
					.readSignalFrom(relativeNeuron(-2))//
					// boundY - upper bound
					// TODO Reuse 1 from boundX
					.createNeuronWith(fixedSignal(1))//
					.createNeuronWith(minFunction())//
					.moveTo(lastNeuron())//
					.readSignalFrom(relativeNeuron(-1))//
					.readSignalFrom(relativeNeuron(-2))//
					// DY
					.setDYAt(lastNeuron())//

					.build();
		}

		public NeuralNetwork moveUp() {
			return moveStraight(0, -1);
		}

		public NeuralNetwork moveUpRight() {
			return moveStraight(1, -1);
		}

		public NeuralNetwork moveRight() {
			return moveStraight(1, 0);
		}

		public NeuralNetwork moveDownRight() {
			return moveStraight(1, 1);
		}

		public NeuralNetwork moveDown() {
			return moveStraight(0, 1);
		}

		public NeuralNetwork moveDownLeft() {
			return moveStraight(-1, 1);
		}

		public NeuralNetwork moveLeft() {
			return moveStraight(-1, 0);
		}

		public NeuralNetwork moveUpLeft() {
			return moveStraight(-1, -1);
		}

		public NeuralNetwork moveStraight(int dXSignal, int dYSignal) {
			return new NeuralNetwork.Builder()//
					.createNeuronWith(fixedSignal(dXSignal))//
					.setDXAt(lastNeuron())//
					.createNeuronWith(fixedSignal(dYSignal))//
					.setDYAt(lastNeuron())//
					.build();
		}

		public NeuralNetwork moveRandomly() {
			Supplier<Double> randomSupplier = () -> (double) random.nextInt(3) - 1;
			NeuralNetwork neuralNetwork = new NeuralNetwork.Builder()//
					// randX - lower bound
					.createNeuronWith(suppliedSignal(randomSupplier))//
					.createNeuronWith(fixedSignal(-1))//
					.createNeuronWith(maxFunction())//
					.moveTo(lastNeuron())//
					.readSignalFrom(relativeNeuron(-1))//
					.readSignalFrom(relativeNeuron(-2))//
					// randX - upper bound
					.createNeuronWith(fixedSignal(1))//
					.createNeuronWith(minFunction())//
					.moveTo(lastNeuron())//
					.readSignalFrom(relativeNeuron(-1))//
					.readSignalFrom(relativeNeuron(-2))//
					// DX
					.setDXAt(lastNeuron())//

					// randY - lower bound
					// TODO Reuse -1 from randX
					.createNeuronWith(suppliedSignal(randomSupplier))//
					.createNeuronWith(fixedSignal(-1))//
					.createNeuronWith(maxFunction())//
					.moveTo(lastNeuron())//
					.readSignalFrom(relativeNeuron(-1))//
					.readSignalFrom(relativeNeuron(-2))//
					// randY - upper bound
					// TODO Reuse 1 from boundX
					.createNeuronWith(fixedSignal(1))//
					.createNeuronWith(minFunction())//
					.moveTo(lastNeuron())//
					.readSignalFrom(relativeNeuron(-1))//
					.readSignalFrom(relativeNeuron(-2))//
					// DY
					.setDYAt(lastNeuron())//

					.build();
			return neuralNetwork;
		}

		public NeuralNetwork decode(List<Code> codes) {
			NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
			codes.stream().map(Code::resolve).forEach(step -> step.apply(builder));
			return builder.build();
		}
	}

	static interface Neuron {
		void fire(List<Double> inputSignals);

		double signal();

		static Neuron create(Function<List<Double>, Double> signalComputer) {
			return new Neuron() {

				double signal;

				@Override
				public void fire(List<Double> inputSignals) {
					signal = signalComputer.apply(inputSignals);
				}

				@Override
				public double signal() {
					return signal;
				}
			};
		}

		static Neuron createOnStream(Function<DoubleStream, Double> signalComputer) {
			return new Neuron() {

				double signal;

				@Override
				public void fire(List<Double> inputSignals) {
					signal = signalComputer.apply(inputSignals.stream().mapToDouble(d -> d));
				}

				@Override
				public double signal() {
					return signal;
				}
			};
		}

		default Neuron named(String id) {
			Neuron neuron = this;
			return new Neuron() {

				@Override
				public void fire(List<Double> inputSignals) {
					neuron.fire(inputSignals);
				}

				@Override
				public double signal() {
					return neuron.signal();
				}

				@Override
				public String toString() {
					return id;
				}
			};
		}

		static Neuron withoutInputs(double signal) {
			return create(inputSignals -> signal);
		}

		static Neuron withoutInputs(Supplier<Double> signalSource) {
			return create(inputSignals -> signalSource.get());
		}

		static Neuron passingFirstInput() {
			return create(inputSignals -> inputSignals.iterator().next());
		}

		static Neuron summingInputs() {
			return createOnStream(inputSignals -> inputSignals.sum());
		}

	}

	static interface Synapse {

		Neuron input();

		double signal();

		static Synapse create(Neuron input, UnaryOperator<Double> signalComputer) {
			return new Synapse() {

				@Override
				public Neuron input() {
					return input;
				}

				@Override
				public double signal() {
					return signalComputer.apply(input.signal());
				}
			};
		}

		static Synapse direct(Neuron input) {
			return create(input, UnaryOperator.identity());
		}

		static Synapse weighted(Neuron input, double weight) {
			return create(input, signal -> signal * weight);
		}

	}

}
