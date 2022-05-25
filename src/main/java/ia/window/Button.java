package ia.window;

import static ia.window.Button.Action.*;
import static java.util.Collections.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class Button {

	public static interface Action {
		void execute();

		default CompositeAction then(Action next) {
			Action previous = this;
			return () -> {
				Stream<Action> previousSteps = previous instanceof CompositeAction comp1 ? comp1.steps()
						: Stream.of(previous);
				Stream<Action> nextSteps = next instanceof CompositeAction comp2 ? comp2.steps() : Stream.of(next);
				return Stream.concat(previousSteps, nextSteps);
			};
		}

		default Action times(int count) {
			if (count < 0) {
				throw new IllegalArgumentException("count must be >0: " + count);
			}
			Action action = this;
			return new CompositeAction() {

				@Override
				public Stream<Action> steps() {
					class Context {
						int iteration = 0;
						Iterator<Action> iterator = emptyIterator();
					}
					Context ctx = new Context();
					Action prepareNext = () -> {
						while (!ctx.iterator.hasNext() && ctx.iteration++ < count) {
							if (action instanceof CompositeAction comp) {
								ctx.iterator = comp.steps().iterator();
							} else {
								ctx.iterator = Stream.of(action).iterator();
							}
						}
					};
					Predicate<Action> predicate = a -> ctx.iterator.hasNext();
					UnaryOperator<Action> iterator = a -> () -> {
						ctx.iterator.next().then(prepareNext).execute();
					};

					prepareNext.execute();
					return Stream.iterate(noop(), predicate, iterator);
				}
			};
		}

		static Action noop() {
			return () -> {
				// noop
			};
		}

		static CompositeAction wait(Duration duration) {
			return () -> {
				Instant[] instantOf = { Instant.MAX };
				int end = 0;
				Action seed = () -> instantOf[end] = Instant.now().plus(duration);
				Predicate<Action> predicate = a -> Instant.now().isBefore(instantOf[end]);
				UnaryOperator<Action> iterator = a -> noop();
				return Stream.iterate(seed, predicate, iterator);
			};
		}
	}

	public static interface CompositeAction extends Action {
		@Override
		default void execute() {
			steps().forEach(Action::execute);
		}

		Stream<Action> steps();
	}

	public final String title;
	public final Action action;

	private Button(String title, Action action) {
		this.title = title;
		this.action = action;
	}

	public static Button create(String title, Action action) {
		return new Button(title, action);
	}

}
