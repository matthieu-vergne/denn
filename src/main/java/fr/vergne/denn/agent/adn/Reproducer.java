package fr.vergne.denn.agent.adn;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

@FunctionalInterface
public interface Reproducer {

	Chromosome reproduce(Chromosome chromosome1, Chromosome chromosome2);

	static Reproducer onFirstParent() {
		return (chromosome1, chromosome2) -> chromosome1;
	}

	static Reproducer onRandomParent(Random random) {
		return (chromosome1, chromosome2) -> random.nextBoolean() ? chromosome1 : chromosome2;
	}

	static Reproducer onRandomCodes(Random random) {
		return (chromosome1, chromosome2) -> {
			byte[] bytes1 = chromosome1.bytes();
			byte[] bytes2 = chromosome2.bytes();

			List<Code> codes1 = Program.deserialize(bytes1).codes();
			List<Code> codes2 = Program.deserialize(bytes2).codes();

			if (codes1.size() < codes2.size()) {
				List<Code> subList = codes2.subList(0, codes2.size() - codes1.size());
				codes1.addAll(0, subList);
			} else if (codes1.size() > codes2.size()) {
				List<Code> subList = codes1.subList(0, codes1.size() - codes2.size());
				codes2.addAll(0, subList);
			}

			return generate(random, codes1, codes2);
		};
	}

	

	static Chromosome generate(Random random, List<Code> codes1, List<Code> codes2) {
		List<Code> codesChild = new LinkedList<>();
		for (int i = 0; i < codes1.size(); i++) {
			List<Code> parent = random.nextBoolean() ? codes1 : codes2;
			Code code = parent.get(i);
			codesChild.add(code);
		}
		byte[] bytes = new Program(codesChild).serialize();
		return new Chromosome(bytes);
	}
}
