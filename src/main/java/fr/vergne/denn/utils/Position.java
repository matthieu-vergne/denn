package fr.vergne.denn.utils;

import static java.lang.Math.*;

import java.awt.Rectangle;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public record Position(int x, int y) {

	public static final Position ORIGIN = Position.at(0, 0);

	public static Position at(int x, int y) {
		return new Position(x, y);
	}

	@Override
	public String toString() {
		return String.format("(%d, %d)", x, y);
	}

	public Position move(Move move) {
		return move(move.dX(), move.dY());
	}

	public Position move(int moveX, int moveY) {
		return Position.at(x + moveX, y + moveY);
	}

	public Position restrict(Position min, Position max) {
		return Position.at(//
				max(min.x, min(x, max.x)), //
				max(min.y, min(y, max.y)) //
		);
	}

	public double distanceTo(Position position) {
		return Math.hypot(position.x - x, position.y - y);
	}

	public Bounds boundsTo(Position otherPosition) {
		return Bounds.between(this, otherPosition);
	}

	public Move to(Position there) {
		return new Move(there.x - this.x, there.y - this.y);
	}

	public static record Bounds(Position min, Position max) {

		public Bounds {
			if (min.x > max.x) {
				throw new IllegalArgumentException("min x (" + min.x + ") cannot be above max x (" + max.x + ")");
			}
			if (min.y > max.y) {
				throw new IllegalArgumentException("min y (" + min.y + ") cannot be above max y (" + max.y + ")");
			}
		}

		public int width() {
			return max.x - min.x;
		}

		public int height() {
			return max.y - min.y;
		}

		public Bounds reduce(int radius) {
			return reduce(radius, radius);
		}

		public Bounds reduce(int xRadius, int yRadius) {
			return reduce(xRadius, xRadius, yRadius, yRadius);
		}

		public Bounds reduce(int xMin, int xMax, int yMin, int yMax) {
			return between(min.move(xMin, yMin), max.move(-xMax, -yMax));
		}

		public Bounds extend(int radius) {
			return extend(radius, radius);
		}

		public Bounds extend(int xRadius, int yRadius) {
			return extend(xRadius, xRadius, yRadius, yRadius);
		}

		public Bounds extend(int xMin, int xMax, int yMin, int yMax) {
			return reduce(-xMin, -xMax, -yMin, -yMax);
		}

		public Bounds merge(Bounds bounds) {
			Position min1 = this.min;
			Position max1 = this.max;
			Position min2 = bounds.min;
			Position max2 = bounds.max;
			Position minAll = Position.at(Math.min(min1.x, min2.x), Math.min(min1.y, min2.y));
			Position maxAll = Position.at(Math.max(max1.x, max2.x), Math.max(max1.y, max2.y));
			return Bounds.between(minAll, maxAll);
		}

		public Stream<Position> allPositions() {
			return range(min.x, max.x).flatMap(x -> //
			range(min.y, max.y).flatMap(y -> //
			Stream.of(Position.at(x, y))));
		}

		private Stream<Integer> range(int minY, int maxY) {
			return IntStream.rangeClosed(minY, maxY).mapToObj(i -> i);
		}

		public Rectangle rectangle() {
			return new Rectangle(min.x(), min.y(), width(), height());
		}

		public Bounds paintable() {
			/**
			 * If we paint a pixel at (x, y), we paint with a width and height of 1. Thus,
			 * painting a bound from (x, y) to (x, y) means painting a square of size 1.
			 * Since a bound from (x, y) to (x, y) has 0 width and height, we need to extend
			 * it. Since the (x, y) is based on the minimum position of the bound, we extend
			 * on the maximum position.
			 */
			return this.extend(0, 1, 0, 1);
		}

		@Override
		public String toString() {
			return List.of(min, max).toString();
		}

		public static Bounds between(Position p1, Position p2) {
			Position minPosition = Position.at(Math.min(p1.x, p2.x), Math.min(p1.y, p2.y));
			Position maxPosition = Position.at(Math.max(p1.x, p2.x), Math.max(p1.y, p2.y));
			return new Bounds(minPosition, maxPosition);
		}

		public static Bounds from(Rectangle rectangle) {
			return between(//
					Position.at(rectangle.x, rectangle.y), //
					Position.at(rectangle.x + rectangle.width - 1, rectangle.y + rectangle.height - 1)//
			);
		}

		public static Bounds around(Position position, int radius) {
			return around(position, radius, radius);
		}

		public static Bounds around(Position position, int xRadius, int yRadius) {
			return between(//
					position.move(-xRadius, -yRadius), //
					position.move(xRadius, yRadius)//
			);
		}
	}

	public record Move(int dX, int dY) {

		public Move absolute() {
			return new Move(abs(dX), abs(dY));
		}
	}

	public static class Conversion {

		private final UnaryOperator<Position> conversion;
		private final UnaryOperator<Position> reverseConversion;

		protected Conversion(UnaryOperator<Position> conversion, UnaryOperator<Position> reverseConversion) {
			this.conversion = conversion;
			this.reverseConversion = reverseConversion;
		}

		public static Conversion createFromBounds(Bounds sourceBounds, Bounds destinationBounds) {
			return createFromBounds(sourceBounds, destinationBounds, Implementation.DEFAULT);
		}

		public static Conversion createFromBounds(Bounds sourceBounds, Bounds destinationBounds,
				Conversion.Implementation implementation) {
			return new Conversion(//
					implementation.apply(sourceBounds, destinationBounds), //
					implementation.apply(destinationBounds, sourceBounds)//
			);
		}

		public static Conversion createFromConversion(UnaryOperator<Position> conversion,
				UnaryOperator<Position> reverseConversion) {
			return new Conversion(conversion, reverseConversion);
		}

		public static Conversion createFromConversion(UnaryOperator<Position> conversion) {
			IllegalArgumentException missingReverseException = new IllegalArgumentException(
					"Missing reversed conversion");
			return new Conversion(conversion, position -> {
				throw new IllegalStateException("Cannot convert", missingReverseException);
			});
		}

		public Position convert(Position sourcePosition) {
			return conversion.apply(sourcePosition);
		}

		public Bounds convert(Bounds sourceBounds) {
			return Bounds.between(convert(sourceBounds.min), convert(sourceBounds.max));
		}

		public Conversion reverse() {
			return new Conversion(reverseConversion, conversion);
		};

		public static enum Implementation {
			NAIVE(Implementation::computeNaively), //
			DOUBLE_FACTORS(Implementation::computeOnDoubleFactors), //
			MINIMAL_OPS(Implementation::computeOnMinimalIntOperations),//
			;

			public static final Implementation DEFAULT = NAIVE;// Separate constant to not be in values()

			private final BiFunction<Bounds, Bounds, UnaryOperator<Position>> implementation;

			private Implementation(BiFunction<Bounds, Bounds, UnaryOperator<Position>> implementation) {
				this.implementation = implementation;
			}

			UnaryOperator<Position> apply(Bounds sourceBounds, Bounds destinationBounds) {
				return implementation.apply(sourceBounds, destinationBounds);
			}

			public static UnaryOperator<Position> computeNaively(Bounds sourceBounds, Bounds destinationBounds) {
				return sourcePosition -> {
					return Position.at(//
							(sourcePosition.x() - sourceBounds.min.x())
									* (destinationBounds.max.x() - destinationBounds.min.x() + 1)
									/ (sourceBounds.max.x() - sourceBounds.min.x() + 1) + destinationBounds.min.x(), //
							(sourcePosition.y() - sourceBounds.min.y())
									* (destinationBounds.max.y() - destinationBounds.min.y() + 1)
									/ (sourceBounds.max.y() - sourceBounds.min.y() + 1) + destinationBounds.min.y()//
					);
				};
			}

			public static UnaryOperator<Position> computeOnDoubleFactors(Bounds sourceBounds,
					Bounds destinationBounds) {
				double xFactor = (double) (destinationBounds.max.x() - destinationBounds.min.x() + 1)
						/ (sourceBounds.max.x() - sourceBounds.min.x() + 1);
				double yFactor = (double) (destinationBounds.max.y() - destinationBounds.min.y() + 1)
						/ (sourceBounds.max.y() - sourceBounds.min.y() + 1);
				return sourcePosition -> {
					return Position.at(//
							(int) ((sourcePosition.x() - sourceBounds.min.x()) * xFactor) + destinationBounds.min.x(), //
							(int) ((sourcePosition.y() - sourceBounds.min.y()) * yFactor) + destinationBounds.min.y()//
					);
				};
			}

			public static UnaryOperator<Position> computeOnMinimalIntOperations(Bounds sourceBounds,
					Bounds destinationBounds) {
				int numX = destinationBounds.max.x() - destinationBounds.min.x() + 1;
				int denX = sourceBounds.max.x() - sourceBounds.min.x() + 1;
				int biasX = destinationBounds.min.x() - sourceBounds.min.x() * numX / denX;
				int numY = destinationBounds.max.y() - destinationBounds.min.y() + 1;
				int denY = sourceBounds.max.y() - sourceBounds.min.y() + 1;
				int biasY = destinationBounds.min.y() - sourceBounds.min.y() * numX / denX;
				return sourcePosition -> {
					return Position.at(//
							sourcePosition.x() * numX / denX + biasX, //
							sourcePosition.y() * numY / denY + biasY//
					);
				};
			}
		}

	}

	public static Collector<Position, ?, List<Position>> toSpreadedPositions() {
		return Collectors.collectingAndThen(Collectors.toList(), new UnaryOperator<List<Position>>() {

			@Override
			public List<Position> apply(List<Position> list) {
				if (list.isEmpty()) {
					return list;
				}

				Position start = list.get(0);
				LinkedList<Position> sortedPositions = new LinkedList<Position>();
				sortedPositions.add(start);
				HashSet<Position> remainingPositions = new HashSet<>(list);
				remainingPositions.remove(start);
				record X(Position position, double distance) {
				}
				while (!remainingPositions.isEmpty()) {
					Position lastPosition = sortedPositions.getLast();
					Position farthestPosition = remainingPositions.stream()//
							.map(position -> {
								double distance = position.distanceTo(lastPosition);
								return new X(position, distance);
							})//
							.sorted(Comparator.comparing(X::distance).reversed())//
							.findFirst().get()//
							.position();
					sortedPositions.add(farthestPosition);
					remainingPositions.remove(farthestPosition);
				}
				return sortedPositions;
			}
		});
	}
}
