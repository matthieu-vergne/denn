package fr.vergne.denn.utils;

import static fr.vergne.denn.utils.Position.Bounds.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.*;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import fr.vergne.denn.utils.Position.Bounds;

class PositionBoundsTest {

	static Stream<Arguments> mergeCases() {
		Position p0 = Position.at(0, 0);
		Position p1 = Position.at(1, 1);
		Position p2 = Position.at(2, 2);
		Position p3 = Position.at(3, 3);
		Bounds bounds01 = between(p0, p1);
		Bounds bounds02 = between(p0, p2);
		Bounds bounds03 = between(p0, p3);
		Bounds bounds12 = between(p1, p2);
		Bounds bounds13 = between(p1, p3);
		Bounds bounds23 = between(p2, p3);
		return Stream.of(//
				// Merge with self
				selfMergeCase(bounds01), //
				selfMergeCase(bounds02), //
				selfMergeCase(bounds03), //
				selfMergeCase(bounds12), //
				selfMergeCase(bounds13), //
				selfMergeCase(bounds23), //

				// Merge with adjacent
				arguments(bounds01, bounds12, bounds02), //
				arguments(bounds12, bounds23, bounds13), //

				// Merge with far away
				arguments(bounds01, bounds23, bounds03) //
		);
	}

	private static Arguments selfMergeCase(Bounds bounds) {
		return arguments(bounds, bounds, bounds);
	}

	@ParameterizedTest(name = "Merge {0} with {1} gives {2}")
	@MethodSource("mergeCases")
	void testMergeReturnsExpectedBounds(Bounds bounds1, Bounds bounds2, Bounds expected) {
		assertEquals(expected, bounds1.merge(bounds2));
	}

	@ParameterizedTest(name = "Merge {0} with {1} = Merge {1} with {0}")
	@MethodSource("mergeCases")
	void testMergeIsCommutative(Bounds bounds1, Bounds bounds2) {
		assertEquals(bounds1.merge(bounds2), bounds2.merge(bounds1));
	}
}
