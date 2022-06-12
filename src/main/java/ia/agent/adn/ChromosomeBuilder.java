package ia.agent.adn;

import static java.util.Objects.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import ia.agent.NeuralNetwork;
import ia.agent.NeuralNetwork.Builder.IdRetriever;
import ia.agent.NeuralNetwork.Builder.NetworkPiece;
import ia.agent.NeuralNetwork.Builder.NetworkPieceRetriever;
import ia.agent.NeuralNetwork.Builder.NeuronRetriever;
import ia.agent.NeuralNetwork.Neuron;
import ia.window.Colorizer;

// TODO Create synapses?
// TODO Refactor neurons creations into core+synapse creations?
public class ChromosomeBuilder {
	private final List<Gene> genes = new LinkedList<>();
	
	public ChromosomeBuilder set(IdRetriever idRetriever, Neuron neuron, Colorizer colorizer) {
		requireNonNull(neuron, "Missing neuron");
		return set(idRetriever, NetworkPiece.ofNeuron(neuron), colorizer);
	}

	public ChromosomeBuilder set(IdRetriever idRetriever, NetworkPiece networkPiece, Colorizer colorizer) {
		requireNonNull(idRetriever, "Missing ID retriever");
		requireNonNull(networkPiece, "Missing network piece");
		requireNonNull(colorizer, "Missing colorizer");
		return set(idRetriever, (NetworkPieceRetriever) builder -> networkPiece, colorizer);
	}
	
	public ChromosomeBuilder set(IdRetriever idRetriever, NetworkPieceRetriever networkPieceRetriever,
			Colorizer colorizer) {
		requireNonNull(idRetriever, "Missing ID retriever");
		requireNonNull(networkPieceRetriever, "Missing network piece retriever");
		requireNonNull(colorizer, "Missing colorizer");
		genes.add(new Gene() {

			@Override
			public NeuralNetwork.Builder applyOn(NeuralNetwork.Builder builder) {
				return builder.set(idRetriever, networkPieceRetriever);
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
