package fr.vergne.denn.window;

import static fr.vergne.denn.utils.CollectorsUtils.*;
import static fr.vergne.denn.utils.Position.*;
import static java.lang.Math.*;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import fr.vergne.denn.agent.Agent;
import fr.vergne.denn.agent.NeuralNetwork;
import fr.vergne.denn.agent.Neural.Builder;
import fr.vergne.denn.agent.adn.Program;
import fr.vergne.denn.terrain.BrowsersFactory;
import fr.vergne.denn.terrain.Terrain;
import fr.vergne.denn.terrain.BrowsersFactory.Step;
import fr.vergne.denn.utils.Position;
import fr.vergne.denn.utils.Position.Move;

// TODO Can we do something with wave function collapse? https://www.procjam.com/tutorials/wfc/
// TODO Can we do something with dimensions reduction? https://en.wikipedia.org/wiki/Dimensionality_reduction
// TODO Can we do something with non-linear dimensions reduction? https://en.wikipedia.org/wiki/Nonlinear_dimensionality_reduction
public interface AgentColorizer {

	public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

	public Color colorize(Agent agent);

	default AgentColorizer cacheByAgent(Map<Agent, Color> cache) {
		return agent -> cache.computeIfAbsent(agent, this::colorize);
	}

	default AgentColorizer cacheByProgram(Map<Program, Color> cache) {
		return agent -> {
			Program program = Program.deserialize(agent.chromosome().bytes());
			return cache.computeIfAbsent(program, k -> colorize(agent));
		};
	}

	// TODO New colorizer based on structure?
	public static AgentColorizer pickingOnChromosome() {
		return agent -> {
			// Adapt bits to avoid long sequences of 0 or 1.
			// It avoids fully transparent/white colors.
			boolean[] invert = { false };
			UnaryOperator<Boolean> bitSwitcher = bit -> {
				invert[0] = !invert[0];
				return bit ^ invert[0];
			};

			int rgba = pickBits(agent.chromosome().bytes(), Integer.SIZE, bitSwitcher).getInt();
			return new Color(rgba, true);
		};
	}

	public static AgentColorizer loopingOnChromosome() {
		return agent -> {
			byte[] chromosomeBytes = agent.chromosome().bytes();
			IntBuffer intBuffer = ByteBuffer.wrap(chromosomeBytes).asIntBuffer();
			int rgba = 0;
			while (intBuffer.remaining() > 0) {
				rgba = rgba ^ intBuffer.get();
			}
			return new Color(rgba, true);
		};
	}

	public static AgentColorizer basedOnStructure() {
		return agent -> {
			byte[] chromosomeBytes = agent.chromosome().bytes();
			Program program = Program.deserialize(chromosomeBytes);
			interface ColorFunction {
				Color compute(List<Color> inputs);
			}
			List<ColorFunction> colorFunctions = new LinkedList<>();
			Color xColor = new Color(255, 0, 0);
			Color yColor = new Color(0, 255, 0);
			colorFunctions.add(inputs -> xColor);
			colorFunctions.add(inputs -> yColor);
			Map<Integer, List<Integer>> indexesMap = new LinkedHashMap<>();
			Builder<Color> colorBuilder = new Builder<Color>() {

				private int currentIndex = 0;
				private int dXIndex = 0;
				private int dYIndex = 1;

				@Override
				public Builder<Color> createNeuronWithFixedSignal(double signal) {
					// TODO Use hue
					colorFunctions.add(inputs -> new Color(Color.HSBtoRGB((float) signal, 1.0f, 1.0f)));
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithRandomSignal() {
					colorFunctions.add(inputs -> Color.MAGENTA);
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithWeightedSumFunction(double weight) {
					colorFunctions.add(inputs -> inputs.stream()//
							.reduce(colorAccumulator((value1, value2) -> normalizeChannel(value1 + value2)))//
							.map(colorAdapter(value -> normalizeChannel((int) (value * weight))))//
							.orElse(TRANSPARENT));
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithSumFunction() {
					return createNeuronWithWeightedSumFunction(1.0);
				}

				@Override
				public Builder<Color> createNeuronWithMinFunction() {
					colorFunctions.add(inputs -> {
						return inputs.stream().reduce(colorAccumulator(Math::min)).orElse(TRANSPARENT);
					});
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithMaxFunction() {
					colorFunctions
							.add(inputs -> inputs.stream().reduce(colorAccumulator(Math::max)).orElse(TRANSPARENT));
					return this;
				}

				@Override
				public Builder<Color> moveTo(int neuronIndex) {
					currentIndex = neuronIndex;
					return this;
				}

				@Override
				public Builder<Color> readSignalFrom(int neuronIndex) {
					indexesMap.computeIfAbsent(currentIndex, k -> new LinkedList<>()).add(neuronIndex);
					return this;
				}

				@Override
				public Builder<Color> setDXAt(int neuronIndex) {
					dXIndex = neuronIndex;
					return this;
				}

				@Override
				public Builder<Color> setDYAt(int neuronIndex) {
					dYIndex = neuronIndex;
					return this;
				}

				@Override
				public Color build() {
					List<Color> colors = new ArrayList<>(colorFunctions.size());
					for (int neuronIndex = 0; neuronIndex < colorFunctions.size(); neuronIndex++) {
						colors.add(TRANSPARENT);
					}
					int size = colors.size();
					UnaryOperator<Integer> indexNormalizer = index -> normalize(index, size);

					for (int neuronIndex = 0; neuronIndex < colorFunctions.size(); neuronIndex++) {
						List<Integer> inputIndexes = indexesMap.computeIfAbsent(neuronIndex, k -> emptyList());
						List<Color> inputColors = inputIndexes == null //
								? emptyList()//
								: inputIndexes.stream().map(indexNormalizer).map(colors::get).collect(toList());
						Color neuronColor = colorFunctions.get(neuronIndex).compute(inputColors);
						colors.set(neuronIndex, neuronColor);
					}

					Integer dXIndex = indexNormalizer.apply(this.dXIndex);
					Integer dYIndex = indexNormalizer.apply(this.dYIndex);
					Color dXColor = colors.get(dXIndex);
					Color dYColor = colors.get(dYIndex);
					Color color = List.of(dXColor, dYColor).stream().reduce(colorAccumulator((a, b) -> (a + b) / 2))
							.orElse(TRANSPARENT);
					return color;
				}

			};
			program.executeOn(colorBuilder);
			return colorBuilder.build();
		};
	}

	public static AgentColorizer pickingOnWeights() {
		return agent -> {
			Builder<Color> colorBuilder = new Builder<Color>() {

				private final List<Double> weights = new LinkedList<>();

				@Override
				public Builder<Color> createNeuronWithFixedSignal(double signal) {
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithRandomSignal() {
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithWeightedSumFunction(double weight) {
					weights.add(weight);
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithSumFunction() {
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithMinFunction() {
					return this;
				}

				@Override
				public Builder<Color> createNeuronWithMaxFunction() {
					return this;
				}

				@Override
				public Builder<Color> moveTo(int neuronIndex) {
					return this;
				}

				@Override
				public Builder<Color> readSignalFrom(int neuronIndex) {
					return this;
				}

				@Override
				public Builder<Color> setDXAt(int neuronIndex) {
					return this;
				}

				@Override
				public Builder<Color> setDYAt(int neuronIndex) {
					return this;
				}

				@Override
				public Color build() {
					if (weights.isEmpty()) {
						return Color.BLACK;
					}

					ByteBuffer buffer = weights.stream().collect(//
							() -> ByteBuffer.allocate(Double.BYTES * weights.size()), //
							ByteBuffer::putDouble, //
							(buffer1, buffer2) -> {
								buffer1.put(buffer2.flip());
							});
					int rgba = pickBits(buffer.array(), Integer.SIZE, UnaryOperator.identity()).getInt();
					return new Color(rgba, false);
				}
			};
			byte[] chromosomeBytes = agent.chromosome().bytes();
			Program program = Program.deserialize(chromosomeBytes);
			program.executeOn(colorBuilder);
			Color color = colorBuilder.build();
			return color;
		};
	}

	public static AgentColorizer pickingOnMoves(Terrain terrain, NeuralNetwork.Factory networkFactory) {
		boolean[] bitsMemory = new boolean[4];
		Runnable bitsMemoryReset = () -> {
			Arrays.fill(bitsMemory, false);
		};
		Function<BiFunction<Position.Move, Boolean, Boolean>, Function<Position.Move, Boolean>> previouser = new Function<BiFunction<Position.Move, Boolean, Boolean>, Function<Position.Move, Boolean>>() {
			private int index = 0;

			@Override
			public Function<Position.Move, Boolean> apply(BiFunction<Position.Move, Boolean, Boolean> function) {
				int previousIndex = index++;
				return move -> {
					Boolean bit = function.apply(move, bitsMemory[previousIndex]);
					bitsMemory[previousIndex] = bit;
					return bit;
				};
			}
		};
		Function<Position.Move, Boolean> redBit = previouser.apply((move, previous) -> {
			int dX = move.dX();
			return dX > 0 ? true //
					: dX < 0 ? false //
							: previous;
		});
		Function<Position.Move, Boolean> greenBit = previouser.apply((move, previous) -> {
			int dY = move.dY();
			return dY > 0 ? true //
					: dY < 0 ? false //
							: previous;
		});
		Function<Position.Move, Boolean> blueBit = previouser.apply((move, previous) -> {
			int dX = move.dX();
			int dY = move.dY();
			return dX > dY ? true //
					: dX < dY ? false //
							: previous;
		});
		Function<Position.Move, Boolean> alphaBit = previouser.apply((move, previous) -> {
			int dX = move.dX();
			int dY = move.dY();
			return dX == dY;
		});

		int redMinIndex = 0;
		int greenMinIndex = Byte.SIZE;
		int blueMinIndex = Byte.SIZE * 2;
		int alphaMinIndex = Byte.SIZE * 3;

		BitSet bits = new BitSet(4 * Byte.SIZE);

		AgentColorizer agentColorizer = agent -> {
			byte[] bytes = agent.chromosome().bytes();
			Program program = Program.deserialize(bytes);
			NeuralNetwork network = networkFactory.execute(program);

			int stepX = (terrain.width() - 1) / (Byte.SIZE - 1);
			int stepY = (terrain.height() - 1) / (Byte.SIZE - 1);

			bits.clear();
			bitsMemoryReset.run();
			int x = 0;
			int y = 0;
			for (int i = 0; i < Byte.SIZE; i++) {
				network.setInputs(Position.at(x, y));
				network.fire();
				Position.Move move = network.output();

				bits.set(redMinIndex + i, redBit.apply(move));
				bits.set(greenMinIndex + i, greenBit.apply(move));
				bits.set(blueMinIndex + i, blueBit.apply(move));
				bits.set(alphaMinIndex + i, alphaBit.apply(move));

				x += stepX;
				y += stepY;
			}

			ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
			buffer.mark();
			buffer.put(bits.toByteArray());
			buffer.reset();
			int rgba = buffer.getInt();

			return new Color(rgba);
		};
		return agentColorizer;
	}

	public static AgentColorizer pickingOnAttractors(Terrain terrain, NeuralNetwork.Factory networkFactory) {
		// FIXME Try sorting the positions smartly (taking the next most distant?)
		Collector<Position, ?, List<Position>> collector1 = toSpreadedPositions();
		Collector<Position, ?, List<Position>> collector2 = toShuffledList();
		BrowsersFactory browserFactory = new BrowsersFactory(networkFactory, terrain, collector2);

		Move maxDistances = terrain.minPosition().to(terrain.maxPosition());
		BiFunction<Position, Position, Double> proximityComputer = (p1, p2) -> {
			Move distances = p1.to(p2).absolute();
			double xProximity = 1 - (double) distances.dX() / maxDistances.dX();
			double yProximity = 1 - (double) distances.dY() / maxDistances.dY();
			return min(xProximity, yProximity);
		};

		record RG(int red, int green) {
		}
		record RGB(RG rg, int blue) {
			public Color toColor() {
				return new Color(rg.red, rg.green, blue);
			}
		}
		var averagedRed = weightedAverageChannel(Color::getRed);
		var averagedGreen = weightedAverageChannel(Color::getGreen);
		var averagedBlue = weightedAverageChannel(Color::getBlue);
		var averagedRGB = teeing(teeing(averagedRed, averagedGreen, RG::new), averagedBlue, RGB::new);
		var averagedColor = collectingAndThen(averagedRGB, RGB::toColor);
		Map<Position, Color> colorReferences = Map.of(//
				terrain.topLeft(), Color.ORANGE, //
				terrain.topRight(), Color.RED, //
				terrain.bottomRight(), Color.BLUE, //
				terrain.bottomLeft(), Color.CYAN//
		);
		Function<Position, Color> positionColorer = position -> {
			return colorReferences.entrySet().stream()//
					.map(entry -> {
						Color refColor = entry.getValue();
						Position refPosition = entry.getKey();
						Double proximity = proximityComputer.apply(position, refPosition);
						return Map.entry(refColor, proximity);
					})//
					.collect(averagedColor);
		};

		// TODO Profile reproduction to identify bottlenecks
		// FIXME Too high computation time
		int terrainSurface = terrain.width() * terrain.height();
		// FIXME Perform poorly for attractors on random/bands
		int maxStarts = min(terrainSurface, 20);
		int maxPathsPerStart = min(terrainSurface, 5);
		int maxStepsPerPath = terrain.width() + terrain.height();
		AgentColorizer agentColorizer = agent -> {
			byte[] bytes = agent.chromosome().bytes();
			Program program = Program.deserialize(bytes);
			AttractorsStats stats = browserFactory.browsers(program).limit(maxStarts)//
					.flatMap(browser -> {
						Position startPosition = browser.startPosition();
						return browser.paths().limit(maxPathsPerStart)//
								.map(path -> path.steps().limit(maxStepsPerPath)//
										.takeWhile(Step.movesWithin(10))// Optimization
										.reduce(toLast()).get()//
										.positionAfter())//
								.map(attractor -> new StartAttractorPair(startPosition, attractor));
					})//
					.parallel()// Optimization
					.collect(toStats());

			// FIXME Separate randomness over X and Y for bands
			float randomnessPerStart = (float) (stats.attractorsCountPerStartAverage() - 1) / (maxPathsPerStart - 1);
			float stabilityPerStart = 1 - randomnessPerStart;

			float randomnessOverStarts = (float) (stats.averageStartAttractorsCount() - 1) / (maxStarts - 1);
			float stabilityOverStarts = 1 - randomnessOverStarts;

			// TODO Test
			// TODO WHITE (s=0, b=1) when many attractors per start (random)
			// TODO BLACK (s=0, b=0) when few attractors per start but many overall (static)
			// FIXME Don't color average attractor, create average color of attractors
			Color baseColor = positionColorer.apply(stats.averageAttractor());
			float[] hsb = extractHSB(baseColor);
			int hue = 0;
			int saturation = 1;
			int brightness = 2;
			return Color.getHSBColor(//
					hsb[hue], //
					hsb[saturation] * (stabilityOverStarts * stabilityPerStart), //
					hsb[brightness] * strengthen(max(stabilityOverStarts, randomnessPerStart))
							+ (1 - hsb[brightness]) * strengthen(randomnessPerStart)//
			);
		};
		return agentColorizer;
	}

	

	private static float strengthen(float value) {
		// FIXME required?
		return value;// FunctionUtils.sigmoid(0.5, 10.0).apply(value);
	}

	private static float[] extractHSB(Color color) {
		return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
	}

	private static Collector<Entry<Color, Double>, Object, Integer> weightedAverageChannel(
			Function<Color, Integer> channelExtractor) {
		return collectingAndThen(weightedAverageOf(channelExtractor), Double::intValue);
	}

	static <T, U extends Number> Collector<Map.Entry<T, Double>, ?, Double> weightedAverageOf(
			Function<T, U> valueExtractor) {
		class Aggregator {
			double value = 0;
			double weight = 0;
		}
		return new Collector<Map.Entry<T, Double>, Aggregator, Double>() {

			@Override
			public Supplier<Aggregator> supplier() {
				return Aggregator::new;
			}

			@Override
			public BiConsumer<Aggregator, Entry<T, Double>> accumulator() {
				return (ctx, entry) -> {
					T source = entry.getKey();
					Double weight = entry.getValue();
					ctx.value += weight * valueExtractor.apply(source).doubleValue();
					ctx.weight += weight;
				};
			}

			@Override
			public BinaryOperator<Aggregator> combiner() {
				return (ctx1, ctx2) -> {
					ctx1.value += ctx2.value;
					ctx1.weight += ctx2.weight;
					return ctx1;
				};
			}

			@Override
			public Function<Aggregator, Double> finisher() {
				return ctx -> ctx.value / ctx.weight;
			}

			@Override
			public Set<Characteristics> characteristics() {
				return Set.of();
			}
		};
	}

	record AttractorsStats(Map<Position, Integer> attractorsCountPerStart, Position averageAttractor,
			int attractorsCountPerStartAverage, long averageStartAttractorsCount) {
		public AttractorsStats(AttractorsStats.Split1 split1, AttractorsStats.Split2 split2) {
			this(split1.attractorsCountPerStart, split1.averageAttractor, split2.attractorsCountPerStartAverage,
					split2.averageStartAttractorsCount);
		}

		record Split1(Map<Position, Integer> attractorsCountPerStart, Position averageAttractor) {
		}

		record Split2(int attractorsCountPerStartAverage, long averageStartAttractorsCount) {
		}
	}

	record StartAttractorPair(Position start, Position attractor) {
	}

	private static Collector<StartAttractorPair, ?, AttractorsStats> toStats() {
		var attractorsCountPerStart = toAttractorsCountPerStart();
		var averageAttractor = toAverageAttractor();
		var attractorsCountPerStartAverage = toAttractorsCountPerStartAverage();
		var averageStartAttractorsCount = toAverageStartAttractorsCount();

		var split1 = teeing(attractorsCountPerStart, averageAttractor, AttractorsStats.Split1::new);
		var split2 = teeing(attractorsCountPerStartAverage, averageStartAttractorsCount, AttractorsStats.Split2::new);
		return teeing(split1, split2, AttractorsStats::new);
	}

	static Collector<StartAttractorPair, ?, Position> toAverageAttractor() {
		return mapping(StartAttractorPair::attractor, toAveragePosition());
	}

	static Collector<StartAttractorPair, ?, Map<Position, Integer>> toAttractorsCountPerStart() {
		return new Collector<StartAttractorPair, Map<Position, Set<Position>>, Map<Position, Integer>>() {

			@Override
			public Supplier<Map<Position, Set<Position>>> supplier() {
				return HashMap::new;
			}

			@Override
			public BiConsumer<Map<Position, Set<Position>>, StartAttractorPair> accumulator() {
				return (map, pair) -> map.computeIfAbsent(pair.start, k -> new HashSet<>()).add(pair.attractor);
			}

			@Override
			public BinaryOperator<Map<Position, Set<Position>>> combiner() {
				return (map1, map2) -> {
					map2.forEach((k, v) -> map1.computeIfAbsent(k, k2 -> new HashSet<>()).addAll(v));
					return map1;
				};
			}

			@Override
			public Function<Map<Position, Set<Position>>, Map<Position, Integer>> finisher() {
				return map -> map.entrySet().stream().collect(toMap(//
						entry -> entry.getKey(), //
						entry -> entry.getValue().size()//
				));
			}

			@Override
			public Set<Characteristics> characteristics() {
				return Set.of();
			}
		};
	}

	static Collector<StartAttractorPair, ?, Integer> toAttractorsCountPerStartAverage() {
		return new Collector<StartAttractorPair, Map<Position, Set<Position>>, Integer>() {

			@Override
			public Supplier<Map<Position, Set<Position>>> supplier() {
				return HashMap::new;
			}

			@Override
			public BiConsumer<Map<Position, Set<Position>>, StartAttractorPair> accumulator() {
				return (map, pair) -> map.computeIfAbsent(pair.start, k -> new HashSet<>()).add(pair.attractor);
			}

			@Override
			public BinaryOperator<Map<Position, Set<Position>>> combiner() {
				return (map1, map2) -> {
					map2.forEach((k, v) -> map1.computeIfAbsent(k, k2 -> new HashSet<>()).addAll(v));
					return map1;
				};
			}

			@Override
			public Function<Map<Position, Set<Position>>, Integer> finisher() {
				return map -> (int) round(
						map.entrySet().stream().mapToInt(entry -> entry.getValue().size()).average().getAsDouble());
			}

			@Override
			public Set<Characteristics> characteristics() {
				return Set.of();
			}
		};
	}

	private static Collector<StartAttractorPair, ?, Long> toAverageStartAttractorsCount() {
		return new Collector<StartAttractorPair, Map<Position, List<Position>>, Long>() {

			@Override
			public Supplier<Map<Position, List<Position>>> supplier() {
				return HashMap::new;
			}

			@Override
			public BiConsumer<Map<Position, List<Position>>, StartAttractorPair> accumulator() {
				return (map, pair) -> map.computeIfAbsent(pair.start, k -> new LinkedList<>()).add(pair.attractor);
			}

			@Override
			public BinaryOperator<Map<Position, List<Position>>> combiner() {
				return (map1, map2) -> {
					map2.forEach((k, v) -> map1.computeIfAbsent(k, k2 -> new LinkedList<>()).addAll(v));
					return map1;
				};
			}

			@Override
			public Function<Map<Position, List<Position>>, Long> finisher() {
				return map -> map.entrySet().stream()//
						.map(entry -> entry.getValue().stream().collect(toAveragePosition()))//
						.distinct().count();
			}

			@Override
			public Set<Characteristics> characteristics() {
				return Set.of();
			}
		};
	}

	private static Collector<Position, ?, Position> toAveragePosition() {
		return teeing(toIntAverageOf(Position::x), toIntAverageOf(Position::y), Position::at);
	}

	private static <T> Collector<T, Object, Integer> toIntAverageOf(ToIntFunction<? super T> mapper) {
		return collectingAndThen(averagingInt(mapper), Double::intValue);
	}

	private static <T> BinaryOperator<T> toLast() {
		return (a, b) -> b;
	}

	private static BinaryOperator<Color> colorAccumulator(BinaryOperator<Integer> channelAccumulator) {
		return (c1, c2) -> new Color(//
				channelAccumulator.apply(c1.getRed(), c2.getRed()), //
				channelAccumulator.apply(c1.getGreen(), c2.getGreen()), //
				channelAccumulator.apply(c1.getBlue(), c2.getBlue())//
		);
	}

	private static UnaryOperator<Color> colorAdapter(UnaryOperator<Integer> channelAdapter) {
		return c -> new Color(//
				channelAdapter.apply(c.getRed()), //
				channelAdapter.apply(c.getGreen()), //
				channelAdapter.apply(c.getBlue())//
		);
	}

	private static int normalize(int value, int size) {
		return (((int) value % size) + size) % size;
	}

	private static int normalizeChannel(int value) {
		return normalize(value, 256);
	}

	private static ByteBuffer pickBits(byte[] inputBytes, int outputBitsCount, UnaryOperator<Boolean> bitAdapter) {
		if (inputBytes.length == 0) {
			throw new IllegalArgumentException("No bytes to pick from");
		}
		int inputBitsCount = inputBytes.length * Byte.SIZE;
		BitSet inputBits = BitSet.valueOf(inputBytes);
		BitSet outputBits = new BitSet(outputBitsCount);
		double indexConversionFactor = (double) (inputBitsCount - 1) / (outputBitsCount - 1);
		for (int outputBitIndex = 0; outputBitIndex < outputBitsCount; outputBitIndex++) {
			int inputBitIndex = (int) (indexConversionFactor * outputBitIndex);
			boolean bit = inputBits.get(inputBitIndex);
			outputBits.set(outputBitIndex, bitAdapter.apply(bit));
		}

		byte[] outputBytes = Arrays.copyOf(outputBits.toByteArray(), outputBitsCount);
		return ByteBuffer.wrap(outputBytes);
	}

}
