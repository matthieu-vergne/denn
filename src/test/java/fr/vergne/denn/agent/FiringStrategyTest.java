package fr.vergne.denn.agent;

import static java.util.Collections.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import fr.vergne.denn.agent.NeuralNetwork.Builder.FiringStrategy;
import fr.vergne.denn.agent.NeuralNetwork.Neuron;

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
		List<List<Integer>> inputsMap = List.of(//
				emptyList(), //
				emptyList()//
		);
		Runnable runnable = strategy.create(neurons, inputsMap);
		runnable.run();
		runnable.run();
		runnable.run();
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
		List<List<Integer>> inputsMap = List.of(//
				emptyList(), //
				emptyList(), //
				List.of(0), //
				List.of(1)//
		);
		Runnable runnable = strategy.create(neurons, inputsMap);

		x.setSignal(123);
		y.setSignal(456);
		runnable.run();
		assertEquals(123, neurons.get(2).signal());
		assertEquals(456, neurons.get(3).signal());

		x.setSignal(789);
		y.setSignal(321);
		runnable.run();
		assertEquals(789, neurons.get(2).signal());
		assertEquals(321, neurons.get(3).signal());
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
		List<List<Integer>> inputsMap = List.of(//
				emptyList(), //
				emptyList(), //
				List.of(0), //
				List.of(2), //
				List.of(3), //
				List.of(4), //
				List.of(5), //
				List.of(6)//
		);
		Runnable runnable = strategy.create(neurons, inputsMap);

		x.setSignal(123);
		runnable.run();
		assertEquals(123, neurons.get(2).signal());
		assertEquals(123, neurons.get(3).signal());
		assertEquals(123, neurons.get(4).signal());
		assertEquals(123, neurons.get(5).signal());
		assertEquals(123, neurons.get(6).signal());
		assertEquals(123, neurons.get(7).signal());

		x.setSignal(456);
		runnable.run();
		assertEquals(456, neurons.get(2).signal());
		assertEquals(456, neurons.get(3).signal());
		assertEquals(456, neurons.get(4).signal());
		assertEquals(456, neurons.get(5).signal());
		assertEquals(456, neurons.get(6).signal());
		assertEquals(456, neurons.get(7).signal());
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
		List<List<Integer>> inputsMap = List.of(//
				emptyList(), //
				emptyList(), //
				List.of(0, 1)//
		);
		Runnable runnable = strategy.create(neurons, inputsMap);

		x.setSignal(123);
		y.setSignal(321);
		runnable.run();
		assertEquals(444, neurons.get(2).signal());
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
		List<List<Integer>> inputsMap = List.of(//
				emptyList(), //
				emptyList(), //
				List.of(0), //
				List.of(2), //
				List.of(3), //
				List.of(4), //
				List.of(5), //
				List.of(6)//
		);
		Runnable runnable = strategy.create(neurons, inputsMap);

		x.setSignal(0);
		runnable.run();
		assertEquals(1, neurons.get(2).signal());
		assertEquals(3, neurons.get(3).signal());
		assertEquals(7, neurons.get(4).signal());
		assertEquals(15, neurons.get(5).signal());
		assertEquals(31, neurons.get(6).signal());
		assertEquals(63, neurons.get(7).signal());
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
