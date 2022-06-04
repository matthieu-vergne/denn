package ia.window;

import static java.util.stream.Collectors.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ia.agent.LayeredNetwork;
import ia.agent.LayeredNetwork.Description;
import ia.agent.Neural;
import ia.agent.Neural.Builder;

public class LayeredNetworkInfoBuilder implements Neural.Builder<LayeredNetwork.Description> {

	private int nextNeuronIndex = 0;
	private int currentIndex = 0;
	private final Collection<Integer> nonWeightNeurons;
	private final Map<Integer, Double> weightNeurons;
	private int dX;
	private int dY;

	record Reading(int reader, int readee) {
	}

	private final Collection<Reading> readings;

	public LayeredNetworkInfoBuilder() {
		nonWeightNeurons = new LinkedList<>();
		weightNeurons = new HashMap<Integer, Double>();
		readings = new LinkedList<>();
		nonWeightNeurons.add(nextNeuronIndex++);// x = 0
		nonWeightNeurons.add(nextNeuronIndex++);// y = 1
	}

	@Override
	public Builder<Description> createNeuronWithFixedSignal(double signal) {
		nonWeightNeurons.add(nextNeuronIndex++);
		return this;
	}

	@Override
	public Builder<Description> createNeuronWithWeightedSumFunction(double weight) {
		weightNeurons.put(nextNeuronIndex++, weight);
		return this;
	}

	@Override
	public Builder<Description> createNeuronWithRandomSignal() {
		nonWeightNeurons.add(nextNeuronIndex++);
		return this;
	}

	@Override
	public Builder<Description> createNeuronWithSumFunction() {
		nonWeightNeurons.add(nextNeuronIndex++);
		return this;
	}

	@Override
	public Builder<Description> createNeuronWithMinFunction() {
		nonWeightNeurons.add(nextNeuronIndex++);
		return this;
	}

	@Override
	public Builder<Description> createNeuronWithMaxFunction() {
		nonWeightNeurons.add(nextNeuronIndex++);
		return this;
	}

	@Override
	public Builder<Description> moveTo(int neuronIndex) {
		currentIndex = neuronIndex;
		return this;
	}

	@Override
	public Builder<Description> readSignalFrom(int neuronIndex) {
		readings.add(new Reading(currentIndex, neuronIndex));
		return this;
	}

	@Override
	public Builder<Description> setDXAt(int neuronIndex) {
		dX = neuronIndex;
		return this;
	}

	@Override
	public Builder<Description> setDYAt(int neuronIndex) {
		dY = neuronIndex;
		return this;
	}

	record Extract(Set<Integer> layer, Collection<Synapse> remaining) {
	}

	record Synapse(int reader, double weight, int readee) {
	}

	@Override
	public Description build() {
		List<Synapse> synapses = weightNeurons.entrySet().stream().map(entry -> {
			Integer weightNeuron = entry.getKey();
			Double weight = entry.getValue();
			Integer reader = null;
			Integer readee = null;
			for (Reading reading : readings) {
				if (reading.reader == weightNeuron) {
					readee = reading.readee;
				}
				if (reading.readee == weightNeuron) {
					reader = reading.reader;
				}
			}
			return new Synapse(reader, weight, readee);
		}).collect(toList());

		Collection<Collection<Integer>> layers = new LinkedList<>();
		Extract lastExtract = new Extract(null, synapses);
		while (!lastExtract.remaining().isEmpty()) {
			lastExtract = extractNextLayer(lastExtract.remaining());
			layers.add(lastExtract.layer());
		}
		List<Integer> alreadyLayered = layers.stream().flatMap(layer -> layer.stream()).collect(toList());
		Set<Integer> lastLayer = new LinkedHashSet<>();
		lastLayer.addAll(nonWeightNeurons);
		lastLayer.removeAll(alreadyLayered);
		layers.add(lastLayer);

		return new Description() {

			@Override
			public Collection<Collection<Integer>> layers() {
				return layers;
			}

			@Override
			public Double weight(Integer neuron1, Integer neuron2) {
				return synapses.stream()//
						.filter(synapse -> synapse.readee == neuron1 && synapse.reader == neuron2)//
						.findFirst()//
						.map(Synapse::weight).orElse(0.0);
			}

			@Override
			public String name(Integer neuron) {
				if (dX == neuron) {
					return neuron.toString() + ":dX";
				}
				if (dY == neuron) {
					return neuron.toString() + ":dY";
				}
				return neuron.toString();
			}
		};
	}

	private Extract extractNextLayer(Collection<Synapse> readings) {
		Set<Integer> readers = new HashSet<Integer>();
		Set<Integer> readees = new HashSet<Integer>();
		readings.forEach(reading -> {
			readers.add(reading.reader);
			readees.add(reading.readee);
		});
		readees.removeAll(readers);
		Collection<Synapse> remaining = readings.stream()//
				.filter(reading -> !readees.contains(reading.readee))//
				.collect(toList());
		return new Extract(readees, remaining);
	}
}
