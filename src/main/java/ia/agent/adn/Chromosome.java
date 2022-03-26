package ia.agent.adn;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ia.agent.Brain;
import ia.agent.NeuralNetwork;
import ia.terrain.Move;
import ia.terrain.Position;

// TODO Compatible mutations
// TODO Compatible reproductions
public interface Chromosome {

	Stream<Gene> genes();

	default Brain createBrain() {
		Chromosome chromosome = this;

		NeuralNetwork.Builder builder = NeuralNetwork.builder();
		chromosome.genes().forEach(gene -> gene.applyOn(builder));
		NeuralNetwork network = builder.build();

		return new Brain() {

			@Override
			public Move decideMoveFrom(Position position) {
				network.setInputs(position);
				return network.output();
			}
			
			@Override
			public Chromosome chromosome() {
				return chromosome;
			}

		};
	}

	default Collector<Gene, ?, Chromosome> collector() {
		return collectorHelper(Collectors.toList());
	}

	private static <T> Collector<Gene, T, Chromosome> collectorHelper(Collector<Gene, T, List<Gene>> listCollector) {
		return new Collector<Gene, T, Chromosome>() {

			@Override
			public Supplier<T> supplier() {
				return listCollector.supplier();
			}

			@Override
			public BiConsumer<T, Gene> accumulator() {
				return listCollector.accumulator();
			}

			@Override
			public BinaryOperator<T> combiner() {
				return listCollector.combiner();
			}

			@Override
			public Function<T, Chromosome> finisher() {
				return listCollector.finisher()//
						.andThen(genes -> (Chromosome) () -> genes.stream());
			}

			@Override
			public Set<Characteristics> characteristics() {
				return Collections.emptySet();
			}
		};
	}
}
