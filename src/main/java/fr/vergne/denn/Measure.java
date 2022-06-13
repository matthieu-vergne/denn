package fr.vergne.denn;

import static java.util.stream.Collectors.*;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;

/**
 * Generic framework to help measuring any accessible property of any object. It
 * is done in 3 steps:
 * <ol>
 * <li>Specify how to measure the property on the type of object targeted
 * <li>Specify what to do with the collected measures
 * <li>Apply it on the object(s) to measure
 * </ol>
 * 
 * Here is an illustrative example to measure the size of a {@link Collection}:
 * 
 * <pre>
 * <code>
 * Measure.of(Collection<?>::size)
 *     .feeding(size -> System.out.println("size = " + size))
 *     .from(Set.of("a", "b", "c", "d"));
 * </code>
 * </pre>
 * 
 * For the 3 steps we find 3 method calls:
 * <ol>
 * <li><code>Measure.of(...)</code> specifies how to measure the size of a
 * {@link Collection} (by calling {@link Collection#size()}) and returns a
 * {@link Feeder} for the next step
 * <li><code>Feader.feeding(...)</code> specifies that the collected size should
 * be displayed on the console and returns a {@link Applier} for the next step
 * <li><code>Retriever.from(...)</code> applies it on the given {@link Set} of 4
 * elements and produces the result <code>"size = 4"</code>.
 * </ol>
 * 
 * Each step can be reused, thus each {@link Feeder} can produce a
 * {@link Applier} for each collector to feed, and each {@link Applier} can
 * measure several objects.
 * 
 * <h1>Measuring Modes</h1>
 * <h2>Static Property</h2>
 * 
 * A static property is a property that is present "statically", meaning that as
 * long as the object measured is there, the property can be measured at any
 * time. For instance, as long as you have a {@link Collection}, you can measure
 * its size by calling its {@link Collection#size()}:
 * 
 * <pre>
 * <code>
 * Measure.of(Collection<?>::size)
 *     .feeding(size -> System.out.println("size = " + size))
 *     .from(Set.of("a", "b", "c", "d"));
 * // Result: size = 4
 * </code>
 * </pre>
 * 
 * If the property requires more computation, you may specify the type of object
 * measured and the type of measure obtained to properly type the
 * {@link Function} of the first step:
 * 
 * <pre>
 * <code>
 * Measure.&lt;List&lt;String>, Integer>of(list -> list.indexOf("c"))
 *     .feeding(index -> System.out.println("index of c = " + index))
 *     .from(List.of("a", "b", "c", "d"));
 * // Result: index of c = 2
 * </code>
 * </pre>
 * 
 * Extract it into a dedicated method to avoid this effort:
 * 
 * <pre>
 * <code>
 * Measure.of(listIndexOf("c"))
 *     .feeding(index -> System.out.println("index of c = " + index))
 *     .from(List.of("a", "b", "c", "d"));
 * // Result: index of c = 2
 * </code>
 * </pre>
 * 
 * <h2>Dynamic Property</h2>
 * 
 * A dynamic property is a property that cannot be measured without interacting
 * with the object measured. For instance, to measure the {@link Duration} of a
 * {@link Runnable}, you need to call its {@link Runnable#run()} method. In this
 * case, rather than telling how to measure the property from the object itself
 * (the {@link Runnable}), you tell how to decorate the object to use the
 * measure {@link Collector} properly.
 * <p>
 * Here is the complete, commented code:
 * 
 * <pre>
 * <code>
 * // Tell how to decorate a Runnable to compute the Duration of its run()
 * Feeder<Runnable, Duration> feeder = Measure.<Runnable, Duration>of(
 *     (runnable, durationCollector) -> {
 *         return () -> {
 *             Instant start = Instant.now();
 *             runnable.run();
 *             durationCollector.collect(Duration.between(start, Instant.now()));
 *         };
 *     })//
 * 
 * // Tell that the duration must be displayed on the console
 * Retriever<Duration> retriever = feeder.feeding(duration -> System.out.println("duration = " + duration))//
 * 
 * // Apply it on a Runnable that lasts 1 second:
 * Runnable measuredRunnable = retriever.from(() -> {
 *         try {
 *             Thread.sleep(1000);
 *         } catch (InterruptedException cause) {
 *             throw new RuntimeException("Interrupted", cause);
 *         }
 *     });
 * 
 * // Call the decorated Runnable to produce the Duration
 * measuredRunnable.run();
 * 
 * // Result: duration = PT1.00017648S
 * </code>
 * </pre>
 * 
 * Here is the reduced code with some methods and variables to better show the
 * global structure:
 * 
 * <pre>
 * <code>
 * Runnable measuredRunnable =
 *   Measure.of(runnableDuration())
 *     .feeding(theConsole())
 *     .from(oneSecondTask());
 * 
 * measuredRunnable.run();
 * // Result: duration = PT1.00017648S
 * </code>
 * </pre>
 * 
 * <h2>Multiple Objects</h2>
 * 
 * Each {@link Applier} can be used on several objects. It will use the same
 * specifications every time:
 * 
 * <pre>
 * <code>
 * Retriever<Integer> retriever =
 *   Measure.of(Collection<?>::size)
 *     .feeding(size -> System.out.println("size = " + size));
 * 
 * retriever.from(Set.of("a", "b", "c", "d"));
 * // Result: size = 4
 * retriever.from(Set.of("a", "b", "c"));
 * // Result: size = 3
 * retriever.from(Set.of("a", "b""));
 * // Result: size = 2
 * retriever.from(Set.of("a"));
 * // Result: size = 1
 * </code>
 * </pre>
 * 
 * It can also be aggregated by calling <code>fromAll(...)</code>:
 * 
 * <pre>
 * <code>
 * Measure.of(Collection<?>::size)
 *     .feeding(size -> System.out.println("size = " + size))
 *     .fromAll(List.of(
 *         Set.of("a", "b", "c", "d"),
 *         Set.of("a", "b", "c"),
 *         Set.of("a", "b"),
 *         Set.of("a")
 *     ));
 * // Result:
 * // size = 4
 * // size = 3
 * // size = 2
 * // size = 1
 * </code>
 * </pre>
 * 
 * For dynamic properties, it returns a {@link Collection} of decorated objects
 * to be called:
 * 
 * <pre>
 * <code>
 * Collection&lt;Runnable> measuredRunnables =
 *   Measure.of(runnableDuration())
 *     .feeding(theConsole())
 *     .fromAll(List.of(task1s, task2s, task3s));
 * 
 * measuredRunnables.forEach(Runnable::run);
 * // Result:
 * // duration = PT1.000331236S
 * // duration = PT2.000377497S
 * // duration = PT3.000415269S
 * </code>
 * </pre>
 */
public interface Measure {

	/**
	 * Specify how to measure a static property from the measured object(s).
	 * 
	 * @param <T>      the type of object measured
	 * @param <M>      the type of measure produced
	 * @param measurer the measuring {@link Function} to apply on the object(s)
	 * @return a new {@link Feeder}
	 */
	public static <T, M> Feeder<T, M> of(Function<T, M> measurer) {
		return measureCollector -> measured -> {
			measureCollector.collect(measurer.apply(measured));
			return measured;
		};
	}

	/**
	 * Specify how to measure a dynamic property by decorating the measured
	 * object(s).
	 * 
	 * @param <T>       the type of object measured
	 * @param <M>       the type of measure produced
	 * @param decorator the {@link Decorator} used on the measured object to collect
	 *                  its measures
	 * @return a new {@link Feeder}
	 */
	public static <T, M> Feeder<T, M> of(Decorator<T, M> decorator) {
		return measureCollector -> measured -> decorator.decorate(measured, measureCollector);
	}

	/**
	 * The decorator definition for {@link Measure#of(Decorator)}.
	 *
	 * @param <T> the type of object measured
	 * @param <M> the type of measure produced
	 */
	public interface Decorator<T, M> {
		/**
		 * Decorate the measured object to collect its measure when interacting with it.
		 * 
		 * @param measured         the measured object
		 * @param measureCollector the measure {@link Collector}
		 * @return a decorated object
		 */
		T decorate(T measured, Collector<M> measureCollector);
	}

	/**
	 * A {@link Feeder} knows how to measure the required property and help to
	 * collect its measures into a {@link Collector}.
	 *
	 * @param <T> the type of object measured
	 * @param <M> the type of measure produced
	 */
	public interface Feeder<T, M> {
		/**
		 * Tell how to collect the measures.
		 * 
		 * @param measureCollector the measure {@link Collector}
		 * @return the {@link Applier} to apply on a measured object
		 */
		Applier<T> feeding(Collector<M> measureCollector);
	}

	/**
	 * The collector definition for {@link Feeder#feeding(Collector)}.
	 *
	 * @param <M> the type of measure produced
	 */
	public interface Collector<M> {
		void collect(M measure);

		/**
		 * Implementation of a {@link Duration} {@link Collector}.
		 * 
		 * @return a new {@link Collector}
		 */
		public static AggregatingCollector<Duration> ofDuration() {
			return new AggregatingCollector<Duration>(Duration.ZERO, Duration::plus);
		}
	}

	/**
	 * A {@link Applier} applies the measuring process to an object via
	 * {@link #from(Object)}. It can apply it on several objects at once with
	 * {@link #fromAll(Collection)}.
	 * 
	 * @param <T> the type of object measured
	 */
	public interface Applier<T> {
		/**
		 * Apply the measuring process to the given object. For a static property, the
		 * measure is collected upon calling this method. For a dynamic property, a
		 * decorated object is returned. By interacting with it as defined by the
		 * previously set {@link Decorator}, the measure is collected.
		 * 
		 * @param measured the object measured
		 * @return the decorated measured object
		 */
		public T from(T measured);

		/**
		 * Apply the measuring process to all the given objects. For a static property,
		 * the measures are collected upon calling this method. For a dynamic property,
		 * a decorated object is returned for each object. By interacting with them as
		 * defined by the previously set {@link Decorator}, the measures are collected.
		 * 
		 * @param measured the objects measured
		 * @return the decorated measured objects
		 */
		default Collection<T> fromAll(Collection<T> measured) {
			return measured.stream().map(this::from).collect(toList());
		}
	}

	/**
	 * {@link Collector} implementation that stores the collected measures by
	 * aggregating them into a single value.
	 *
	 * @param <M> the type of measure produced
	 */
	public static class AggregatingCollector<M> implements Collector<M> {
		private M value;
		private final BinaryOperator<M> aggregator;

		/**
		 * Creates an {@link AggregatingCollector} with the given value. Any collected
		 * measure will be aggregated to it with the provided operator.
		 * 
		 * @param initialValue the initial value of this {@link AggregatingCollector}
		 * @param aggregator   the operator to aggregate the collected values
		 */
		public AggregatingCollector(M initialValue, BinaryOperator<M> aggregator) {
			this.value = initialValue;
			this.aggregator = aggregator;
		}

		@Override
		public void collect(M measure) {
			this.value = aggregator.apply(this.value, measure);
		}

		/**
		 * Returns the current value stored.
		 * 
		 * @return the current value stored
		 */
		public M value() {
			return value;
		}
	}
}
