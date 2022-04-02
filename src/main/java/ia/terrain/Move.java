package ia.terrain;

public record Move(int dX, int dY)  {

	public static Move create(int dX, int dY) {
		return new Move(dX, dY);
	}

}
