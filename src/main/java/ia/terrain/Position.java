package ia.terrain;

import static java.lang.Math.*;
import static java.util.Objects.*;

import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Position {
	public static final Position ORIGIN = Position.at(0, 0);

	public final int x;
	public final int y;

	private Position(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public static Position at(int x, int y) {
		return new Position(x, y);
	}

	@Override
	public String toString() {
		return String.format("(%d, %d)", x, y);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		} else if (obj instanceof Position) {
			Position that = (Position) obj;
			return Objects.equals(this.x, that.x) && Objects.equals(this.y, that.y);
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		return hash(x, y);
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
	}

	public record Move(int dX, int dY) {

		public static Move create(int dX, int dY) {
			return new Move(dX, dY);
		}
	}
}
