package ia.agent.adn;

import static java.util.Objects.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import ia.agent.NeuralNetwork;
import ia.agent.NeuralNetwork.Builder.IdRetriever;
import ia.agent.NeuralNetwork.Builder.NeuronRetriever;
import ia.agent.NeuralNetwork.Neuron;
import ia.window.Colorizer;

// TODO Create synapses?
// TODO Refactor neurons creations into core+synapse creations?
public class ChromosomeBuilder {
	private final List<Gene> genes = new LinkedList<>();

	public ChromosomeBuilder set(IdRetriever idRetriever, NeuronRetriever neuronRetriever, Colorizer colorizer) {
		requireNonNull(idRetriever, "Missing ID retriever");
		requireNonNull(neuronRetriever, "Missing neuron retriever");
		requireNonNull(colorizer, "Missing colorizer");
		genes.add(new Gene() {

			@Override
			public NeuralNetwork.Builder applyOn(NeuralNetwork.Builder builder) {
				return builder.set(idRetriever, neuronRetriever);
			}

			@Override
			public Gene mutate(Random random) {
				System.out.println("No mutation yet for neural network genes");
				// TODO Make genes easily mutable
				return this;
			}

			@Override
			public Colorizer colorizer() {
				return colorizer;
			}
		});
		return this;
	}

	public ChromosomeBuilder set(IdRetriever idRetriever, Neuron neuron, Colorizer colorizer) {
		requireNonNull(idRetriever, "Missing ID retriever");
		requireNonNull(neuron, "Missing neuron");
		requireNonNull(colorizer, "Missing colorizer");
		return set(idRetriever, (NeuronRetriever) builder -> neuron, colorizer);
	}

	public Chromosome build() {
		List<Gene> currentGenes = new ArrayList<>(genes);
		return new Chromosome() {

			@Override
			public Stream<Gene> genes() {
				return currentGenes.stream();
			}
		};
	}
}
