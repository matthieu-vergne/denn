package ia.window;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.*;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import ia.terrain.Position;

class PositionConverterTest {

	private static Position p(int x, int y) {
		return Position.at(x, y);
	}

	private static Position.Bounds bound(int minX, int minY, int maxX, int maxY) {
		return Position.Bounds.between(p(minX, minY), p(maxX, maxY));
	}

	public static Stream<Arguments> inputs() {
		return Stream.of(//
				// identity
				arguments(p(0, 0), p(0, 0), bound(0, 0, 2, 2), bound(0, 0, 2, 2)), //
				arguments(p(0, 1), p(0, 1), bound(0, 0, 2, 2), bound(0, 0, 2, 2)), //
				arguments(p(0, 2), p(0, 2), bound(0, 0, 2, 2), bound(0, 0, 2, 2)), //
				arguments(p(1, 2), p(1, 2), bound(0, 0, 2, 2), bound(0, 0, 2, 2)), //
				arguments(p(2, 2), p(2, 2), bound(0, 0, 2, 2), bound(0, 0, 2, 2)), //
				// scale down
				arguments(p(0, 0), p(0, 0), bound(0, 0, 5, 5), bound(0, 0, 2, 2)), //
				arguments(p(0, 1), p(0, 0), bound(0, 0, 5, 5), bound(0, 0, 2, 2)), //
				arguments(p(0, 2), p(0, 1), bound(0, 0, 5, 5), bound(0, 0, 2, 2)), //
				arguments(p(0, 3), p(0, 1), bound(0, 0, 5, 5), bound(0, 0, 2, 2)), //
				arguments(p(0, 4), p(0, 2), bound(0, 0, 5, 5), bound(0, 0, 2, 2)), //
				arguments(p(0, 5), p(0, 2), bound(0, 0, 5, 5), bound(0, 0, 2, 2)), //
				arguments(p(1, 5), p(0, 2), bound(0, 0, 5, 5), bound(0, 0, 2, 2)), //
				arguments(p(2, 4), p(1, 2), bound(0, 0, 5, 5), bound(0, 0, 2, 2)), //
				arguments(p(3, 4), p(1, 2), bound(0, 0, 5, 5), bound(0, 0, 2, 2)), //
				arguments(p(4, 4), p(2, 2), bound(0, 0, 5, 5), bound(0, 0, 2, 2)), //
				arguments(p(5, 5), p(2, 2), bound(0, 0, 5, 5), bound(0, 0, 2, 2)), //
				// scale up
				arguments(p(0, 0), p(0, 0), bound(0, 0, 2, 2), bound(0, 0, 5, 5)), //
				arguments(p(0, 1), p(0, 2), bound(0, 0, 2, 2), bound(0, 0, 5, 5)), //
				arguments(p(0, 2), p(0, 4), bound(0, 0, 2, 2), bound(0, 0, 5, 5)), //
				arguments(p(1, 2), p(2, 4), bound(0, 0, 2, 2), bound(0, 0, 5, 5)), //
				arguments(p(2, 2), p(4, 4), bound(0, 0, 2, 2), bound(0, 0, 5, 5)), //
				// translation
				arguments(p(0, 0), p(1, 2), bound(0, 0, 2, 2), bound(1, 2, 3, 4)), //
				arguments(p(0, 1), p(1, 3), bound(0, 0, 2, 2), bound(1, 2, 3, 4)), //
				arguments(p(0, 2), p(1, 4), bound(0, 0, 2, 2), bound(1, 2, 3, 4)), //
				arguments(p(1, 2), p(2, 4), bound(0, 0, 2, 2), bound(1, 2, 3, 4)), //
				arguments(p(2, 2), p(3, 4), bound(0, 0, 2, 2), bound(1, 2, 3, 4)), //
				// translation + scale down
				arguments(p(0, 0), p(1, 2), bound(0, 0, 5, 5), bound(1, 2, 3, 4)), //
				arguments(p(0, 1), p(1, 2), bound(0, 0, 5, 5), bound(1, 2, 3, 4)), //
				arguments(p(0, 2), p(1, 3), bound(0, 0, 5, 5), bound(1, 2, 3, 4)), //
				arguments(p(0, 3), p(1, 3), bound(0, 0, 5, 5), bound(1, 2, 3, 4)), //
				arguments(p(0, 4), p(1, 4), bound(0, 0, 5, 5), bound(1, 2, 3, 4)), //
				arguments(p(0, 5), p(1, 4), bound(0, 0, 5, 5), bound(1, 2, 3, 4)), //
				arguments(p(1, 5), p(1, 4), bound(0, 0, 5, 5), bound(1, 2, 3, 4)), //
				arguments(p(2, 4), p(2, 4), bound(0, 0, 5, 5), bound(1, 2, 3, 4)), //
				arguments(p(3, 4), p(2, 4), bound(0, 0, 5, 5), bound(1, 2, 3, 4)), //
				arguments(p(4, 4), p(3, 4), bound(0, 0, 5, 5), bound(1, 2, 3, 4)), //
				arguments(p(5, 5), p(3, 4), bound(0, 0, 5, 5), bound(1, 2, 3, 4)), //
				// translation + scale up
				arguments(p(0, 0), p(1, 2), bound(0, 0, 2, 2), bound(1, 2, 6, 7)), //
				arguments(p(0, 1), p(1, 4), bound(0, 0, 2, 2), bound(1, 2, 6, 7)), //
				arguments(p(0, 2), p(1, 6), bound(0, 0, 2, 2), bound(1, 2, 6, 7)), //
				arguments(p(1, 2), p(3, 6), bound(0, 0, 2, 2), bound(1, 2, 6, 7)), //
				arguments(p(2, 2), p(5, 6), bound(0, 0, 2, 2), bound(1, 2, 6, 7)) //
		);
	}

	@ParameterizedTest(name = "{0} gives {1} from {2} to {3}")
	@MethodSource("inputs")
	void testConvert(Position srcPosition, Position destPosition, Position.Bounds srcBounds,
			Position.Bounds destBounds) {
		assertEquals(destPosition, new PositionConverter(srcBounds, destBounds).convert(srcPosition));
	}

	@ParameterizedTest(name = "{0} gives {1} from {2} to {3}")
	@MethodSource("inputs")
	void testReverse(Position srcPosition, Position destPosition, Position.Bounds srcBounds,
			Position.Bounds destBounds) {
		// Reverse bounds in initial converter to get usual bounds after reversing
		assertEquals(destPosition, new PositionConverter(destBounds, srcBounds).reverse().convert(srcPosition));
	}

}
