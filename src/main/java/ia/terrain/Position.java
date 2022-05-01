package ia.terrain;

import static java.lang.Math.*;
import static java.util.Objects.*;

import java.util.Objects;

public class Position {

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
}
