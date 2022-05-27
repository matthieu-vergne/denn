package ia.terrain;

import static java.lang.Math.*;

import java.awt.Rectangle;
import java.util.List;
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

	public Move to(Position otherPosition) {
		Position that = otherPosition;// Just renaming for readability
		return Move.create(that.x - this.x, that.y - this.y);
	}

	public static class Bounds {
		public final Position min;
		public final Position max;

		private Bounds(Position min, Position max) {
			if (min.x > max.x) {
				throw new IllegalArgumentException("min x (" + min.x + ") cannot be above max x (" + max.x + ")");
			}
			if (min.y > max.y) {
				throw new IllegalArgumentException("min y (" + min.y + ") cannot be above max y (" + max.y + ")");
			}
			this.min = min;
			this.max = max;
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

		public Stream<Position> allPositions() {
			return range(min.x, max.x).flatMap(x -> //
			range(min.y, max.y).flatMap(y -> //
			Stream.of(Position.at(x, y))));
		}

		private Stream<Integer> range(int minY, int maxY) {
			return IntStream.rangeClosed(minY, maxY).mapToObj(i -> i);
		}

		@Override
		public String toString() {
			return List.of(min, max).toString();
		}

		public static Bounds between(Position p1, Position p2) {
			Position minPosition = Position.at(min(p1.x, p2.x), min(p1.y, p2.y));
			Position maxPosition = Position.at(max(p1.x, p2.x), max(p1.y, p2.y));
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

		public static Move create(int dX, int dY) {
			return new Move(dX, dY);
		}
	}
}
