package fr.vergne.denn.agent;

import static fr.vergne.denn.agent.NeuralNetwork.Builder.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// FIXME
@Disabled("Moves are now restricted to {-1;0;1}, we need to test actual signals another way")
class NeuralNetworkBuilderTest {

	@Test
	void testInitialBuilderReturnsNonNullNetwork() {
		assertNotNull(new NeuralNetwork.Builder().build());
	}

	@Test
	void testInitialBuilderReturnsWorkingNetwork() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		NeuralNetwork network = builder.build();
		assertDoesNotThrow(() -> {
			network.setXSignal(654);
			network.setYSignal(123);
		});
		assertDoesNotThrow(() -> network.fire());
		assertNotNull(network.dXSignal());
		assertNotNull(network.dYSignal());
	}

	@Test
	void testInitialBuilderReturnsNonMovingNetwork() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		NeuralNetwork network = builder.build();

		network.setXSignal(651);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(0, network.dXSignal());
		assertEquals(0, network.dYSignal());
	}

	@Test
	void testDXNeuronOnXNeuronProducesDXAtX() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.setDXAt(xNeuron());
		NeuralNetwork network = builder.build();

		int x = 651;
		network.setXSignal(x);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(x, network.dXSignal());
	}

	@Test
	void testDYNeuronOnYNeuronProducesDYAtY() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.setDYAt(yNeuron());
		NeuralNetwork network = builder.build();

		int y = -3254;
		network.setXSignal(651);
		network.setYSignal(y);
		network.fire();
		assertEquals(y, network.dYSignal());
	}

	@Test
	void testDXNeuronOnYNeuronProducesDXAtY() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.setDXAt(yNeuron());
		NeuralNetwork network = builder.build();

		int y = -3254;
		network.setXSignal(651);
		network.setYSignal(y);
		network.fire();
		assertEquals(y, network.dXSignal());
	}

	@Test
	void testDYNeuronOnXNeuronProducesDYAtX() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.setDYAt(xNeuron());
		NeuralNetwork network = builder.build();

		int x = 651;
		network.setXSignal(x);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(x, network.dYSignal());
	}

	@Test
	void testDXNeuronOnNewNeuronProducesDXAtNewNeuronRoundedSignal() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(inputs -> 123.8);
		builder.setDXAt(lastNeuron());
		NeuralNetwork network = builder.build();

		network.setXSignal(651);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(124, network.dXSignal());
	}

	@Test
	void testDYNeuronOnNewNeuronProducesDYAtNewNeuronRoundedSignal() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(inputs -> 123.8);
		builder.setDYAt(lastNeuron());
		NeuralNetwork network = builder.build();

		network.setXSignal(651);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(124, network.dYSignal());
	}

	@Test
	void testFixedSignalNeuronProvidesSpecifiedSignal() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(fixedSignal(123));
		builder.setDXAt(lastNeuron());
		NeuralNetwork network = builder.build();

		network.setXSignal(651);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(123, network.dXSignal());
	}

	@Test
	void testMoveToLastNeuronAllowsCurrentNeuronToReferToLastNeuron() {
		NeuralNetwork.Builder builder = new NeuralNetwork.Builder();
		builder.createNeuronWith(fixedSignal(123));
		builder.moveTo(lastNeuron());
		builder.setDXAt(currentNeuron());
		NeuralNetwork network = builder.build();

		network.setXSignal(651);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(123, network.dXSignal());
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

		network.setXSignal(651);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(1, network.dXSignal());
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

		network.setXSignal(651);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(2, network.dXSignal());
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

		network.setXSignal(651);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(6, network.dYSignal());
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

		network.setXSignal(651);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(-30, network.dYSignal());
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

		network.setXSignal(651);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(1, network.dYSignal());
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

		network.setXSignal(651);
		network.setYSignal(-3254);
		network.fire();
		assertEquals(3, network.dYSignal());
	}

}
