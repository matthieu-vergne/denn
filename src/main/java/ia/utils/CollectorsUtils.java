package ia.utils;

import static java.util.stream.Collectors.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;

public class CollectorsUtils {
	public static <T> Collector<T, T, List<T>> toShuffledList() {
		return collectingAndThen(toList(), list -> {
			Collections.shuffle(list);
			return list;
		});
	}
}
