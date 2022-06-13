package fr.vergne.denn.utils;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

import java.util.List;
import java.util.stream.Collector;

public class CollectorsUtils {
	public static <T> Collector<T, ?, List<T>> toShuffledList() {
		return collectingAndThen(toList(), list -> {
			shuffle(list);
			return list;
		});
	}
}
