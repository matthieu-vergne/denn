package ia.window;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import ia.agent.Neural;
import ia.agent.Neural.Builder;

public class ProgramInfoBuilder implements Neural.Builder<String> {
	private final Runnable flusher;
	private final Consumer<String> neuronAdder;
	private final Consumer<Integer> neuronTargeter;
	private final Consumer<Integer> neuronReader;
	private final BiConsumer<String, Integer> outputAssigner;
	private final ByteArrayOutputStream stream = new ByteArrayOutputStream(); //

	public ProgramInfoBuilder() {
		PrintStream out = new PrintStream(stream);

		int[] indexOf = { 0, 0 };
		int reader = 0;
		int lastAdded = 1;

		List<String> neuronNames = new LinkedList<String>();
		List<Integer> readIndexes = new LinkedList<Integer>();

		Function<Integer, String> neuronIdentifier = neuronIndex -> {
			return neuronIndex + ":" + neuronNames.get(neuronIndex);
		};
		this.neuronTargeter = neuronIndex -> {
			indexOf[reader] = neuronIndex;
		};
		this.neuronReader = neuronIndex -> {
			readIndexes.add(neuronIndex);
		};
		this.flusher = () -> {
			if (!readIndexes.isEmpty()) {
				int readerIndex = indexOf[reader];
				String readerName = neuronIdentifier.apply(readerIndex);
				if (indexOf[lastAdded] != readerIndex) {
					out.println(readerName);
				}
				readIndexes.forEach(index -> {
					out.println("→" + neuronIdentifier.apply(index));
				});
				readIndexes.clear();
			}
		};
		this.neuronAdder = name -> {
			flusher.run();
			int index = neuronNames.size();
			neuronNames.add(name);
			indexOf[lastAdded] = index;
			out.println(index + " = " + name);
		};
		this.outputAssigner = (name, index) -> {
			flusher.run();
			out.println(name + " = " + neuronIdentifier.apply(index));
		};

		neuronAdder.accept("X");
		neuronAdder.accept("Y");
	}

	@Override
	public Builder<String> createNeuronWithFixedSignal(double signal) {
		neuronAdder.accept("CONST(" + signal + ")");
		return this;
	}

	@Override
	public Builder<String> createNeuronWithRandomSignal() {
		neuronAdder.accept("RAND");
		return this;
	}

	@Override
	public Builder<String> createNeuronWithSumFunction() {
		neuronAdder.accept("SUM");
		return this;
	}

	@Override
	public Builder<String> createNeuronWithWeightedSumFunction(double weight) {
		neuronAdder.accept("WEIGHT(" + weight + ")");
		return this;
	}

	@Override
	public Builder<String> createNeuronWithMinFunction() {
		neuronAdder.accept("MIN");
		return this;
	}

	@Override
	public Builder<String> createNeuronWithMaxFunction() {
		neuronAdder.accept("MAX");
		return this;
	}

	@Override
	public Builder<String> moveTo(int neuronIndex) {
		neuronTargeter.accept(neuronIndex);
		return this;
	}

	@Override
	public Builder<String> readSignalFrom(int neuronIndex) {
		neuronReader.accept(neuronIndex);
		return this;
	}

	@Override
	public Builder<String> setDXAt(int neuronIndex) {
		outputAssigner.accept("DX", neuronIndex);
		return this;
	}

	@Override
	public Builder<String> setDYAt(int neuronIndex) {
		outputAssigner.accept("DY", neuronIndex);
		return this;
	}

	@Override
	public String build() {
		flusher.run();
		return stream.toString().trim();
	}
}
