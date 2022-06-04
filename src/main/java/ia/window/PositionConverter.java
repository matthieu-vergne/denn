package ia.window;

import java.util.function.UnaryOperator;

import ia.terrain.Position;

public class PositionConverter {

	private final UnaryOperator<Position> conversion;
	private final UnaryOperator<Position> reverseConversion;

	private PositionConverter(UnaryOperator<Position> conversion, UnaryOperator<Position> reverseConversion) {
		this.conversion = conversion;
		this.reverseConversion = reverseConversion;
	}

	public static PositionConverter createFromBounds(Position.Bounds sourceBounds, Position.Bounds destinationBounds) {
		// TODO Choose best implementation
		return new PositionConverter(//
				Implementation.computeNaively(sourceBounds, destinationBounds), //
				Implementation.computeNaively(destinationBounds, sourceBounds)//
		);
	}

	public static PositionConverter createFromConversion(UnaryOperator<Position> conversion,
			UnaryOperator<Position> reverseConversion) {
		return new PositionConverter(conversion, reverseConversion);
	}

	public static PositionConverter createFromConversion(UnaryOperator<Position> conversion) {
		IllegalArgumentException missingReverseException = new IllegalArgumentException("Missing reversed conversion");
		return new PositionConverter(conversion, position -> {
			throw new IllegalStateException("Cannot convert", missingReverseException);
		});
	}

	public Position convert(Position sourcePosition) {
		return conversion.apply(sourcePosition);
	}

	public Position.Bounds convert(Position.Bounds sourceBounds) {
		return Position.Bounds.between(convert(sourceBounds.min), convert(sourceBounds.max));
	}

	public PositionConverter reverse() {
		return new PositionConverter(reverseConversion, conversion);
	};

	@SuppressWarnings("unused")
	private static final class Implementation {
		private static UnaryOperator<Position> computeNaively(Position.Bounds sourceBounds,
				Position.Bounds destinationBounds) {
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

		private static UnaryOperator<Position> computeOnDoubleFactors(Position.Bounds sourceBounds,
				Position.Bounds destinationBounds) {
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

		private static UnaryOperator<Position> computeOnMinimalIntOperations(Position.Bounds sourceBounds,
				Position.Bounds destinationBounds) {
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
