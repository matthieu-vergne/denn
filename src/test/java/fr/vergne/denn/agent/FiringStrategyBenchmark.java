package fr.vergne.denn.agent;

import static java.util.Collections.*;

import java.util.List;
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

public class FiringStrategyBenchmark {
	public static void main(String[] args) throws Exception {
		org.openjdk.jmh.Main.main(args);
	}

	@State(Scope.Benchmark)
	public static class ExecutionPlan {

		@Param
		public FiringStrategy strategy;

		public Runnable runnable;
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
			List<List<Integer>> inputsMap = List.of(//
					// Inputs
					emptyList(), //
					emptyList(), //
					emptyList(), //
					emptyList(), //
					emptyList(), //
					// Weights dX
					List.of(0), //
					List.of(1), //
					List.of(2), //
					List.of(3), //
					List.of(4), //
					// dX
					List.of(5, 6, 7, 8, 9), //
					// Weights dY
					List.of(0), //
					List.of(1), //
					List.of(2), //
					List.of(3), //
					List.of(4), //
					// dY
					List.of(11, 12, 13, 14, 15) //
			);
			this.runnable = this.strategy.create(neurons, inputsMap);
		}
	}

	@Benchmark()
	@Fork(value = 1, warmups = 1)
	@Warmup(iterations = 3)
	@Measurement(iterations = 5)
	@BenchmarkMode(Mode.SampleTime)
	@OutputTimeUnit(TimeUnit.NANOSECONDS)
	public void benchmark(ExecutionPlan plan) {
		plan.x.setSignal(12);
		plan.y.setSignal(58);
		plan.runnable.run();
	}
}
