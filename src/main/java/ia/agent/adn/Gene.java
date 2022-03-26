package ia.agent.adn;

import java.util.Random;

import ia.agent.NeuralNetwork;
import ia.window.Colorizer;

public interface Gene {
	NeuralNetwork.Builder applyOn(NeuralNetwork.Builder builder);

	Gene mutate(Random random);

	Colorizer colorizer();
}
