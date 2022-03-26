package ia.terrain;

public class Move {

	public final int dX;
	public final int dY;

	private Move(int dX, int dY) {
		this.dX = dX;
		this.dY = dY;
	}

	public static Move create(int dX, int dY) {
		return new Move(dX, dY);
	}

}
