package fr.vergne.denn.agent;

import static java.util.Collections.*;
import static java.util.Map.*;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import fr.vergne.denn.agent.FiringStrategyTest.InputNeuron;
import fr.vergne.denn.agent.NeuralNetwork.Builder.FiringStrategy;
import fr.vergne.denn.agent.NeuralNetwork.Neuron;
import fr.vergne.denn.agent.NeuralNetwork.XXX;

public class FiringStrategyBenchmark {
	public static void main(String[] args) throws Exception {
		org.openjdk.jmh.Main.main(args);
	}

	@State(Scope.Benchmark)
	public static class ExecutionPlan {

		@Param
		public FiringStrategy strategy;

		public XXX xxx;
		public final Random random = new Random(0);
		public final InputNeuron x = new InputNeuron();
		public final InputNeuron y = new InputNeuron();

		@Setup(Level.Invocation)
		public void setUp() {
			// Network which goes to position (50,50)
			List<Neuron> neurons = List.of(//
					// Inputs
					x, //
					y, //
					Neuron.onSignalSupplier(() -> 1.0), //
					Neuron.onSignalSupplier(() -> random.nextDouble()), //
					Neuron.onSignalSupplier(() -> random.nextDouble()), //
					// Weights dX
					Neuron.onInputsFunction(inputs -> inputs.sum() * -1.0), //
					Neuron.onInputsFunction(inputs -> inputs.sum() * 0.0), //
					Neuron.onInputsFunction(inputs -> inputs.sum() * 50.0), //
					Neuron.onInputsFunction(inputs -> inputs.sum() * 0.0), //
					Neuron.onInputsFunction(inputs -> inputs.sum() * 0.0), //
					// dX
					Neuron.onInputsFunction(inputs -> inputs.sum()), //
					// Weights dY
					Neuron.onInputsFunction(inputs -> inputs.sum() * -1.0), //
					Neuron.onInputsFunction(inputs -> inputs.sum() * 0.0), //
					Neuron.onInputsFunction(inputs -> inputs.sum() * 50.0), //
					Neuron.onInputsFunction(inputs -> inputs.sum() * 0.0), //
					Neuron.onInputsFunction(inputs -> inputs.sum() * 0.0), //
					// dY
					Neuron.onInputsFunction(inputs -> inputs.sum()) //
			);
			Map<Integer, List<Integer>> inputsMap = Map.ofEntries(//
					// Inputs
					entry(0, emptyList()), //
					entry(1, emptyList()), //
					entry(2, emptyList()), //
					entry(3, emptyList()), //
					entry(4, emptyList()), //
					// Weights dX
					entry(5, List.of(0)), //
					entry(6, List.of(1)), //
					entry(7, List.of(2)), //
					entry(8, List.of(3)), //
					entry(9, List.of(4)), //
					// dX
					entry(10, List.of(5, 6, 7, 8, 9)), //
					// Weights dY
					entry(11, List.of(0)), //
					entry(12, List.of(1)), //
					entry(13, List.of(2)), //
					entry(14, List.of(3)), //
					entry(15, List.of(4)), //
					// dY
					entry(16, List.of(11, 12, 13, 14, 15)) //
			);
			this.xxx = this.strategy.create(neurons, inputsMap, null, null);
		}
	}

	@Benchmark()
	@Fork(value = 1, warmups = 1)
	@Warmup(iterations = 3)
	@Measurement(iterations = 20)
	@BenchmarkMode(Mode.SampleTime)
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	public void benchmark(ExecutionPlan plan) {
		plan.xxx.setX(12);
		plan.xxx.setY(58);
		plan.xxx.fire();
	}
}
