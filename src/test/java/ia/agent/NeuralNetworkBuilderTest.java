package ia.agent;

import static ia.agent.NeuralNetwork.Builder.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ia.utils.Position;

class NeuralNetworkBuilderTest {

	@Test
	void testInitialBuilderReturnsNonNullNetwork() {
		assertNotNull(new NeuralNetwork.Builder().build());
	}

	@Test
	void testInitialBuilderReturnsWorkingNetwork() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		NeuralNetwork network = builder.build();
		assertDoesNotThrow(() -> network.setInputs(Position.at(654, 123)));
		assertDoesNotThrow(() -> network.fire());
		assertNotNull(network.output());
	}

	@Test
	void testInitialBuilderReturnsNonMovingNetwork() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		NeuralNetwork network = builder.build();
		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(Position.Move.create(0, 0), network.output());
	}

	@Test
	void testDXNeuronOnXNeuronProducesDXAtX() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.setDXAt(xNeuron());
		NeuralNetwork network = builder.build();

		int x = 651;
		network.setInputs(Position.at(x, -3254));
		network.fire();
		assertEquals(x, network.output().dX());
	}

	@Test
	void testDYNeuronOnYNeuronProducesDYAtY() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.setDYAt(yNeuron());
		NeuralNetwork network = builder.build();

		int y = -3254;
		network.setInputs(Position.at(651, y));
		network.fire();
		assertEquals(y, network.output().dY());
	}

	@Test
	void testDXNeuronOnYNeuronProducesDXAtY() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.setDXAt(yNeuron());
		NeuralNetwork network = builder.build();

		int y = -3254;
		network.setInputs(Position.at(651, y));
		network.fire();
		assertEquals(y, network.output().dX());
	}

	@Test
	void testDYNeuronOnXNeuronProducesDYAtX() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.setDYAt(xNeuron());
		NeuralNetwork network = builder.build();

		int x = 651;
		network.setInputs(Position.at(x, -3254));
		network.fire();
		assertEquals(x, network.output().dY());
	}

	@Test
	void testDXNeuronOnNewNeuronProducesDXAtNewNeuronRoundedSignal() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(inputs -> 123.8);
		builder.setDXAt(lastNeuron());
		NeuralNetwork network = builder.build();

		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(124, network.output().dX());
	}

	@Test
	void testDYNeuronOnNewNeuronProducesDYAtNewNeuronRoundedSignal() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(inputs -> 123.8);
		builder.setDYAt(lastNeuron());
		NeuralNetwork network = builder.build();

		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(124, network.output().dY());
	}

	@Test
	void testFixedSignalNeuronProvidesSpecifiedSignal() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(fixedSignal(123));
		builder.setDXAt(lastNeuron());
		NeuralNetwork network = builder.build();

		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(123, network.output().dX());
	}

	@Test
	void testMoveToLastNeuronAllowsCurrentNeuronToReferToLastNeuron() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(fixedSignal(123));
		builder.moveTo(lastNeuron());
		builder.setDXAt(currentNeuron());
		NeuralNetwork network = builder.build();

		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(123, network.output().dX());
	}

	@Test
	void testMoveToPreviousNeuronMovesToNeuronJustBeforeCurrent() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(fixedSignal(1));
		builder.createNeuronWith(fixedSignal(2));
		builder.moveTo(lastNeuron());
		builder.moveTo(previousNeuron());
		builder.setDXAt(currentNeuron());
		NeuralNetwork network = builder.build();

		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(1, network.output().dX());
	}

	@Test
	void testMoveToNextNeuronMovesToNeuronJustAfterCurrent() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(fixedSignal(1));
		builder.moveTo(lastNeuron());
		builder.createNeuronWith(fixedSignal(2));
		builder.moveTo(nextNeuron());
		builder.setDXAt(currentNeuron());
		NeuralNetwork network = builder.build();

		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(2, network.output().dX());
	}

	@Test
	void testSumNeuronSumsInputSignals() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(fixedSignal(1));
		builder.createNeuronWith(fixedSignal(2));
		builder.createNeuronWith(fixedSignal(3));
		builder.createNeuronWith(sumFunction());
		builder.moveTo(lastNeuron());
		builder.readSignalFrom(relativeNeuron(-1));
		builder.readSignalFrom(relativeNeuron(-2));
		builder.readSignalFrom(relativeNeuron(-3));
		builder.setDYAt(currentNeuron());
		NeuralNetwork network = builder.build();

		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(6, network.output().dY());
	}

	@Test
	void testWeightedNeuronMultiplyAndSumInputSignals() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(fixedSignal(1));
		builder.createNeuronWith(fixedSignal(2));
		builder.createNeuronWith(fixedSignal(3));
		builder.createNeuronWith(weightedSumFunction(-5));
		builder.moveTo(lastNeuron());
		builder.readSignalFrom(relativeNeuron(-1));
		builder.readSignalFrom(relativeNeuron(-2));
		builder.readSignalFrom(relativeNeuron(-3));
		builder.setDYAt(currentNeuron());
		NeuralNetwork network = builder.build();

		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(-30, network.output().dY());
	}

	@Test
	void testMinNeuronReturnsMinInputSignal() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(fixedSignal(1));
		builder.createNeuronWith(fixedSignal(2));
		builder.createNeuronWith(fixedSignal(3));
		builder.createNeuronWith(minFunction());
		builder.moveTo(lastNeuron());
		builder.readSignalFrom(relativeNeuron(-1));
		builder.readSignalFrom(relativeNeuron(-2));
		builder.readSignalFrom(relativeNeuron(-3));
		builder.setDYAt(currentNeuron());
		NeuralNetwork network = builder.build();

		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(1, network.output().dY());
	}

	@Test
	void testMaxNeuronReturnsMaxInputSignal() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(fixedSignal(1));
		builder.createNeuronWith(fixedSignal(2));
		builder.createNeuronWith(fixedSignal(3));
		builder.createNeuronWith(maxFunction());
		builder.moveTo(lastNeuron());
		builder.readSignalFrom(relativeNeuron(-1));
		builder.readSignalFrom(relativeNeuron(-2));
		builder.readSignalFrom(relativeNeuron(-3));
		builder.setDYAt(currentNeuron());
		NeuralNetwork network = builder.build();

		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(3, network.output().dY());
	}

}
