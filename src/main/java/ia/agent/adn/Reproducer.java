package ia.agent.adn;

import static java.util.stream.Collectors.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

@FunctionalInterface
public interface Reproducer {

	Chromosome reproduce(Chromosome chromosome1, Chromosome chromosome2);

	static Reproducer onFirstParent() {
		return (chromosome1, chromosome2) -> chromosome1;
	}

	static Reproducer onRandomParent(Random random) {
		return (chromosome1, chromosome2) -> random.nextBoolean() ? chromosome1 : chromosome2;
	}

	static Reproducer onRandomGenes(Random random) {
		return (chromosome1, chromosome2) -> {
			Stream<Gene> genes1 = chromosome1.genes();
			Stream<Gene> genes2 = chromosome2.genes();

			List<Gene> list1 = new LinkedList<>(genes1.collect(toList()));
			List<Gene> list2 = new LinkedList<>(genes2.collect(toList()));

			if (list1.size() < list2.size()) {
				List<Gene> subList = list2.subList(0, list2.size()-list1.size());
				list1.addAll(0, subList);
			}
			else if (list1.size() > list2.size()) {
				List<Gene> subList = list1.subList(0, list1.size()-list2.size());
				list2.addAll(0, subList);
			}

			// TODO Optimize perfs, potential infinite loop if weak statistics
			while (true) {
				Chromosome chromosome = generate(random, list1, list2);
				try {
					chromosome.createBrain();
					return chromosome;
				} catch (Exception cause) {
				}
			}
		};
	}

	static Chromosome generate(Random random, List<Gene> list1, List<Gene> list2) {
		List<Gene> listChild = new LinkedList<>();
		for (int i = 0; i < list1.size(); i++) {
			List<Gene> parent = random.nextBoolean() ? list1 : list2;
			Gene gene = parent.get(i);
			listChild.add(gene);
		}
		Chromosome chromosome = new Chromosome() {

			@Override
			public Stream<Gene> genes() {
				return listChild.stream();
			}
		};
		return chromosome;
	}
}
