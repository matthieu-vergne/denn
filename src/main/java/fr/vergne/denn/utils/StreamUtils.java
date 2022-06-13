package fr.vergne.denn.utils;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class StreamUtils {
	public static <T> Stream<T> cycleOver(List<T> elements) {
		return lazyFlatMap(Stream.iterate(elements, list -> true, list -> list), List<T>::stream);
	}

	/**
	 * The idiomatic way to flatten a stream of streams is with
	 * {@link Stream#flatMap(Function)}. However, it is not lazy when the stream is
	 * consumed on demand through an iterator or spliterator. Such consumption is
	 * thus incompatible with flattening streams that are dynamically produced. For
	 * examples, infinite streams run indefinitely, and time-dependent streams are
	 * produced in an "instant". To cope with dynamic streams, we provide this
	 * method that implements a lazy consumption, since it won't be fixed:
	 * 
	 * https://bugs.openjdk.java.net/browse/JDK-8267359
	 */
	public static <T, R> Stream<R> lazyFlatMap(Stream<T> stream,
			Function<? super T, ? extends Stream<? extends R>> mapper) {
		return FlatMappingSpliterator.flatMap(stream, mapper);
	}
}
