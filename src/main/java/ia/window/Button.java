package ia.window;

import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Button {

	public static interface Action {
		void execute();

		default CompositeAction then(Action next) {
			Action previous = this;
			return new CompositeAction() {

				@Override
				public void execute() {
					previous.execute();
					next.execute();
				}

				@Override
				public Stream<Action> steps() {
					Stream<Action> previousSteps = previous instanceof CompositeAction comp ? comp.steps()
							: Stream.of(previous);
					Stream<Action> nextSteps = next instanceof CompositeAction comp ? comp.steps() : Stream.of(next);
					return Stream.concat(previousSteps, nextSteps);
				}
			};
		}

		default Action times(int count) {
			if (count < 0) {
				throw new IllegalArgumentException("count must be >0: " + count);
			} else if (count == 0) {
				return noop();
			} else {
				Action action = this;
				return new CompositeAction() {

					@Override
					public void execute() {
						for (int i = 0; i < count; i++) {
							action.execute();
						}
					}

					Function<Action, Stream<Action>> decomposer;

					@Override
					public Stream<Action> steps() {
						decomposer = act -> {
							if (act instanceof CompositeAction comp) {
								return comp.steps().flatMap(decomposer);
							} else {
								return Stream.of(act);
							}
						};
						return IntStream.range(0, count)//
								.mapToObj(i -> action)//
								.flatMap(decomposer);
					}
				};
			}
		}

		static Action noop() {
			return () -> {
				// noop
			};
		}
	}

	public static interface CompositeAction extends Action {
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
