package ia.window;

import static java.time.Instant.*;
import static java.time.temporal.ChronoUnit.*;
import static java.util.stream.Collectors.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import ia.window.Button.Action;
import ia.window.Button.CompositeAction;
import ia.window.ButtonActionTest.DynamicAction.CallsAsserter;

class ButtonActionTest {

	@Test
	void testThenGeneratesStepsLazilyWithIterator() {
		DynamicAction action = new DynamicAction(2);// 1 action = 2 steps
		CompositeAction repeatedAction = action.then(action);// 2 actions = 4 steps
		CallsAsserter assertCallsOf = action.callsAsserter();

		assertCallsOf.stream(0).check(0).next(0).step(0);

		Iterator<Action> iterator = repeatedAction.steps().iterator();
		assertCallsOf.stream(0).check(0).next(0).step(0);

		// Step 1 of iteration 1
		if (!iterator.hasNext()) {
			throw new IllegalStateException("hasNext should be true");
		}
		assertCallsOf.stream(1).check(1)/* .next(0) */.step(0);// has next so next() might have been called already
		Button.Action step1 = iterator.next();
		assertCallsOf.stream(1).check(1).next(1).step(0);
		step1.execute();
		assertCallsOf.stream(1).check(1).next(1).step(1);

		// Step 2 of iteration 1
		if (!iterator.hasNext()) {
			throw new IllegalStateException("hasNext should be true");
		}
		assertCallsOf.stream(1).check(2)/* .next(1) */.step(1);// has next so next() might have been called already
		Button.Action step2 = iterator.next();
		assertCallsOf.stream(1).check(2).next(2).step(1);
		step2.execute();
		assertCallsOf.stream(1).check(2).next(2).step(2);

		// Step 1 of iteration 2
		if (!iterator.hasNext()) {
			throw new IllegalStateException("hasNext should be true");
		}
		assertCallsOf.stream(2).check(4)/* .next(2) */.step(2);// has next so next() might have been called already
		Button.Action step3 = iterator.next();
		assertCallsOf.stream(2).check(4).next(3).step(2);
		step3.execute();
		assertCallsOf.stream(2).check(4).next(3).step(3);

		// Step 2 of iteration 2
		if (!iterator.hasNext()) {
			throw new IllegalStateException("hasNext should be true");
		}
		assertCallsOf.stream(2).check(5)/* .next(3) */.step(3);// has next so next() might have been called already
		Button.Action step4 = iterator.next();
		assertCallsOf.stream(2).check(5).next(4).step(3);
		step4.execute();
		assertCallsOf.stream(2).check(5).next(4).step(4);

		// Stop
		if (iterator.hasNext()) {
			throw new IllegalStateException("hasNext should be false");
		}
		assertCallsOf.stream(2).check(6).next(4).step(4);
	}

	@Test
	void testTimesGeneratesStepsLazilyWithIterator() {
		DynamicAction action = new DynamicAction(2);// 1 action = 2 steps
		CompositeAction repeatedAction = action.times(2);// 2 actions = 4 steps
		CallsAsserter assertCallsOf = action.callsAsserter();

		assertCallsOf.stream(0).check(0).next(0).step(0);

		Iterator<Action> iterator = repeatedAction.steps().iterator();
		assertCallsOf.stream(0).check(0).next(0).step(0);

		// Step 1 of iteration 1
		if (!iterator.hasNext()) {
			throw new IllegalStateException("hasNext should be true");
		}
		assertCallsOf.stream(1).check(1)/* .next(0) */.step(0);// has next so next() might have been called already
		Button.Action step1 = iterator.next();
		assertCallsOf.stream(1).check(1).next(1).step(0);
		step1.execute();
		assertCallsOf.stream(1).check(1).next(1).step(1);

		// Step 2 of iteration 1
		if (!iterator.hasNext()) {
			throw new IllegalStateException("hasNext should be true");
		}
		assertCallsOf.stream(1).check(2)/* .next(1) */.step(1);// has next so next() might have been called already
		Button.Action step2 = iterator.next();
		assertCallsOf.stream(1).check(2).next(2).step(1);
		step2.execute();
		assertCallsOf.stream(1).check(2).next(2).step(2);

		// Step 1 of iteration 2
		if (!iterator.hasNext()) {
			throw new IllegalStateException("hasNext should be true");
		}
		assertCallsOf.stream(2).check(4)/* .next(2) */.step(2);// has next so next() might have been called already
		Button.Action step3 = iterator.next();
		assertCallsOf.stream(2).check(4).next(3).step(2);
		step3.execute();
		assertCallsOf.stream(2).check(4).next(3).step(3);

		// Step 2 of iteration 2
		if (!iterator.hasNext()) {
			throw new IllegalStateException("hasNext should be true");
		}
		assertCallsOf.stream(2).check(5)/* .next(3) */.step(3);// has next so next() might have been called already
		Button.Action step4 = iterator.next();
		assertCallsOf.stream(2).check(5).next(4).step(3);
		step4.execute();
		assertCallsOf.stream(2).check(5).next(4).step(4);

		// Stop
		if (iterator.hasNext()) {
			throw new IllegalStateException("hasNext should be false");
		}
		assertCallsOf.stream(2).check(6).next(4).step(4);
	}

	static Stream<Arguments> testThenAddSteps() {
		return Stream.of(//
				arguments(action("A"), action("B"), 2), //
				arguments(compositeAction(1, "A"), compositeAction(1, "B"), 2), //
				arguments(compositeAction(3, "A"), compositeAction(2, "B"), 5), //
				arguments(compositeAction(0, "A"), compositeAction(2, "B"), 2), //
				arguments(compositeAction(3, "A"), compositeAction(0, "B"), 3), //
				arguments(compositeAction(0, "A"), compositeAction(0, "B"), 0) //
		);
	}

	@ParameterizedTest(name = "{0} then {1} has {2} steps")
	@MethodSource
	void testThenAddSteps(Action action1, Action action2, int totalSteps) {
		CompositeAction totalAction = action1.then(action2);
		assertEquals(totalSteps, totalAction.steps().count());
	}

	@Test
	void testThenStepsBrowsingDoNotExecute() {
		int[] counter = { 0 };
		Action action = () -> counter[0]++;
		CompositeAction totalAction = action.then(action);
		totalAction.steps().forEach(act -> {
			// do not execute
		});
		assertEquals(0, counter[0]);
	}

	@Test
	void testThenExecutesActionsInTheRightOrder() {
		List<Action> calls = new LinkedList<>();
		Action action1 = action("A", calls::add);
		Action action2 = action("B", calls::add);
		Action action3 = action("C", calls::add);
		action1.then(action2).then(action3).execute();
		assertEquals(List.of(action1, action2, action3), calls);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3, 4, 5 })
	void testTimesProvidesTheRightNumberOfSteps(int count) {
		Action action = action("action");
		CompositeAction repeatedAction = action.times(count);
		assertEquals(count, repeatedAction.steps().count());
	}

	@Test
	void testTimesStepsBrowsingDoNotExecute() {
		int[] counter = { 0 };
		Action action = () -> counter[0]++;
		CompositeAction repeatedAction = action.times(3);
		repeatedAction.steps().forEach(act -> {
			// do not execute
		});
		assertEquals(0, counter[0]);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, 1, 2, 3, 4, 5 })
	void testTimesExecutesTheRightNumberOfTimes(int count) {
		int[] counter = { 0 };
		Action action = () -> counter[0]++;
		Action repeatedAction = action.times(count);
		repeatedAction.execute();
		assertEquals(count, counter[0]);
	}

	@Test
	void testMultipleTimesProvidesTheRightNumberOfSteps() {
		Action action = action("action");
		CompositeAction repeatedAction = action.times(2).times(3).times(5);
		assertEquals(30, repeatedAction.steps().count());
	}

	@Test
	void testMultipleTimesStepsBrowsingDoNotExecute() {
		int[] counter = { 0 };
		Action action = () -> counter[0]++;
		CompositeAction repeatedAction = action.times(2).times(3);
		repeatedAction.steps().forEach(act -> {
			// do not execute
		});
		assertEquals(0, counter[0]);
	}

	@Test
	void testMultipleTimesExecutesTheRightNumberOfTimes() {
		int[] counter = { 0 };
		Action action = () -> {
			counter[0]++;
		};
		Action repeatedAction = action.times(2).times(3).times(5);
		repeatedAction.execute();
		assertEquals(30, counter[0]);
	}

	@Test
	void testWaitDoesNotStartBeforeFirstStepIsExecuted() throws InterruptedException {
		Duration waitDuration = Duration.of(100, MILLIS);
		CompositeAction action = Action.wait(waitDuration);
		Iterator<Action> iterator = action.steps().iterator();

		Instant preparationEnd = now().plus(waitDuration.multipliedBy(3));// Way after the wait duration
		while (now().isBefore(preparationEnd)) {
			assertTrue(iterator.hasNext());// Still has step to execute
			iterator.next();// Retrieve step but does not execute to not start
		}
	}

	@Test
	void testWaitHasStepsWhileWaiting() throws InterruptedException {
		Duration waitDuration = Duration.of(100, MILLIS);
		CompositeAction action = Action.wait(waitDuration);
		Iterator<Action> iterator = action.steps().iterator();

		iterator.next().execute();// Start waiting
		Duration margin = waitDuration.dividedBy(10);// 10% of waiting time
		Instant waitingEnd = now().plus(waitDuration.minus(margin));// 90% of waiting time
		while (now().isBefore(waitingEnd)) {
			assertTrue(iterator.hasNext());// Still has step to execute
			iterator.next().execute();
		}
	}

	@Test
	void testWaitHaveNoMoreStepsAfterWaiting() throws InterruptedException {
		Duration waitDuration = Duration.of(100, MILLIS);
		CompositeAction action = Action.wait(waitDuration);
		Iterator<Action> iterator = action.steps().iterator();

		iterator.next().execute();// Start waiting
		Duration margin = waitDuration.dividedBy(10);// 10% of waiting time
		Thread.sleep(waitDuration.plus(margin).toMillis());// 110% of waiting time
		assertFalse(iterator.hasNext());
	}

	private static Action action(String name) {
		return action(name, action -> {
			// Do nothing
		});
	}

	private static Action action(String name, Consumer<Action> callNotifier) {
		return new Action() {
			@Override
			public void execute() {
				callNotifier.accept(this);
			}

			@Override
			public String toString() {
				return name;
			}
		};
	}

	private static List<Action> actions(String name, int count) {
		return actions(count, name, action -> {
			// Do nothing
		});
	}

	private static List<Action> actions(int count, String name, Consumer<Action> callNotifier) {
		return IntStream.range(0, count).mapToObj(i -> action(name + " " + i, callNotifier)).collect(toList());
	}

	private static CompositeAction compositeAction(int count, String name) {
		return compositeAction(count + "x" + name, actions(name, count));
	}

	private static CompositeAction compositeAction(String name, List<Action> steps) {
		return new CompositeAction() {
			@Override
			public Stream<Action> steps() {
				return steps.stream();
			}

			@Override
			public String toString() {
				return name;
			}
		};
	}

	static class DynamicAction implements CompositeAction {
		private int streamCalls = 0;
		private int checkCalls = 0;
		private int nextCalls = 0;
		private int stepCalls = 0;
		private final Supplier<Stream<Action>> stream;

		public DynamicAction(int steps) {
			this.stream = () -> {
				streamCalls++;
				Iterator<Action> iterator = new Iterator<Button.Action>() {
					int remainingSteps = steps;

					@Override
					public boolean hasNext() {
						checkCalls++;
						return remainingSteps > 0;
					}

					@Override
					public Action next() {
						nextCalls++;
						if (remainingSteps <= 0) {
							throw new NoSuchElementException();
						}
						remainingSteps--;
						return () -> {
							stepCalls++;
						};
					}
				};
				return StreamSupport.stream(Spliterators.spliterator(iterator, steps, 0), false);
			};
		}

		@Override
		public Stream<Action> steps() {
			return stream.get();
		}

		public CallsAsserter callsAsserter() {
			return new CallsAsserter();
		}

		class CallsAsserter {

			public CallsAsserter stream(int calls) {
				assertCalls("stream", calls, streamCalls);
				return this;
			}

			public CallsAsserter check(int calls) {
				assertCalls("check", calls, checkCalls);
				return this;
			}

			public CallsAsserter next(int calls) {
				assertCalls("next", calls, nextCalls);
				return this;
			}

			public CallsAsserter step(int calls) {
				assertCalls("step", calls, stepCalls);
				return this;
			}

		}

		private void assertCalls(String name, int expected, int actual) {
			assertEquals(expected, actual, name + " calls == " + actual + " != " + expected);
		}
	}
}
