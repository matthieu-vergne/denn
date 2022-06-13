package fr.vergne.denn.utils;

import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Optimized implementation of {@link Stream#flatMap(Function)} found on
 * <a href="https://stackoverflow.com/a/32767282/2031083">StackOverflow</a>.
 * Look at the documentation of {@link #flatMap(Stream, Function)} for the
 * details.
 */
public class FlatMappingSpliterator<T, R> extends Spliterators.AbstractSpliterator<T> implements Consumer<R> {

	private static final boolean USE_ORIGINAL_IMPL = Boolean.getBoolean("stream.flatmap.usestandard");

	/**
	 * Implementation of {@link Stream#flatMap(Function)} to resolve its impartial
	 * laziness with recursive calls. This is an issue that
	 * <a href="https://bugs.openjdk.java.net/browse/JDK-8075939">should have been
	 * fixed</a> but seems not. Updating the JDK may solve it again. It can be
	 * disabled with the property <code>stream.flatmap.usestandard</code>.
	 * 
	 * @see {@link Stream#flatMap(Function)} for the details of the method.
	 */
	public static <T, R> Stream<R> flatMap(Stream<T> in, Function<? super T, ? extends Stream<? extends R>> mapper) {

		if (USE_ORIGINAL_IMPL)
			return in.flatMap(mapper);

		Objects.requireNonNull(in);
		Objects.requireNonNull(mapper);
		return StreamSupport.stream(new FlatMappingSpliterator<>(sp(in), mapper), in.isParallel()).onClose(in::close);
	}

	private final Spliterator<R> src;
	private final Function<? super R, ? extends Stream<? extends T>> f;
	private Stream<? extends T> currStream;
	private Spliterator<T> curr;

	private FlatMappingSpliterator(Spliterator<R> src, Function<? super R, ? extends Stream<? extends T>> f) {
		// actually, the mapping function can change the size to anything,
		// but it seems, with the current stream implementation, we are
		// better off with an estimate being wrong by magnitudes than with
		// reporting unknown size
		super(src.estimateSize() + 100, src.characteristics() & ORDERED);
		this.src = src;
		this.f = f;
	}

	private void closeCurr() {
		try {
			currStream.close();
		} finally {
			currStream = null;
			curr = null;
		}
	}

	@Override
	public void accept(R s) {
		curr = sp(currStream = f.apply(s));
	}

	@Override
	public boolean tryAdvance(Consumer<? super T> action) {
		do {
			if (curr != null) {
				if (curr.tryAdvance(action))
					return true;
				closeCurr();
			}
		} while (src.tryAdvance(this));
		return false;
	}

	@Override
	public void forEachRemaining(Consumer<? super T> action) {
		if (curr != null) {
			curr.forEachRemaining(action);
			closeCurr();
		}
		src.forEachRemaining(s -> {
			try (Stream<? extends T> str = f.apply(s)) {
				if (str != null)
					str.spliterator().forEachRemaining(action);
			}
		});
	}

	@SuppressWarnings("unchecked")
	private static <X> Spliterator<X> sp(Stream<? extends X> str) {
		return str != null ? ((Stream<X>) str).spliterator() : null;
	}

	@Override
	public Spliterator<T> trySplit() {
		Spliterator<R> split = src.trySplit();
		if (split == null) {
			Spliterator<T> prefix = curr;
			while (prefix == null && src.tryAdvance(s -> curr = sp(f.apply(s))))
				prefix = curr;
			curr = null;
			return prefix;
		}
		FlatMappingSpliterator<T, R> prefix = new FlatMappingSpliterator<>(split, f);
		if (curr != null) {
			prefix.curr = curr;
			curr = null;
		}
		return prefix;
	}
}
