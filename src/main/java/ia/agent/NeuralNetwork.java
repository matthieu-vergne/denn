package ia.agent;

import static java.util.Collections.*;
import static java.util.Objects.*;
import static java.util.stream.Collectors.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.DoubleStream;

import ia.agent.adn.Chromosome2;
import ia.terrain.Move;
import ia.terrain.Position;

public interface NeuralNetwork {

	void setInputs(Position position);

	void fire();

	Move output();

	static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private final Map<Object, Neuron> neurons = new LinkedHashMap<Object, Neuron>();
		private final List<Synapse> synapses = new LinkedList<>();
		private final Object neuronXId = specialId("input:x");
		private final Object neuronYId = specialId("input:y");
		private final Object neuronDXId = specialId("output:dx");
		private final Object neuronDYId = specialId("output:dy");
		private Position position = null;
		private Chromosome2.Builder chromosomeBuilder = new Chromosome2.Builder();

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
			synapses.add(Synapse.create(input, output, signalComputer));
			return this;
		}

		public NeuralNetwork build() {
			requireNonNull(neurons.get(neuronDXId), "Missing " + neuronDXId);
			requireNonNull(neurons.get(neuronDYId), "Missing " + neuronDYId);
			List<Neuron> allNeurons = unmodifiableList(new ArrayList<>(neurons.values()));
			List<Synapse> allSynapses = unmodifiableList(new ArrayList<>(synapses));
			return new NeuralNetwork() {

				@Override
				public void setInputs(Position position) {
					requireNonNull(position, "Missing position");
					Builder.this.position = position;
				}

				@Override
				public void fire() {
					// TOOD Support loops
					for (Neuron neuron : allNeurons) {
						List<Double> inputSignals = allSynapses.stream()//
								.filter(synapse -> synapse.output().equals(neuron))//
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
			// TODO Generalize to set of neurons
			Optional<Neuron> neuron();

			Collection<Synapse> synapses();

			static NetworkPiece ofNeuron(Neuron neuron) {
				return new NetworkPiece() {

					@Override
					public Optional<Neuron> neuron() {
						return Optional.of(neuron);
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
				Synapse synapse = Synapse.weighted(input, neuron, 1);
				return new NetworkPiece() {

					@Override
					public Optional<Neuron> neuron() {
						return Optional.of(neuron);
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
								return Synapse.weighted(input, neuron, weight);
							})//
							.collect(toList());
					return new NetworkPiece() {

						@Override
						public Optional<Neuron> neuron() {
							return Optional.of(neuron);
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
					Synapse synapse = Synapse.weighted(srcNeuron, neuron, 1);
					return new NetworkPiece() {

						@Override
						public Optional<Neuron> neuron() {
							return Optional.of(neuron);
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

		Neuron output();

		double signal();

		static Synapse create(Neuron input, Neuron output, UnaryOperator<Double> signalComputer) {
			return new Synapse() {

				@Override
				public Neuron input() {
					return input;
				}

				@Override
				public Neuron output() {
					return output;
				}

				@Override
				public double signal() {
					return signalComputer.apply(input.signal());
				}
			};
		}

		static Synapse direct(Neuron input, Neuron output) {
			return create(input, output, UnaryOperator.identity());
		}

		static Synapse weighted(Neuron input, Neuron output, double weight) {
			return create(input, output, signal -> signal * weight);
		}

	}

}
