package ia.agent;

import static ia.agent.NeuralNetwork.Builder2.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ia.agent.NeuralNetwork.Builder2;
import ia.terrain.Move;
import ia.terrain.Position;

class NeuralNetworkBuilderTest {

	@Test
	void testInitialBuilderReturnsNonNullNetwork() {
		assertNotNull(new NeuralNetwork.Builder2().build());
	}

	@Test
	void testInitialBuilderReturnsWorkingNetwork() {
		Builder2 builder = new NeuralNetwork.Builder2();
		NeuralNetwork network = builder.build();
		assertDoesNotThrow(() -> network.setInputs(Position.at(654, 123)));
		assertDoesNotThrow(() -> network.fire());
		assertNotNull(network.output());
	}

	@Test
	void testInitialBuilderReturnsNonMovingNetwork() {
		Builder2 builder = new NeuralNetwork.Builder2();
		NeuralNetwork network = builder.build();
		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(Move.create(0, 0), network.output());
	}

	@Test
	void testDXNeuronOnXNeuronProducesDXAtX() {
		Builder2 builder = new NeuralNetwork.Builder2();
		builder.setDXAt(xNeuron());
		NeuralNetwork network = builder.build();
		
		int x = 651;
		network.setInputs(Position.at(x, -3254));
		network.fire();
		assertEquals(x, network.output().dX());
	}

	@Test
	void testDYNeuronOnYNeuronProducesDYAtY() {
		Builder2 builder = new NeuralNetwork.Builder2();
		builder.setDYAt(yNeuron());
		NeuralNetwork network = builder.build();
		
		int y = -3254;
		network.setInputs(Position.at(651, y));
		network.fire();
		assertEquals(y, network.output().dY());
	}

	@Test
	void testDXNeuronOnYNeuronProducesDXAtY() {
		Builder2 builder = new NeuralNetwork.Builder2();
		builder.setDXAt(yNeuron());
		NeuralNetwork network = builder.build();
		
		int y = -3254;
		network.setInputs(Position.at(651, y));
		network.fire();
		assertEquals(y, network.output().dX());
	}

	@Test
	void testDYNeuronOnXNeuronProducesDYAtX() {
		Builder2 builder = new NeuralNetwork.Builder2();
		builder.setDYAt(xNeuron());
		NeuralNetwork network = builder.build();
		
		int x = 651;
		network.setInputs(Position.at(x, -3254));
		network.fire();
		assertEquals(x, network.output().dY());
	}

	@Test
	void testDXNeuronOnNewNeuronProducesDXAtNewNeuronRoundedSignal() {
		Builder2 builder = new NeuralNetwork.Builder2();
		builder.createNeuronWith(inputs -> 123.8);
		builder.setDXAt(lastNeuron());
		NeuralNetwork network = builder.build();
		
		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(124, network.output().dX());
	}

	@Test
	void testDYNeuronOnNewNeuronProducesDYAtNewNeuronRoundedSignal() {
		Builder2 builder = new NeuralNetwork.Builder2();
		builder.createNeuronWith(inputs -> 123.8);
		builder.setDYAt(lastNeuron());
		NeuralNetwork network = builder.build();
		
		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(124, network.output().dY());
	}
	
	@Test
	void testFixedSignalNeuronProvidesSpecifiedSignal() {
		Builder2 builder = new NeuralNetwork.Builder2();
		builder.createNeuronWith(fixedSignal(123));
		builder.setDXAt(lastNeuron());
		NeuralNetwork network = builder.build();
		
		network.setInputs(Position.at(651, -3254));
		network.fire();
		assertEquals(123, network.output().dX());
	}
	
	@Test
	void testMoveToLastNeuronAllowsCurrentNeuronToReferToLastNeuron() {
		Builder2 builder = new NeuralNetwork.Builder2();
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
		Builder2 builder = new NeuralNetwork.Builder2();
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
		Builder2 builder = new NeuralNetwork.Builder2();
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
		Builder2 builder = new NeuralNetwork.Builder2();
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
		Builder2 builder = new NeuralNetwork.Builder2();
		builder.createNeuronWith(fixedSignal(1));
		builder.createNeuronWith(fixedSignal(2));
		builder.createNeuronWith(fixedSignal(3));
		builder.createNeuronWith(weightFunction(-5));
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
		Builder2 builder = new NeuralNetwork.Builder2();
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
		Builder2 builder = new NeuralNetwork.Builder2();
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
