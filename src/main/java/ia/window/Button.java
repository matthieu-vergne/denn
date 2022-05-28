package ia.window;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Button {

	public static interface Action {
		void execute();

		default CompositeAction then(Action next) {
			Action previous = this;
			return () -> {
				Stream<? extends Action> previousSteps = previous instanceof CompositeAction comp1 ? comp1.steps()
						: Stream.of(previous);
				Stream<? extends Action> nextSteps = next instanceof CompositeAction comp2 ? comp2.steps()
						: Stream.of(next);
				return Stream.concat(previousSteps, nextSteps);
			};
		}

		default CompositeAction times(int count) {
			if (count < 0) {
				throw new IllegalArgumentException("count must be positive: " + count);
			}
			Action action = this;
			Supplier<Stream<Action>> stepsStream = action instanceof CompositeAction comp //
					? () -> {
						System.out.println("times wrap composite steps");
						return comp.steps();
					}
					: () -> {
						System.out.println("times wrap mono step");
						return Stream.of(action);
					};
			return new CompositeAction() {

				@Override
				public Stream<Action> steps() {
					System.out.println("times prepare steps");
					Stream<Stream<Action>> streamOfStreams = IntStream.range(0, count).mapToObj(i -> {
						System.out.println("times retrieve steps for " + i);
						return stepsStream.get();
					});
					/**
					 * The idiomatic way to deal with a stream of streams is to flatten it with
					 * flatMap(). However, flatMap() is not lazy when the stream is consumed on
					 * demand through an iterator or spliterator. Such consumption is thus
					 * incompatible with flattening streams that are dynamically produced. For
					 * examples, infinite streams run indefinitely, and time-dependent streams are
					 * produced in an "instant". To cope with dynamic streams, we need to maintain
					 * the laziness of the consumption in another way since it won't be fixed:
					 * 
					 * https://bugs.openjdk.java.net/browse/JDK-8267359
					 */
					// FIXME Make it lazy
//					Iterator<Stream<Action>> iterator = streamOfStream.iterator();
//					Action seed;
//					Predicate<Action> hasNext;
//					UnaryOperator<Action> next;
//					Stream.<Action>iterate(seed, hasNext, next);

					return streamOfStreams.flatMap(s -> {
						System.out.println("times flatten steps");
						return s;
					});
				}

				@Override
				public String toString() {
					return count + "x" + action;
				}
			};
		}

		static Action noop() {
			return new Action() {
				@Override
				public void execute() {
					// Do nothing
				}

				@Override
				public String toString() {
					return "noop";
				}
			};
		}

		static CompositeAction wait(Duration duration) {
			return () -> {
				System.out.println("wait prepare steps");
				Instant[] instantOf = { Instant.MAX };
				int end = 0;
				Action seed = () -> {
					Instant startInstant = Instant.now();
					Instant endInstant = startInstant.plus(duration);
					System.out.println("wait seed: " + startInstant + " > " + endInstant);
					instantOf[end] = endInstant;
				};
				Predicate<Action> predicate = a -> {
					Instant now = Instant.now();
					Instant endInstant = instantOf[end];
					boolean before = now.isBefore(endInstant);
					System.out.println(
							"wait checks " + endInstant + " at " + now + ": " + (before ? "continue" : "stop"));
					return before;
				};
				UnaryOperator<Action> iterator = a -> {
					System.out.println("wait");
					return noop();
				};
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
