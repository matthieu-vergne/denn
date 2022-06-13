package fr.vergne.denn.agent;

import static fr.vergne.denn.agent.NeuralNetwork.Builder.*;
import static java.lang.Math.*;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.DoubleStream;

import fr.vergne.denn.agent.adn.Program;
import fr.vergne.denn.utils.Position;

public interface NeuralNetwork {

	void setInputs(Position position);

	void fire();

	Position.Move output();

	public static record NeuronPair(Object input, Object output) {
	};

	public static interface NeuralFunction {
		Double compute(DoubleStream inputs);
	}

	public static interface Neuron {

		void fire(List<Supplier<Double>> inputs);

		double signal();

		public static Neuron onInputsFunction(NeuralFunction function) {
			return new Neuron() {
				private double signal;

				@Override
				public void fire(List<Supplier<Double>> inputs) {
					signal = function.compute(inputs.stream().mapToDouble(Supplier::get));
				}

				@Override
				public double signal() {
					return signal;
				}
			};
		}

		public static Neuron onSingleInputFunction(UnaryOperator<Double> operator) {
			return new Neuron() {
				private double signal;

				@Override
				public void fire(List<Supplier<Double>> inputs) {
					signal = operator.apply(inputs.get(0).get());
				}

				@Override
				public double signal() {
					return signal;
				}
			};
		}

		public static Neuron onSignalSupplier(Supplier<Double> supplier) {
			return new Neuron() {
				private double signal;

				@Override
				public void fire(List<Supplier<Double>> inputs) {
					signal = supplier.get();
				}

				@Override
				public double signal() {
					return signal;
				}
			};
		}

		public static Neuron onFixedSignal(double signal) {
			return new Neuron() {
				@Override
				public void fire(List<Supplier<Double>> inputs) {
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
	public static class Builder implements Neural.Builder<NeuralNetwork> {
		private final List<Neuron> neurons = new LinkedList<>();
		private final Map<Integer, List<Integer>> inputsMap = new HashMap<>();
		private final Rand random;
		private int currentNeuronIndex = 0;
		private Integer dXIndex = null;
		private Integer dYIndex = null;

		// TODO remove
		public static interface Rand {
			double next();
		}

		// TODO remove
		public Builder() {
			this(() -> {
				throw new RuntimeException("No random provided");
			});
		}

		public Builder(Rand random) {
			this.random = random;
			NeuralFunction noFunctionYet = inputs -> {
				throw new IllegalStateException("Reserved neuron not replaced yet");
			};
			createNeuronWith(noFunctionYet);// Reserve index for X
			createNeuronWith(noFunctionYet);// Reserve index for Y
		}

		public Builder createNeuronWith(NeuralFunction function) {
			Neuron neuron = Neuron.onInputsFunction(function);
			neurons.add(neuron);
			int neuronIndex = neurons.size() - 1;
			inputsMap.put(neuronIndex, new LinkedList<>());
			return this;
		}

		@Override
		public fr.vergne.denn.agent.Neural.Builder<NeuralNetwork> createNeuronWithRandomSignal() {
			return createNeuronWith(suppliedSignal(() -> random.next()));
		}

		@Override
		public Builder createNeuronWithFixedSignal(double signal) {
			return createNeuronWith(fixedSignal(signal));
		}

		@Override
		public Builder createNeuronWithWeightedSumFunction(double weight) {
			return createNeuronWith(weightedSumFunction(weight));
		}

		@Override
		public Builder createNeuronWithSumFunction() {
			return createNeuronWith(sumFunction());
		}

		@Override
		public Builder createNeuronWithMinFunction() {
			return createNeuronWith(minFunction());
		}

		@Override
		public Builder createNeuronWithMaxFunction() {
			return createNeuronWith(maxFunction());
		}

		public Builder moveTo(IndexRetriever indexRetriever) {
			return moveTo(indexRetriever.indexFrom(this));
		}

		@Override
		public Builder moveTo(int neuronIndex) {
			currentNeuronIndex = normalizeIndex(neuronIndex);
			return this;
		}

		public Builder readSignalFrom(IndexRetriever indexRetriever) {
			return readSignalFrom(indexRetriever.indexFrom(this));
		}

		@Override
		public Builder readSignalFrom(int neuronIndex) {
			inputsMap.get(currentNeuronIndex).add(normalizeIndex(neuronIndex));
			return this;
		}

		public Builder setDXAt(IndexRetriever indexRetriever) {
			return setDXAt(indexRetriever.indexFrom(this));
		}

		@Override
		public Builder setDXAt(int neuronIndex) {
			this.dXIndex = normalizeIndex(neuronIndex);
			return this;
		}

		public Builder setDYAt(IndexRetriever indexRetriever) {
			return setDYAt(indexRetriever.indexFrom(this));
		}

		@Override
		public Builder setDYAt(int neuronIndex) {
			this.dYIndex = normalizeIndex(neuronIndex);
			return this;
		}

		private int normalizeIndex(int index) {
			return ((index % neurons.size()) + neurons.size()) % neurons.size();
		}

		// TODO Compute only neurons having an impact on dX/dY
		// TODO Parallelize neurons firing that are independent
		// TODO Don't retrieve signals from scratch, store it for reuse
		public static enum FiringStrategy {
			SUPPLIED_INPUTS((neurons, inputsMap) -> {
				// TODO Extract stuff here
				return () -> {
					List<List<Supplier<Double>>> inputsSuppliers = inputsMap.stream()//
							.map(indexes -> {
								List<Supplier<Double>> list = new ArrayList<>(indexes.size());
								for (Integer neuronIndex : indexes) {
									Neuron neuron = neurons.get(neuronIndex);
									list.add(neuron::signal);
								}
								return list;
							})//
							.collect(toList());
					for (int neuronIndex = 0; neuronIndex < neurons.size(); neuronIndex++) {
						neurons.get(neuronIndex).fire(inputsSuppliers.get(neuronIndex));
					}
				};
			}),//
			RECORD((neurons, inputsMap) -> {
				// TODO Extract stuff here
				return () -> {
					record X(Neuron neuron, List<Supplier<Double>> inputs) {}
					int[] index = {0};
					inputsMap.stream()//
							.map(indexes -> {
								List<Supplier<Double>> list = new ArrayList<>(indexes.size());
								for (Integer neuronIndex : indexes) {
									Neuron neuron = neurons.get(neuronIndex);
									list.add(neuron::signal);
								}
								return new X(neurons.get(index[0]++), list);
							})//
							.forEach(x -> x.neuron.fire(x.inputs));
				};
			}),//
			;

			public static final FiringStrategy DEFAULT = FiringStrategy.SUPPLIED_INPUTS;

			private final Factory factory;

			private FiringStrategy(Factory factory) {
				this.factory = factory;
			}

			public Runnable create(List<Neuron> neurons, List<List<Integer>> inputsMap) {
				return factory.apply(neurons, inputsMap);
			}

			static interface Factory {
				Runnable apply(List<Neuron> neurons, List<List<Integer>> inputsMap);
			}

		}

		@Override
		public NeuralNetwork build() {
			// TODO List<X> with X(Neuron, List<Neuron>)
			List<Neuron> neurons = snapshot(this.neurons);
			double[] inputs = {0, 0};
			neurons.set(0, Neuron.onSignalSupplier(() -> inputs[0]));
			neurons.set(1, Neuron.onSignalSupplier(() -> inputs[1]));
			
			List<List<Integer>> inputsMap = snapshot(this.inputsMap);
			
			Runnable firingStrategy = FiringStrategy.DEFAULT.create(neurons, inputsMap);
			
			Integer dXIndex = this.dXIndex;
			Integer dYIndex = this.dYIndex;
			return new NeuralNetwork() {

				@Override
				public void setInputs(Position position) {
					inputs[0] = position.x();
					inputs[1] = position.y();
				}

				@Override
				public void fire() {
					firingStrategy.run();
				}

				@Override
				public Position.Move output() {
					return new Position.Move(//
							toUnitaryMove(readSignal(neurons, dXIndex)), //
							toUnitaryMove(readSignal(neurons, dYIndex))//
					);
				}

				private Integer toUnitaryMove(double signal) {
					int requestedMove = (int) round(signal);
					return max(-1, min(requestedMove, 1));
				}

				private double readSignal(List<Neuron> neurons, Integer optionalIndex) {
					return Optional.ofNullable(optionalIndex).map(neurons::get).map(Neuron::signal).orElse(0.0);
				}
			};
		}

		private List<List<Integer>> snapshot(Map<Integer, List<Integer>> inputs) {
			int size = inputs.keySet().stream().mapToInt(i -> i).max().getAsInt() + 1;
			List<List<Integer>> list = new ArrayList<>(size);
			for (int index = 0; index < size; index++) {
				list.add(inputs.computeIfAbsent(index, k -> emptyList()));
			}
			return list;
		}

		private ArrayList<Neuron> snapshot(List<Neuron> neurons) {
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
			return builder -> absoluteIndex;
		}

		public static IndexRetriever xNeuron() {
			return builder -> 0;
		}

		public static IndexRetriever yNeuron() {
			return builder -> 1;
		}

		public static NeuralFunction fixedSignal(double signal) {
			return inputs -> signal;
		}

		public static NeuralFunction suppliedSignal(Supplier<Double> signalSupplier) {
			return inputs -> signalSupplier.get();
		}

		public static NeuralFunction streamFunction(Function<DoubleStream, Double> function) {
			return inputs -> function.apply(inputs);
		}

		public static NeuralFunction sumFunction() {
			return streamFunction(inputs -> inputs.sum());
		}

		public static NeuralFunction weightedSumFunction(double weight) {
			return streamFunction(inputs -> inputs.sum() * weight);
		}

		public static NeuralFunction minFunction() {
			return streamFunction(inputs -> inputs.min().orElse(0));
		}

		public static NeuralFunction maxFunction() {
			return streamFunction(inputs -> inputs.max().orElse(0));
		}

		public static interface BuilderStep {
			void apply(Neural.Builder<?> builder);
		}
	}

	public static class Factory {
		private final Supplier<Neural.Builder<NeuralNetwork>> networkBuilderGenerator;
		private final Random random;

		public Factory(Supplier<Neural.Builder<NeuralNetwork>> networkBuilderGenerator, Random random) {
			this.networkBuilderGenerator = networkBuilderGenerator;
			this.random = random;
		}

		public NeuralNetwork moveToward(Position position) {
			return new NeuralNetwork.Builder(random::nextDouble)//
					// targetX
					.createNeuronWith(fixedSignal(position.x()))//
					// diffX
					.createNeuronWith(weightedSumFunction(-1))//
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
					.createNeuronWith(fixedSignal(position.y()))//
					// diffY
					.createNeuronWith(weightedSumFunction(-1))//
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
			return new NeuralNetwork.Builder(random::nextDouble)//
					.createNeuronWith(fixedSignal(dXSignal))//
					.setDXAt(lastNeuron())//
					.createNeuronWith(fixedSignal(dYSignal))//
					.setDYAt(lastNeuron())//
					.build();
		}

		public NeuralNetwork moveRandomly() {
			Supplier<Double> randomSupplier = () -> (double) random.nextInt(3) - 1;
			NeuralNetwork neuralNetwork = new NeuralNetwork.Builder(random::nextDouble)//
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

		public NeuralNetwork execute(Program program) {
			Neural.Builder<NeuralNetwork> builder = networkBuilderGenerator.get();
			program.executeOn(builder);
			return builder.build();
		}

	}
}