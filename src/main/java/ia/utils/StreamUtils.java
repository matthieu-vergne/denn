package ia.utils;

import static java.util.Collections.emptyIterator;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.StreamSupport.stream;

import java.util.Iterator;
import java.util.stream.Stream;

public class StreamUtils {
	public static <T> Stream<T> flattenLazily(Stream<Stream<T>> streamOfStreams) {
		/**
		 * The idiomatic way to flatten a stream of streams is with flatMap(). However,
		 * flatMap() is not lazy when the stream is consumed on demand through an
		 * iterator or spliterator. Such consumption is thus incompatible with
		 * flattening streams that are dynamically produced. For examples, infinite
		 * streams run indefinitely, and time-dependent streams are produced in an
		 * "instant". To cope with dynamic streams, we need to maintain the laziness of
		 * the consumption in another way since it won't be fixed:
		 * 
		 * https://bugs.openjdk.java.net/browse/JDK-8267359
		 */
		Iterator<Stream<T>> streamsIterator = streamOfStreams.iterator();
		Iterator<T> actionsIterator = new Iterator<T>() {
			Iterator<T> streamIterator = emptyIterator();

			@Override
			public boolean hasNext() {
				while (!streamIterator.hasNext()) {
					if (streamsIterator.hasNext()) {
						streamIterator = streamsIterator.next().iterator();
					} else {
						return false;
					}
				}
				return true;
			}

			@Override
			public T next() {
				return streamIterator.next();
			}
		};
		return stream(spliteratorUnknownSize(actionsIterator, 0), false);
	}
}
