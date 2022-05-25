package ia.window;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ia.window.Button.Action;

class ButtonActionTest {

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3, 4, 5 })
	void testTimes(int count) {
		int[] counter = { 0 };
		Action action = () -> counter[0]++;
		Action repeatedAction = action.times(count);
		assertEquals(0, counter[0]);
		repeatedAction.execute();
		assertEquals(count, counter[0]);
	}

	@Test
	void testMultipleTimes() {
		int[] counter = { 0 };
		Action action = () -> counter[0]++;
		Action repeatedAction = action.times(2).times(3).times(5);
		assertEquals(0, counter[0]);
		repeatedAction.execute();
		assertEquals(30, counter[0]);
	}

}
