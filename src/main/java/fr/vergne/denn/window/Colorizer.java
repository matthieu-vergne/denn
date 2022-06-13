package fr.vergne.denn.window;

import java.awt.Color;
import java.util.Optional;

public interface Colorizer {
	Optional<Integer> red();

	Optional<Integer> green();

	Optional<Integer> blue();

	Optional<Integer> alpha();

	public static Colorizer fromColor(Color color) {
		return new Colorizer() {

			@Override
			public Optional<Integer> red() {
				return Optional.of(color.getRed());
			}

			@Override
			public Optional<Integer> green() {
				return Optional.of(color.getGreen());
			}

			@Override
			public Optional<Integer> blue() {
				return Optional.of(color.getBlue());
			}

			@Override
			public Optional<Integer> alpha() {
				return Optional.of(color.getAlpha());
			}
		};
	}

	public static Colorizer onRed(int value) {
		return new Colorizer() {

			@Override
			public Optional<Integer> red() {
				return Optional.of(value);
			}

			@Override
			public Optional<Integer> green() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> blue() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> alpha() {
				return Optional.of(255);
			}
		};
	}

	public static Colorizer onGreen(int value) {
		return new Colorizer() {

			@Override
			public Optional<Integer> red() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> green() {
				return Optional.of(value);
			}

			@Override
			public Optional<Integer> blue() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> alpha() {
				return Optional.of(255);
			}
		};
	}

	public static Colorizer onBlue(int value) {
		return new Colorizer() {

			@Override
			public Optional<Integer> red() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> green() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> blue() {
				return Optional.of(value);
			}

			@Override
			public Optional<Integer> alpha() {
				return Optional.of(255);
			}
		};
	}

	public static Colorizer onAlpha(int value) {
		return new Colorizer() {

			@Override
			public Optional<Integer> red() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> green() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> blue() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> alpha() {
				return Optional.of(value);
			}
		};
	}

	public static Colorizer off() {
		return new Colorizer() {

			@Override
			public Optional<Integer> red() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> green() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> blue() {
				return Optional.empty();
			}

			@Override
			public Optional<Integer> alpha() {
				return Optional.empty();
			}
		};
	}
}
