package fr.vergne.denn.agent;

import static fr.vergne.denn.agent.NeuralNetwork.Builder.*;
import static java.lang.Math.*;
import static java.util.Objects.*;
import static java.util.stream.Collectors.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import fr.vergne.denn.agent.adn.Program;
import fr.vergne.denn.utils.Position;
import fr.vergne.denn.utils.Position.Move;

public interface NeuralNetwork {

	// TODO Remove X/Y in favor of arbitrary inputs?
	void setXSignal(double x);

	void setYSignal(double y);

	void fire();

	// TODO Remove DX/DY in favor of arbitrary outputs?
	double dXSignal();

	double dYSignal();

	interface AgentNetwork {
		void setPosition(Position position);

		void fire();

		Position.Move getMove();
	}

	default AgentNetwork forAgent() {
		NeuralNetwork neuralNetwork = this;
		return new AgentNetwork() {

			@Override
			public void setPosition(Position position) {
				neuralNetwork.setXSignal(position.x());
				neuralNetwork.setYSignal(position.y());
			}

			@Override
			public void fire() {
				neuralNetwork.fire();
			}

			@Override
			public Move getMove() {
				return new Position.Move(//
						toUnitaryMove(neuralNetwork.dXSignal()), //
						toUnitaryMove(neuralNetwork.dYSignal())//
				);
			}

			private Integer toUnitaryMove(double signal) {
				int requestedMove = (int) round(signal);
				return max(-1, min(requestedMove, 1));
			}
		};
	}

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
		public static enum BuildStrategy {
			BASE((neurons, inputsMap, dXIndex, dYIndex) -> {
				List<Neuron> actualNeurons = new ArrayList<>(neurons);
				double[] inputs = { 0, 0 };
				actualNeurons.set(0, Neuron.onSignalSupplier(() -> inputs[0]));
				actualNeurons.set(1, Neuron.onSignalSupplier(() -> inputs[1]));

				List<Runnable> neuronComputers = IntStream.range(0, neurons.size())//
						.mapToObj(index -> {
							Neuron outputNeuron = actualNeurons.get(index);
							List<Supplier<Double>> inputSignalSuppliers = inputsMap.get(index).stream()//
									.map(inputIndex -> actualNeurons.get(inputIndex))//
									.map(inputNeuron -> (Supplier<Double>) inputNeuron::signal)//
									.collect(toList());
							return (Runnable) () -> outputNeuron.fire(inputSignalSuppliers);
						}).collect(toList());

				Neuron dXNeuron = actualNeurons.get(dXIndex);
				Neuron dYNeuron = actualNeurons.get(dYIndex);

				return new NeuralNetwork() {

					@Override
					public void setXSignal(double x) {
						inputs[0] = x;
					}

					@Override
					public void setYSignal(double y) {
						inputs[1] = y;
					}

					@Override
					public void fire() {
						neuronComputers.forEach(Runnable::run);
					}

					@Override
					public double dXSignal() {
						return dXNeuron.signal();
					}

					@Override
					public double dYSignal() {
						return dYNeuron.signal();
					}

				};
			});

			public static final BuildStrategy DEFAULT = BuildStrategy.BASE;

			private final BuildDefinition buildDefinition;

			private BuildStrategy(BuildDefinition factory) {
				this.buildDefinition = factory;
			}

			public NeuralNetwork buildNetwork(List<Neuron> neurons, Map<Integer, List<Integer>> inputsMap, int dXIndex,
					int dYIndex) {
				return buildDefinition.buildNetwork(neurons, inputsMap, dXIndex, dYIndex);
			}

			static interface BuildDefinition {
				NeuralNetwork buildNetwork(List<Neuron> neurons, Map<Integer, List<Integer>> inputsMap, int dXIndex,
						int dYIndex);
			}

		}

		@Override
		public NeuralNetwork build() {
			requireNonNull(dXIndex, "No dX index defined");
			requireNonNull(dYIndex, "No dY index defined");
			return BuildStrategy.DEFAULT.buildNetwork(neurons, inputsMap, dXIndex, dYIndex);
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
