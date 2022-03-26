package ia.agent.adn;

import java.util.Random;

@FunctionalInterface
public interface Mutator {

	Chromosome mutate(Chromosome chromosome);

	static Mutator createWithoutMutation() {
		return chromosomeChild -> chromosomeChild;
	}

	static Mutator withProbability(Random random, double probability) {
		return chromosome -> chromosome.genes()//
				.map(gene -> random.nextDouble() < probability ? gene.mutate(random) : gene)//
				.collect(chromosome.collector());
	}

}
