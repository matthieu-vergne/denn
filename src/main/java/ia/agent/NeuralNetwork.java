package ia.agent;

import static java.util.Objects.*;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import ia.terrain.Move;
import ia.terrain.Position;

public interface NeuralNetwork {

	void setInputs(Position position);

	Move output();

	Stream<Neuron> neurons();

	static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private final Map<Object, Neuron> neurons = new HashMap<Object, Neuron>();
		private final Object neuronXId = specialId("input:x");
		private final Object neuronYId = specialId("input:y");
		private final Object neuronDXId = specialId("output:dx");
		private final Object neuronDYId = specialId("output:dy");
		private Position position = null;

		public Builder() {
			set(neuronX(), Neuron.withSignalSource(() -> (double) position.x));
			set(neuronY(), Neuron.withSignalSource(() -> (double) position.y));
		}

		public Builder set(IdRetriever idRetriever, NeuronRetriever neuronRetriever) {
			requireNonNull(idRetriever, "Missing ID retriever");
			requireNonNull(neuronRetriever, "Missing neuron retriever");
			neurons.put(idRetriever.resolve(this), neuronRetriever.resolve(this));
			return this;
		}

		public Builder set(IdRetriever idRetriever, Neuron neuron) {
			requireNonNull(idRetriever, "Missing ID retriever");
			requireNonNull(neuron, "Missing neuron");
			return set(idRetriever, builder -> neuron);
		}

		public NeuralNetwork build() {
			requireNonNull(neurons.get(neuronDXId), "Missing " + neuronDXId);
			requireNonNull(neurons.get(neuronDYId), "Missing " + neuronDYId);
			return new NeuralNetwork() {

				@Override
				public void setInputs(Position position) {
					requireNonNull(position, "Missing position");
					Builder.this.position = position;
				}

				@Override
				public Move output() {
					return Move.create(//
							(int) Math.round(retrieveNeuron(neuronDX()).signal()), //
							(int) Math.round(retrieveNeuron(neuronDY()).signal())//
					);
				}

				@Override
				public Stream<Neuron> neurons() {
					return neurons.values().stream();
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

		public static interface NeuronRetriever {
			Neuron resolve(Builder builder);
		}

		public static NeuronRetriever with(IdRetriever idRetriever) {
			return builder -> builder.retrieveNeuron(idRetriever);
		}

		public static NeuronRetriever asDiffOf(IdRetriever target, IdRetriever current) {
			return asWeightedSumOf(Map.of(//
					target, 1.0, //
					current, -1.0 //
			));
		}

		public static NeuronRetriever asWeightedSumOf(Map<IdRetriever, Double> inputs) {
			return new NeuronRetriever() {
				@Override
				public Neuron resolve(Builder builder) {
					return new Neuron() {

						@Override
						public double signal() {
							return inputs.entrySet().stream()//
									.mapToDouble(entry -> {
										IdRetriever idRetriever = entry.getKey();
										Neuron neuron = builder.retrieveNeuron(idRetriever);
										Double weight = entry.getValue();
										return weight * neuron.signal();
									})//
									.sum();
						}
					};
				}
			};
		}

		public static NeuronRetriever asBounded(IdRetriever idRetriever, double min, double max) {
			return new NeuronRetriever() {
				@Override
				public Neuron resolve(Builder builder) {
					return new Neuron() {

						@Override
						public double signal() {
							Neuron diffXNeuron = builder.retrieveNeuron(idRetriever);
							double signal = diffXNeuron.signal();
							return Math.max(min, Math.min(signal, max));
						}
					};
				}
			};
		}

	}

	static interface Neuron {
		double signal();

		static Neuron withSignalSource(Supplier<Double> signalSource) {
			return new Neuron() {

				@Override
				public double signal() {
					return signalSource.get();
				}
			};
		}

		static Neuron withSignal(double signal) {
			return new Neuron() {

				@Override
				public double signal() {
					return signal;
				}
			};
		}

	}

}
