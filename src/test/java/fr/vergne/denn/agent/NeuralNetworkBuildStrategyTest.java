package fr.vergne.denn.agent;

import static java.util.Collections.*;
import static java.util.Map.*;
import static java.util.stream.Collectors.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import fr.vergne.denn.agent.NeuralNetwork.Builder.BuildStrategy;
import fr.vergne.denn.agent.NeuralNetwork.Builder.NeuronDefinition;
import fr.vergne.denn.agent.NeuralNetwork.Neuron;

class NeuralNetworkBuildStrategyTest {

	static Stream<BuildStrategy> buildStrategies() {
		return Stream.of(BuildStrategy.values());
	}

	@ParameterizedTest
	@MethodSource("buildStrategies")
	void testStrategyDoesNotFailOnEmptyNetwork(BuildStrategy strategy) {
		NonUsedNeuron x = new NonUsedNeuron();
		NonUsedNeuron y = new NonUsedNeuron();
		List<Neuron> neurons = List.of(//
				x, //
				y//
		);
		Map<Integer, List<Integer>> inputsMap = Map.ofEntries(//
				entry(0, emptyList()), //
				entry(1, emptyList())//
		);
		List<NeuronDefinition> neuronsDefinitions = buildDefinitions(neurons, inputsMap);
		NeuralNetwork network = strategy.buildNetwork(neuronsDefinitions, 0, 1);
		network.fire();
		network.fire();
		network.fire();
	}

	static List<NeuronDefinition> buildDefinitions(List<Neuron> neurons, Map<Integer, List<Integer>> inputsMap) {
		return inputsMap.entrySet().stream()//
				.sorted(Comparator.comparing(Entry::getKey)).map(entry -> {
					Integer neuronIndex = entry.getKey();
					Neuron neuron = neurons.get(neuronIndex);

					List<Integer> inputIndexes = entry.getValue();

					return new NeuronDefinition(neuron, inputIndexes);
				}).collect(toList());
	}

	@ParameterizedTest
	@MethodSource("buildStrategies")
	void testStrategyTransfersInputsUntilOutputs(BuildStrategy strategy) {
		NonUsedNeuron x = new NonUsedNeuron();
		NonUsedNeuron y = new NonUsedNeuron();
		List<Neuron> neurons = List.of(//
				x, //
				y, //
				Neuron.onSingleInputFunction(input -> input), //
				Neuron.onSingleInputFunction(input -> input), //
				Neuron.onSingleInputFunction(input -> input), //
				Neuron.onSingleInputFunction(input -> input)//
		);
		Map<Integer, List<Integer>> inputsMap = Map.ofEntries(//
				entry(0, emptyList()), //
				entry(1, emptyList()), //
				entry(2, List.of(0)), //
				entry(3, List.of(1)), //
				entry(4, List.of(2)), //
				entry(5, List.of(3))//
		);
		List<NeuronDefinition> neuronsDefinitions = buildDefinitions(neurons, inputsMap);
		NeuralNetwork network = strategy.buildNetwork(neuronsDefinitions, 4, 5);

		// TODO Test various network structures
		// TODO Test various trials sequences
		List.of(//
				new Trial(new Inputs(123, 456), new Outputs(123, 456)), //
				new Trial(new Inputs(789, 321), new Outputs(789, 321))//
		).forEach(trial -> trial.test(network));
	}

	@ParameterizedTest
	@MethodSource("buildStrategies")
	void testStrategyTransfersThroughNetwork(BuildStrategy strategy) {
		NonUsedNeuron x = new NonUsedNeuron();
		NonUsedNeuron y = new NonUsedNeuron();
		List<Double> signals = new ArrayList<>(List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
		List<Neuron> neurons = List.of(//
				x, //
				y, //
				Neuron.onSingleInputFunction(computeAndStore(signals, 0, input -> input)), //
				Neuron.onSingleInputFunction(computeAndStore(signals, 1, input -> input)), //
				Neuron.onSingleInputFunction(computeAndStore(signals, 2, input -> input)), //
				Neuron.onSingleInputFunction(computeAndStore(signals, 3, input -> input)), //
				Neuron.onSingleInputFunction(computeAndStore(signals, 4, input -> input)), //
				Neuron.onSingleInputFunction(computeAndStore(signals, 5, input -> input))//
		);
		Map<Integer, List<Integer>> inputsMap = Map.ofEntries(//
				entry(0, emptyList()), //
				entry(1, emptyList()), //
				entry(2, List.of(0)), //
				entry(3, List.of(2)), //
				entry(4, List.of(3)), //
				entry(5, List.of(4)), //
				entry(6, List.of(5)), //
				entry(7, List.of(6))//
		);
		List<NeuronDefinition> neuronsDefinitions = buildDefinitions(neurons, inputsMap);
		NeuralNetwork network = strategy.buildNetwork(neuronsDefinitions, 7, 7);

		// TODO Test Y
		// TODO Test jumps
		Stream.of(123.0, 456.0).forEach(signal -> {
			network.setXSignal(signal);
			network.fire();
			assertEquals(List.of(signal, signal, signal, signal, signal, signal), signals, "Input = " + signal);
		});
	}

	@ParameterizedTest
	@MethodSource("buildStrategies")
	void testStrategyComputeNeuronFunction(BuildStrategy strategy) {
		// TODO Move to neurons tests
		NonUsedNeuron x = new NonUsedNeuron();
		NonUsedNeuron y = new NonUsedNeuron();
		List<Neuron> neurons = List.of(//
				x, //
				y, //
				Neuron.onInputsFunction(inputs -> inputs.sum()), //
				Neuron.onInputsFunction(inputs -> inputs.average().getAsDouble())//
		);
		Map<Integer, List<Integer>> inputsMap = Map.ofEntries(//
				entry(0, emptyList()), //
				entry(1, emptyList()), //
				entry(2, List.of(0, 1)), //
				entry(3, List.of(0, 1))//
		);
		List<NeuronDefinition> neuronsDefinitions = buildDefinitions(neurons, inputsMap);
		NeuralNetwork network = strategy.buildNetwork(neuronsDefinitions, 2, 3);

		new Trial(new Inputs(123, 321), new Outputs(444, 222)).test(network);
	}

	@ParameterizedTest
	@MethodSource("buildStrategies")
	void testStrategyFollowsDependencies(BuildStrategy strategy) {
		NonUsedNeuron x = new NonUsedNeuron();
		NonUsedNeuron y = new NonUsedNeuron();
		List<Double> signals = new ArrayList<>(List.of(0.0, 0.0, 0.0, 0.0, 0.0, 0.0));
		List<Neuron> neurons = List.of(//
				x, //
				y, //
				Neuron.onSingleInputFunction(computeAndStore(signals, 0, input -> input + 1)), //
				Neuron.onSingleInputFunction(computeAndStore(signals, 1, input -> input + 2)), //
				Neuron.onSingleInputFunction(computeAndStore(signals, 2, input -> input + 4)), //
				Neuron.onSingleInputFunction(computeAndStore(signals, 3, input -> input + 8)), //
				Neuron.onSingleInputFunction(computeAndStore(signals, 4, input -> input + 16)), //
				Neuron.onSingleInputFunction(computeAndStore(signals, 5, input -> input + 32))//
		);
		Map<Integer, List<Integer>> inputsMap = Map.ofEntries(//
				entry(0, emptyList()), //
				entry(1, emptyList()), //
				entry(2, List.of(0)), //
				entry(3, List.of(2)), //
				entry(4, List.of(3)), //
				entry(5, List.of(4)), //
				entry(6, List.of(5)), //
				entry(7, List.of(6))//
		);
		List<NeuronDefinition> neuronsDefinitions = buildDefinitions(neurons, inputsMap);
		NeuralNetwork network = strategy.buildNetwork(neuronsDefinitions, 7, 7);

		// TODO Test several inputs
		// TODO Test Y
		// TODO Test jumps
		network.setXSignal(0);
		network.fire();
		assertEquals(List.of(1.0, 3.0, 7.0, 15.0, 31.0, 63.0), signals);
	}

	private UnaryOperator<Double> computeAndStore(List<Double> signals, int signalIndex,
			UnaryOperator<Double> computer) {
		return input -> {
			Double result = computer.apply(input);
			signals.set(signalIndex, result);
			return result;
		};
	}

	static class NonUsedNeuron implements Neuron {
		RuntimeException runtimeException = new RuntimeException("Neuron should not be used");

		@Override
		public void fire(List<Supplier<Double>> inputs) {
			throw runtimeException;
		}

		public void setSignal(double signal) {
			throw runtimeException;
		}

		@Override
		public double signal() {
			throw runtimeException;
		}

	}

	static record Inputs(double x, double y) {
		public void apply(NeuralNetwork network) {
			network.setXSignal(x);
			network.setYSignal(y);
		}
	}

	static record Outputs(double dX, double dY) {
		public static Outputs from(NeuralNetwork network) {
			return new Outputs(network.dXSignal(), network.dYSignal());
		}
	}

	static record Trial(Inputs inputs, Outputs outputs) {
		public void test(NeuralNetwork network) {
			this.inputs().apply(network);
			network.fire();
			assertEquals(this.outputs(), Outputs.from(network), this.toString());
		}
	}
}
