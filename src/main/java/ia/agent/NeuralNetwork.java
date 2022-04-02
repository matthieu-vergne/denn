package ia.agent;

import static ia.agent.NeuralNetwork.Builder2.*;
import static java.util.Collections.*;
import static java.util.Objects.*;
import static java.util.stream.Collectors.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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

	static Builder builder() {
		return new Builder();
	}

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
	public static class Builder2 {
		private final List<Neuron2> neurons = new LinkedList<>();
		private final Map<Integer, List<Integer>> inputsMap = new HashMap<>();
		private int currentNeuronIndex = 0;
		private Integer dXIndex = null;
		private Integer dYIndex = null;

		public Builder2() {
			Function<List<Double>, Double> noFunctionYet = inputs -> {
				throw new IllegalStateException("Reserved neuron not replaced yet");
			};
			createNeuronWith(noFunctionYet);// Reserve index for X
			createNeuronWith(noFunctionYet);// Reserve index for Y
		}

		public Builder2 createNeuronWith(Function<List<Double>, Double> function) {
			Neuron2 neuron = Neuron2.onInputsFunction(function);
			neurons.add(neuron);
			int neuronIndex = neurons.size() - 1;
			inputsMap.put(neuronIndex, new LinkedList<>());
			return this;
		}

		public Builder2 moveTo(IndexRetriever indexRetriever) {
			currentNeuronIndex = indexRetriever.indexFrom(this);
			return this;
		}

		public Builder2 readSignalFrom(IndexRetriever indexRetriever) {
			int neuronIndex = indexRetriever.indexFrom(this);
			inputsMap.get(currentNeuronIndex).add(neuronIndex);
			return this;
		}

		public Builder2 setDXAt(IndexRetriever indexRetriever) {
			this.dXIndex = indexRetriever.indexFrom(this);
			return this;
		}

		public Builder2 setDYAt(IndexRetriever indexRetriever) {
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
			int indexFrom(Builder2 builder);
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
			void apply(Builder2 builder);
		}
	}

	public static class Factory {

		private final Random random;

		public Factory(Random random) {
			this.random = random;
		}

		public NeuralNetwork moveToward(Position position) {
			return new NeuralNetwork.Builder2()//
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
			return new NeuralNetwork.Builder2()//
					.createNeuronWith(fixedSignal(dXSignal))//
					.setDXAt(lastNeuron())//
					.createNeuronWith(fixedSignal(dYSignal))//
					.setDYAt(lastNeuron())//
					.build();
		}

		public NeuralNetwork moveRandomly() {
			Supplier<Double> randomSupplier = () -> (double) random.nextInt(3) - 1;
			NeuralNetwork neuralNetwork = new NeuralNetwork.Builder2()//
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
			NeuralNetwork.Builder2 builder = new NeuralNetwork.Builder2();
			codes.stream().map(Code::resolve).forEach(step -> step.apply(builder));
			return builder.build();
		}
	}

	public static class Builder {
		private final Map<Object, Neuron> neurons = new LinkedHashMap<>();
		private final Map<NeuronPair, Synapse> synapses2 = new LinkedHashMap<>();
		private final Object neuronXId = specialId("input:x");
		private final Object neuronYId = specialId("input:y");
		private final Object neuronDXId = specialId("output:dx");
		private final Object neuronDYId = specialId("output:dy");
		private Position position = null;

		public Builder() {
			set(neuronX(), Neuron.withoutInputs(() -> (double) position.x));
			set(neuronY(), Neuron.withoutInputs(() -> (double) position.y));
		}

		public Builder set(IdRetriever idRetriever, Neuron neuron) {
			requireNonNull(idRetriever, "Missing ID retriever");
			requireNonNull(neuron, "Missing neuron");
			Object id = idRetriever.resolve(this);
			neurons.put(id, neuron.named(id.toString()));
			return this;
		}

		public Builder set(IdRetriever idRetriever, NetworkPieceRetriever networkPieceRetriever) {
			requireNonNull(idRetriever, "Missing ID retriever");
			requireNonNull(networkPieceRetriever, "Missing network piece retriever");
			NetworkPiece networkPiece = networkPieceRetriever.resolve(this);
			networkPiece.neuron().ifPresent(neuron -> set(idRetriever, neuron));
			networkPiece.synapses().stream()//
					.forEach(synapse -> {
						link(builder -> {
							Object inputId = builder.neurons.entrySet().stream()//
									.filter(entry -> entry.getValue().equals(synapse.input()))//
									.map(entry -> entry.getKey())//
									.findFirst().get();
							return inputId;
						}, idRetriever, signal -> synapse.signal());
					});
			// chromosomeBuilder
			return this;
		}

		public Builder link(IdRetriever inputRetriever, IdRetriever outputRetriever,
				UnaryOperator<Double> signalComputer) {
			requireNonNull(inputRetriever, "Missing input retriever");
			requireNonNull(outputRetriever, "Missing output retriever");
			requireNonNull(signalComputer, "Missing signal computer");
			Neuron input = retrieveNeuron(inputRetriever);
			Neuron output = retrieveNeuron(outputRetriever);
			Synapse synapse = Synapse.create(input, signalComputer);
			synapses2.put(new NeuronPair(input, output), synapse);
			return this;
		}

		public NeuralNetwork build() {
			requireNonNull(neurons.get(neuronDXId), "Missing " + neuronDXId);
			requireNonNull(neurons.get(neuronDYId), "Missing " + neuronDYId);
			Map<Object, Neuron> allNeurons2 = unmodifiableMap(new LinkedHashMap<>(neurons));
			Map<NeuronPair, Synapse> allSynapses2 = unmodifiableMap(new LinkedHashMap<>(synapses2));
			return new NeuralNetwork() {

				@Override
				public void setInputs(Position position) {
					requireNonNull(position, "Missing position");
					Builder.this.position = position;
				}

				@Override
				public void fire() {
					// TODO Support loops
					for (Neuron neuron : allNeurons2.values()) {
						List<Double> inputSignals = allSynapses2.entrySet().stream()//
								.filter(entry -> entry.getKey().output().equals(neuron))//
								.map(entry -> entry.getValue())//
								.map(Synapse::signal)//
								.collect(toList());
						neuron.fire(inputSignals);
					}
				}

				@Override
				public Move output() {
					return Move.create(//
							(int) Math.round(retrieveNeuron(neuronDX()).signal()), //
							(int) Math.round(retrieveNeuron(neuronDY()).signal())//
					);
				}
			};
		}

		private Neuron retrieveNeuron(IdRetriever idRetriever) {
			return neurons.computeIfAbsent(idRetriever.resolve(this), id -> {
				throw new NoSuchElementException("Unknown neuron " + id);
			});
		}

		private static Object specialId(String id) {
			return new Object() {
				@Override
				public String toString() {
					return id;
				}
			};
		}

		public static interface IdRetriever {
			Object resolve(Builder builder);
		}

		public static IdRetriever neuron(Object id) {
			return builder -> id;
		}

		public static IdRetriever neuronX() {
			return builder -> builder.neuronXId;
		}

		public static IdRetriever neuronY() {
			return builder -> builder.neuronYId;
		}

		public static IdRetriever neuronDX() {
			return builder -> builder.neuronDXId;
		}

		public static IdRetriever neuronDY() {
			return builder -> builder.neuronDYId;
		}

		public static interface NetworkPiece {
			@Deprecated
			// TODO Replace by collection of neurons
			Optional<Neuron> neuron();

			Collection<Neuron> neurons();

			Collection<Synapse> synapses();

			static NetworkPiece ofNeuron(Neuron neuron) {
				return new NetworkPiece() {

					@Override
					public Optional<Neuron> neuron() {
						return Optional.of(neuron);
					}

					@Override
					public Collection<Neuron> neurons() {
						return List.of(neuron);
					}

					@Override
					public Collection<Synapse> synapses() {
						return emptyList();
					}
				};
			}
		}

		public static interface NetworkPieceRetriever {
			NetworkPiece resolve(Builder builder);
		}

		public static interface NeuronRetriever {
			Neuron resolve(Builder builder);

			default NetworkPieceRetriever toNetworkPiece() {
				NeuronRetriever src = this;
				return new NetworkPieceRetriever() {

					@Override
					public NetworkPiece resolve(Builder builder) {
						Neuron neuron = src.resolve(builder);
						return new NetworkPiece() {

							@Override
							public Optional<Neuron> neuron() {
								return Optional.of(neuron);
							}

							@Override
							public Collection<Neuron> neurons() {
								return List.of(neuron);
							}

							@Override
							public Collection<Synapse> synapses() {
								return emptyList();
							}
						};
					}
				};
			}
		}

		public static NetworkPieceRetriever as(IdRetriever idRetriever) {
			return builder -> {
				Neuron neuron = Neuron.passingFirstInput();
				Neuron input = builder.retrieveNeuron(idRetriever);
				Synapse synapse = Synapse.weighted(input, (double) 1);
				return new NetworkPiece() {

					@Override
					public Optional<Neuron> neuron() {
						return Optional.of(neuron);
					}

					@Override
					public Collection<Neuron> neurons() {
						return List.of(neuron);
					}

					@Override
					public List<Synapse> synapses() {
						return List.of(synapse);
					}

				};
			};
		}

		public static NetworkPieceRetriever asDiffOf(IdRetriever target, IdRetriever current) {
			return asWeightedSumOf(Map.of(//
					target, 1.0, //
					current, -1.0 //
			));
		}

		public static NetworkPieceRetriever asWeightedSumOf(Map<IdRetriever, Double> inputs) {
			return new NetworkPieceRetriever() {
				@Override
				public NetworkPiece resolve(Builder builder) {
					Neuron neuron = Neuron.summingInputs();
					List<Synapse> synapses = inputs.entrySet().stream()//
							.map(entry -> {
								IdRetriever idRetriever = entry.getKey();
								double weight = entry.getValue();
								Neuron input = builder.retrieveNeuron(idRetriever);
								return Synapse.weighted(input, weight);
							})//
							.collect(toList());
					return new NetworkPiece() {

						@Override
						public Optional<Neuron> neuron() {
							return Optional.of(neuron);
						}

						@Override
						public Collection<Neuron> neurons() {
							return List.of(neuron);
						}

						@Override
						public Collection<Synapse> synapses() {
							return synapses;
						}
					};
				}

			};
		}

		public static NetworkPieceRetriever asBounded(IdRetriever idRetriever, double min, double max) {
			return new NetworkPieceRetriever() {
				@Override
				public NetworkPiece resolve(Builder builder) {
					Neuron srcNeuron = builder.retrieveNeuron(idRetriever);
					Neuron neuron = Neuron
							.create(inputSignals -> Math.max(min, Math.min(inputSignals.iterator().next(), max)));
					Synapse synapse = Synapse.weighted(srcNeuron, (double) 1);
					return new NetworkPiece() {

						@Override
						public Optional<Neuron> neuron() {
							return Optional.of(neuron);
						}

						@Override
						public Collection<Neuron> neurons() {
							return List.of(neuron);
						}

						@Override
						public Collection<Synapse> synapses() {
							return List.of(synapse);
						}
					};
				}
			};
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
