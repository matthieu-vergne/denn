package ia.window;

import static java.time.Instant.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Spliterators;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import ia.utils.StreamUtils;

public class Button {

	public static interface Action {
		void execute();

		default CompositeAction then(Action next) {
			Action previous = this;
			return () -> {
				Stream<Stream<Action>> streamOfStreams = Stream.of(previous, next)//
						.map(act -> act instanceof CompositeAction comp //
								? comp.steps()//
								: Stream.of(act));
				return StreamUtils.flattenLazily(streamOfStreams);
			};
		}

		default CompositeAction times(int count) {
			if (count < 0) {
				throw new IllegalArgumentException("count must be positive: " + count);
			}
			Action action = this;
			Supplier<Stream<Action>> stepsStream = action instanceof CompositeAction comp //
					? () -> comp.steps()
					: () -> Stream.of(action);
			return new CompositeAction() {

				@Override
				public Stream<Action> steps() {
					return StreamUtils.flattenLazily(Stream.generate(stepsStream).limit(count));
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
			return impl2(duration);
		}

		static CompositeAction impl1(Duration duration) {
			return () -> {
				Instant[] end = { Instant.MAX };
				Action seed = () -> end[0] = now().plus(duration);
				Predicate<Action> predicate = a -> now().isBefore(end[0]);
				UnaryOperator<Action> iterator = a -> Action.noop();
				return Stream.iterate(seed, predicate, iterator);
			};
		}

		static CompositeAction impl2(Duration duration) {
			return () -> {
				Iterator<Action> iterator = new Iterator<Action>() {
					Instant end;

					@Override
					public boolean hasNext() {
						return end == null || now().isBefore(end);
					}

					@Override
					public Action next() {
						return end == null ? () -> end = now().plus(duration) : noop();
					}
				};
				return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false);
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
