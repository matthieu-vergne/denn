package fr.vergne.denn.agent;

import static java.util.Collections.*;
import static java.util.Map.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import fr.vergne.denn.agent.NeuralNetwork.Builder.FiringStrategy;
import fr.vergne.denn.agent.NeuralNetwork.Neuron;
import fr.vergne.denn.agent.NeuralNetwork.XXX;

class FiringStrategyTest {

	static Stream<FiringStrategy> firingStrategies() {
		return Stream.of(FiringStrategy.values());
	}

	@ParameterizedTest
	@MethodSource("firingStrategies")
	void testStrategyDoesNotFailOnEmptyNetwork(FiringStrategy strategy) {
		InputNeuron x = new InputNeuron();
		InputNeuron y = new InputNeuron();
		List<Neuron> neurons = List.of(//
				x, //
				y//
		);
		Map<Integer, List<Integer>> inputsMap = Map.ofEntries(//
				entry(0, emptyList()), //
				entry(1, emptyList())//
		);
		XXX xxx = strategy.create(neurons, inputsMap, null, null);
		xxx.fire();
		xxx.fire();
		xxx.fire();
	}

	@ParameterizedTest
	@MethodSource("firingStrategies")
	void testStrategyTransfersInputs(FiringStrategy strategy) {
		InputNeuron x = new InputNeuron();
		InputNeuron y = new InputNeuron();
		List<Neuron> neurons = List.of(//
				x, //
				y, //
				Neuron.onSingleInputFunction(input -> input), //
				Neuron.onSingleInputFunction(input -> input)//
		);
		Map<Integer, List<Integer>> inputsMap = Map.ofEntries(//
				entry(0, emptyList()), //
				entry(1, emptyList()), //
				entry(2, List.of(0)), //
				entry(3, List.of(1))//
		);
		XXX xxx = strategy.create(neurons, inputsMap, null, null);

		xxx.setX(123);
		xxx.setY(456);
		xxx.fire();
		assertEquals(123, xxx.neurons().get(2).signal());
		assertEquals(456, xxx.neurons().get(3).signal());

		xxx.setX(789);
		xxx.setY(321);
		xxx.fire();
		assertEquals(789, xxx.neurons().get(2).signal());
		assertEquals(321, xxx.neurons().get(3).signal());
	}

	@ParameterizedTest
	@MethodSource("firingStrategies")
	void testStrategyTransfersThroughNetwork(FiringStrategy strategy) {
		InputNeuron x = new InputNeuron();
		InputNeuron y = new InputNeuron();
		List<Neuron> neurons = List.of(//
				x, //
				y, //
				Neuron.onSingleInputFunction(input -> input), //
				Neuron.onSingleInputFunction(input -> input), //
				Neuron.onSingleInputFunction(input -> input), //
				Neuron.onSingleInputFunction(input -> input), //
				Neuron.onSingleInputFunction(input -> input), //
				Neuron.onSingleInputFunction(input -> input)//
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
		XXX xxx = strategy.create(neurons, inputsMap, null, null);

		xxx.setX(123);
		xxx.fire();
		assertEquals(123, xxx.neurons().get(2).signal());
		assertEquals(123, xxx.neurons().get(3).signal());
		assertEquals(123, xxx.neurons().get(4).signal());
		assertEquals(123, xxx.neurons().get(5).signal());
		assertEquals(123, xxx.neurons().get(6).signal());
		assertEquals(123, xxx.neurons().get(7).signal());

		xxx.setX(456);
		xxx.fire();
		assertEquals(456, xxx.neurons().get(2).signal());
		assertEquals(456, xxx.neurons().get(3).signal());
		assertEquals(456, xxx.neurons().get(4).signal());
		assertEquals(456, xxx.neurons().get(5).signal());
		assertEquals(456, xxx.neurons().get(6).signal());
		assertEquals(456, xxx.neurons().get(7).signal());
	}

	@ParameterizedTest
	@MethodSource("firingStrategies")
	void testStrategyComputeNeuronFunction(FiringStrategy strategy) {
		InputNeuron x = new InputNeuron();
		InputNeuron y = new InputNeuron();
		List<Neuron> neurons = List.of(//
				x, //
				y, //
				Neuron.onInputsFunction(DoubleStream::sum)//
		);
		Map<Integer, List<Integer>> inputsMap = Map.ofEntries(//
				entry(0, emptyList()), //
				entry(1, emptyList()), //
				entry(2, List.of(0, 1))//
		);
		XXX xxx = strategy.create(neurons, inputsMap, null, null);

		xxx.setX(123);
		xxx.setY(321);
		xxx.fire();
		assertEquals(444, xxx.neurons().get(2).signal());
	}

	@ParameterizedTest
	@MethodSource("firingStrategies")
	void testStrategyFollowsDependcencies(FiringStrategy strategy) {
		InputNeuron x = new InputNeuron();
		InputNeuron y = new InputNeuron();
		List<Neuron> neurons = List.of(//
				x, //
				y, //
				Neuron.onSingleInputFunction(input -> input + 1), //
				Neuron.onSingleInputFunction(input -> input + 2), //
				Neuron.onSingleInputFunction(input -> input + 4), //
				Neuron.onSingleInputFunction(input -> input + 8), //
				Neuron.onSingleInputFunction(input -> input + 16), //
				Neuron.onSingleInputFunction(input -> input + 32)//
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
		XXX xxx = strategy.create(neurons, inputsMap, null, null);

		xxx.setX(0);
		xxx.fire();
		assertEquals(1, xxx.neurons().get(2).signal());
		assertEquals(3, xxx.neurons().get(3).signal());
		assertEquals(7, xxx.neurons().get(4).signal());
		assertEquals(15, xxx.neurons().get(5).signal());
		assertEquals(31, xxx.neurons().get(6).signal());
		assertEquals(63, xxx.neurons().get(7).signal());
	}

	static class InputNeuron implements Neuron {
		private double signal = 0;

		@Override
		public void fire(List<Supplier<Double>> inputs) {
			// Nothing to do when input
		}

		public void setSignal(double signal) {
			this.signal = signal;
		}

		@Override
		public double signal() {
			return signal;
		}

	}
}
