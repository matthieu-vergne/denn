package fr.vergne.denn.utils;

import static java.lang.Math.*;

import java.util.function.Function;
import java.util.function.UnaryOperator;

public class FunctionUtils {
	/**
	 * Creates a sigmoid {@link Function} (<code>y = sigmoid(x)</code>) going from
	 * <code>y = 0 (x -> -infinite)</code> to <code>y = 1 (x -> +infinite)</code>.
	 * At high slopes, <code>y</code> is around 0 before the center, around 1 after
	 * the center. At low slopes, <code>y</code> remains above 0 before the center,
	 * below 1 after the center.
	 * 
	 * @param center the x value where y = 0.5
	 * @param slope  the steepness around the center
	 * @return a sigmoid {@link Function}
	 */
	public static UnaryOperator<Float> sigmoid(double center, double slope) {
		return value -> (float) (1.0 / (1.0 + exp(slope * (center - value))));
	}
}
