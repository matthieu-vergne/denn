package fr.vergne.denn.agent;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Supplier;

import fr.vergne.denn.agent.adn.Program;

public class LayeredNetwork {
	public static class Programmer {
		private final Program.Builder builder = new Program.Builder();
		private int index = 0;
		private final int x = index++;
		private final int y = index++;

		public Layer layerOf() {
			return new Layer(builder, new LinkedList<>(), x, y, () -> index++);
		}

		public Neuron sum(Layer layer) {
			int dx = index++;
			builder.createNeuronWithSumFunction().moveTo(dx);
			layer.indexes.forEach(index -> {
				builder.readSignalFrom(index);
			});
			return new Neuron(dx);
		}

		public Programmer setDx(Neuron dx) {
			builder.setDXAt(dx.index);
			return this;
		}

		public Programmer setDy(Neuron dy) {
			builder.setDYAt(dy.index);
			return this;
		}

		public Program program() {
			return builder.build();
		}
	}

	public static class Layer {
		private final Program.Builder builder;
		private final int x;
		private final int y;
		private final Supplier<Integer> indexSupplier;
		private final List<Integer> indexes;

		public Layer(Program.Builder builder, List<Integer> indexes, int x, int y, Supplier<Integer> indexSupplier) {
			this.builder = builder;
			this.indexes = indexes;
			this.x = x;
			this.y = y;
			this.indexSupplier = indexSupplier;
		}

		public Layer x() {
			indexes.add(x);
			return this;
		}

		public Layer y() {
			indexes.add(y);
			return this;
		}

		public Layer constant(double signal) {
			int c = indexSupplier.get();
			builder.createNeuronWithFixedSignal(1.0);
			indexes.add(c);
			return this;
		}

		public Layer rand() {
			int r = indexSupplier.get();
			builder.createNeuronWithRandomSignal();
			indexes.add(r);
			return this;
		}

		public Layer weighted(double... weights) {
			int requiredSize = indexes.size();
			int actualSize = weights.length;
			if (actualSize != requiredSize) {
				throw new IllegalArgumentException(
						"Your layer requires " + requiredSize + " weights, currently " + actualSize);
			}

			List<Integer> newIndexes = new LinkedList<>();
			for (int i = 0; i < indexes.size(); i++) {
				Integer parentIndex = indexes.get(i);
				double weight = weights[i];
				int childIndex = indexSupplier.get();
				builder.createNeuronWithWeightedSumFunction(weight)//
						.moveTo(childIndex)//
						.readSignalFrom(parentIndex);
				newIndexes.add(childIndex);
			}
			return new Layer(builder, newIndexes, x, y, indexSupplier);
		}
	}

	public static class Neuron {

		private final int index;

		public Neuron(int index) {
			this.index = index;
		}

	}

	public static interface Description {

		Collection<Collection<Integer>> layers();

		Double weight(Integer neuron1, Integer neuron2);
		String name(Integer neuron);
	}
}
