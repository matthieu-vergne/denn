package ia.window;

import static java.awt.Color.*;
import static java.lang.Math.*;
import static java.time.Instant.*;
import static java.util.stream.Collectors.*;
import static javax.swing.SwingUtilities.*;
import static javax.swing.WindowConstants.*;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import ia.agent.Agent;
import ia.agent.Neural;
import ia.agent.Neural.Builder;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Chromosome;
import ia.agent.adn.Program;
import ia.terrain.Position;
import ia.terrain.Terrain;
import ia.terrain.TerrainInteractor;
import ia.window.Window.Button.Action;

public class Window {

	private final JFrame frame;
	private List<Function<Position, Color>> filters = new LinkedList<>();
	private boolean isWindowClosed = false;

	private Window(Terrain terrain, int cellSize, AgentColorizer agentColorizer, List<List<Button>> buttons,
			int compositeActionsPerSecond, NeuralNetwork.Factory networkFactory) {

		JPanel simulationPanel = createSimulationPanel(terrain, cellSize, agentColorizer, buttons,
				compositeActionsPerSecond, networkFactory);

		JTabbedPane tabs = new JTabbedPane();
		tabs.add("Simulation", simulationPanel);

		this.frame = new JFrame("AI");
		frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				isWindowClosed = true;
				frame.removeWindowListener(this);
			}
		});
		Container contentPane = frame.getContentPane();
		contentPane.setLayout(new GridLayout());
		contentPane.add(tabs);
		frame.pack();
		frame.setVisible(true);
	}

	private JPanel createSimulationPanel(Terrain terrain, int cellSize, AgentColorizer agentColorizer,
			List<List<Button>> buttons, int compositeActionsPerSecond, NeuralNetwork.Factory networkFactory) {
		MousePosition canvasMousePosition = new MousePosition();

		JPanel canvas = createCanvas(terrain, cellSize, agentColorizer, canvasMousePosition);

		JPanel[] buttonsPanel = { null };
		Consumer<Boolean> buttonsEnabler = enable -> Stream.of(buttonsPanel[0].getComponents())
				.forEach(component -> component.setEnabled(enable));
		buttonsPanel[0] = createButtonsPanel(withRepaint(buttons, canvas, buttonsEnabler, compositeActionsPerSecond));
		buttonsPanel[0].setBorder(new TitledBorder("Actions"));

		MousePosition.GraphicsListener listener = canvasMousePosition.graphicsListener(terrain, canvas);
		canvas.addMouseListener(listener);
		canvas.addMouseMotionListener(listener);

		JComponent agentPanel = createAgentInfoPanel(canvasMousePosition, terrain, networkFactory);
		JScrollPane agentInfoPanel = new JScrollPane(agentPanel);
		agentInfoPanel.setBorder(new TitledBorder("Agent Info"));

		JPanel terrainPanel = new JPanel();
		terrainPanel.setLayout(new GridBagLayout());

		GridBagConstraints cst = new GridBagConstraints();

		cst.gridx = 0;
		cst.gridy = 0;
		cst.gridwidth = 1;
		cst.gridheight = 2;
		cst.fill = GridBagConstraints.BOTH;
		cst.weightx = 1;
		cst.weighty = 1;
		terrainPanel.add(canvas, cst);

		cst.gridx = 1;
		cst.gridy = 0;
		cst.gridwidth = 1;
		cst.gridheight = 1;
		cst.fill = GridBagConstraints.BOTH;
		cst.weightx = 0;
		cst.weighty = 0;
		terrainPanel.add(buttonsPanel[0], cst);

		cst.gridx = 1;
		cst.gridy = 1;
		cst.gridwidth = 1;
		cst.gridheight = 1;
		cst.fill = GridBagConstraints.BOTH;
		cst.weightx = 0;
		cst.weighty = 0;
		terrainPanel.add(agentInfoPanel, cst);

		return terrainPanel;
	}

	private JPanel createCanvas(Terrain terrain, int cellSize, AgentColorizer agentColorizer,
			MousePosition mousePosition) {
		Position[] positionOf = { null, null };
		int mouse = 0;
		int selection = 1;
		Supplier<Stream<Pointer>> pointersSupplier = () -> Stream.of(//
				new Pointer(positionOf[mouse], PointerRenderer.THIN_SQUARE), //
				new Pointer(positionOf[selection], PointerRenderer.TARGET)//
		).filter(pointer -> pointer.position != null);

		@SuppressWarnings("serial")
		JPanel canvas = new JPanel() {
			@Override
			public void paint(Graphics graphics) {
				DrawContext ctx = DrawContext.create(terrain, this, graphics);
				drawBackground(ctx);
				drawAgents(ctx, agentColorizer);
				drawFilters(ctx, filters);
				drawPointers(ctx, pointersSupplier);
			}
		};
		mousePosition.listenMove(position -> {
			positionOf[mouse] = position;
			canvas.repaint();
		});
		mousePosition.listenExit(() -> {
			positionOf[mouse] = null;
			canvas.repaint();
		});
		mousePosition.listenClick(position -> {
			positionOf[selection] = position;
			canvas.repaint();
		});

		canvas.setPreferredSize(new Dimension(//
				terrain.width() * cellSize - 1, //
				terrain.height() * cellSize - 1//
		));
		return canvas;
	}

	private JPanel createAgentInfoPanel(MousePosition canvasMousePosition, Terrain terrain,
			NeuralNetwork.Factory networkFactory) {
		JLabel moveLabel = new JLabel(" ");
		canvasMousePosition.listenMove(position -> moveLabel.setText(position.toString()));
		canvasMousePosition.listenExit(() -> moveLabel.setText(" "));

		JLabel selectLabel = new JLabel(" ");
		JTextArea programInfoArea = new JTextArea();
		programInfoArea.setEditable(false);
		programInfoArea.setBackground(null);
		JPanel attractorsPanel = new JPanel();
		attractorsPanel.setLayout(new GridBagLayout());
		GridBagConstraints attractorsConstraint = new GridBagConstraints();
		attractorsConstraint.gridx = 0;
		attractorsConstraint.gridy = 1;
		attractorsConstraint.fill = GridBagConstraints.VERTICAL;
		attractorsConstraint.weighty = 1;
		attractorsPanel.add(new JPanel(), attractorsConstraint);
		attractorsConstraint.gridy = 0;
		attractorsConstraint.fill = GridBagConstraints.HORIZONTAL;
		attractorsConstraint.weighty = 0;
		attractorsPanel.add(new JPanel(), attractorsConstraint);
		canvasMousePosition.listenClick(position -> {
			selectLabel.setText(position.toString());
			Optional<Agent> agent = terrain.getAgentAt(position);
			if (agent.isEmpty()) {
				String noAgentNotice = "No agent there";
				programInfoArea.setText(noAgentNotice);
				attractorsPanel.remove(1);
				attractorsPanel.add(new JLabel(noAgentNotice), attractorsConstraint);
			} else {
				Chromosome chromosome = agent.get().chromosome();
				Program program = Program.deserialize(chromosome.bytes());
				ProgramInfoBuilder infoBuilder = new ProgramInfoBuilder();
				program.executeOn(infoBuilder);
				programInfoArea.setText(infoBuilder.build());

				// TODO Compute attractors
				Terrain bench = Terrain.createWithSize(terrain.width(), terrain.height());
				int positionsCount = bench.width() * bench.height();
				int runsPerPosition = 1;
				int iterationsPerRun = (bench.width() + bench.height());

				// TODO Replace by Map<Position, Integer>?
				List<Integer> counts = new ArrayList<Integer>(positionsCount);
				IntStream.range(0, positionsCount).forEach(i -> counts.add(0));
				Function<Position, Integer> indexer = p -> {
					return p.x + p.y * bench.width();
				};
				Function<Integer, Position> desindexer = index -> {
					return Position.at(index % bench.width(), index / bench.width());
				};
				int[] max = { 1 };
				Consumer<Position> countIncrementer = p -> {
					Integer index = indexer.apply(p);
					int count = counts.get(index);
					count++;
					max[0] = max(max[0], count);
					counts.set(index, count);
				};
				Function<Position, Integer> countReader = p -> {
					return counts.get(indexer.apply(p));
				};
				Action iteration = TerrainInteractor.moveAgents().on(bench);
				int iterationCount = 0;
				// TODO Compute in background
				// TODO Continuous repaint while visible
				// TODO Show progress in title
				// TODO Show progress in icon
				// TODO Stop computation when unselected
				counts.set(indexer.apply(Position.at(49, 50)), 5000);// TODO Remove
				for (int i = 0; i < positionsCount; i++) {
					Position startPosition = desindexer.apply(i);
					System.out.println("Start at " + startPosition);
					for (int j = 0; j < runsPerPosition; j++) {
						System.out.print("- Run " + j);
						Agent clone = Agent.createFromProgram(networkFactory, program);
						bench.placeAgent(clone, startPosition);
						for (int k = 0; k < iterationsPerRun; k++) {
							iteration.execute();
							iterationCount++;
						}
						Position lastPosition = bench.removeAgent(clone);
						countIncrementer.accept(lastPosition);
						System.out.println(" until " + lastPosition);
					}
					int percent = 100 * iterationCount / (positionsCount * runsPerPosition * iterationsPerRun);
					System.out.println("Done: " + percent + "%");
				}
				System.out.println("Max count: " + max[0] + " at " + desindexer.apply(counts.indexOf(max[0])));

				Color colorRef = Color.RED;
				Function<Position, Color> attractorFilter = p -> {
					int count = countReader.apply(p);
					int opacity = 255 * count / max[0];
					if (opacity > 0) {
						System.out.println("Draw " + p + " with " + count + " at " + opacity);
					}
					return new Color(colorRef.getRed(), colorRef.getGreen(), colorRef.getBlue(), opacity);
				};
				@SuppressWarnings("serial")
				JPanel canvas = new JPanel() {
					@Override
					public Dimension getPreferredSize() {
						int parentWidth = parentAvailableWidth(this);
						return new Dimension(parentWidth, parentWidth * terrain.height() / terrain.width());
					}

					@Override
					public void paint(Graphics graphics) {
						DrawContext ctx = DrawContext.create(bench, this, graphics);
						drawBackground(ctx);
						drawFilter(ctx, attractorFilter);
					}
				};
				attractorsPanel.remove(1);
				attractorsPanel.add(canvas, attractorsConstraint);
			}
			programInfoArea.setCaretPosition(0);
		});

		JTabbedPane agentInfoTabs = new JTabbedPane();
		agentInfoTabs.addTab("Program", programInfoArea);
		agentInfoTabs.addTab("Attractors", attractorsPanel);

		JPanel agentPanel = new JPanel();
		agentPanel.setLayout(new GridBagLayout());

		GridBagConstraints cst = new GridBagConstraints();
		cst.insets = new Insets(5, 5, 5, 5);
		cst.gridx = GridBagConstraints.RELATIVE;
		cst.gridy = 0;
		cst.anchor = GridBagConstraints.FIRST_LINE_START;

		cst.fill = GridBagConstraints.NONE;
		cst.weightx = 0;
		cst.weighty = 0;
		agentPanel.add(new JLabel("Mouse"), cst);
		agentPanel.add(new JLabel(":"), cst);

		cst.fill = GridBagConstraints.HORIZONTAL;
		cst.weightx = 1;
		cst.weighty = 0;
		agentPanel.add(moveLabel, cst);

		cst.gridy++;

		cst.fill = GridBagConstraints.NONE;
		cst.weightx = 0;
		cst.weighty = 0;
		agentPanel.add(new JLabel("Selected"), cst);
		agentPanel.add(new JLabel(":"), cst);

		cst.fill = GridBagConstraints.HORIZONTAL;
		cst.weightx = 1;
		cst.weighty = 0;
		agentPanel.add(selectLabel, cst);

		cst.gridy++;

		cst.fill = GridBagConstraints.BOTH;
		cst.weightx = 1;
		cst.weighty = 1;
		cst.gridwidth = 3;
		agentPanel.add(agentInfoTabs, cst);
		cst.gridwidth = 0;

		return agentPanel;
	}

	private List<List<Button>> withRepaint(List<List<Button>> buttons, JPanel canvas, Consumer<Boolean> buttonsEnabler,
			int compositeActionsPerSecond) {
		return buttons
				.stream().map(row -> row.stream()
						.map(toButtonWithRepaint(canvas, buttonsEnabler, compositeActionsPerSecond)).collect(toList()))
				.collect(toList());
	}

	private Function<Button, Button> toButtonWithRepaint(JPanel canvas, Consumer<Boolean> buttonsEnabler,
			int compositeActionsPerSecond) {
		int stepPeriodMillis = 1000 / compositeActionsPerSecond;
		return button -> {
			Action action = button.action;
			if (action instanceof Button.CompositeAction comp) {
				return Button.create(button.title, () -> {
					buttonsEnabler.accept(false);

					Iterator<Action> iterator = comp.steps().iterator();
					Runnable[] tasks = { null, null };
					int nextStep = 0;
					int wait = 1;
					Instant[] reservedTime = { null };

					tasks[nextStep] = () -> {
						if (isWindowClosed) {
							return;
						}
						if (iterator.hasNext()) {
							reservedTime[0] = now().plusMillis(stepPeriodMillis);
							iterator.next().execute();
							canvas.repaint();
							invokeLater(tasks[wait]);
						} else {
							buttonsEnabler.accept(true);
						}
					};

					tasks[wait] = () -> {
						if (isWindowClosed) {
							return;
						}
						int next = now().isAfter(reservedTime[0]) ? nextStep : wait;
						invokeLater(tasks[next]);
					};

					invokeLater(tasks[nextStep]);
				});
			} else {
				return Button.create(button.title, () -> {
					action.execute();
					canvas.repaint();
				});
			}
		};
	}

	record DrawContext(Graphics2D graphics2D, Terrain terrain, int componentWidth, int componentHeight,
			double cellWidth, double cellHeight) {

		public static DrawContext create(Terrain terrain, JComponent component, Graphics graphics) {
			Graphics2D graphics2D = (Graphics2D) graphics;
			Rectangle componentBounds = component.getBounds();
			// TODO Scale with panel bounds, but don't draw out of graphics
			int componentWidth = componentBounds.width;
			int componentHeight = componentBounds.height;
			double cellWidth = (double) componentWidth / terrain.width();
			double cellHeight = (double) componentHeight / terrain.height();
			return new DrawContext(//
					graphics2D, terrain, //
					componentWidth, componentHeight, //
					cellWidth, cellHeight//
			);
		}
	}

	private JPanel createButtonsPanel(List<List<Button>> buttons) {
		JPanel buttonsPanel = new JPanel(new GridBagLayout());
		GridBagConstraints cst = new GridBagConstraints();
		cst.gridx = 0;
		cst.gridy = 0;
		cst.insets = new Insets(5, 5, 5, 5);
		cst.fill = GridBagConstraints.HORIZONTAL;
		buttons.forEach(row -> {
			row.forEach(button -> {
				buttonsPanel.add(createButton(button), cst);
				cst.gridx++;
			});
			cst.gridx = 0;
			cst.gridy++;
		});
		return buttonsPanel;
	}

	@SuppressWarnings("serial")
	private JButton createButton(Button button) {
		return new JButton(new AbstractAction(button.title) {

			@Override
			public void actionPerformed(ActionEvent e) {
				button.action.execute();
			}
		});
	}

	public static Window create(Terrain terrain, int cellSize, AgentColorizer agentColorizer,
			int compositeActionsPerSecond, List<List<Button>> buttons, NeuralNetwork.Factory networkFactory) {
		return new Window(terrain, cellSize, agentColorizer, buttons, compositeActionsPerSecond, networkFactory);
	}

	public void setSize(int width, int height) {
		frame.setSize(width, height);
	}

	private void drawBackground(DrawContext ctx) {
		ctx.graphics2D.setColor(WHITE);
		ctx.graphics2D.fillRect(0, 0, ctx.componentWidth, ctx.componentHeight);
	}

	private void drawAgents(DrawContext ctx, AgentColorizer agentColorizer) {
		ctx.terrain.agents().forEach(agent -> {
			Color color = agentColorizer.colorize(agent);
			Position position = ctx.terrain.getAgentPosition(agent);
			int x = (int) (position.x * ctx.cellWidth);
			int y = (int) (position.y * ctx.cellHeight);
			ctx.graphics2D.setColor(color);
			ctx.graphics2D.fillRect(x, y, (int) ctx.cellWidth, (int) ctx.cellHeight);
		});
	}

	public static enum PointerRenderer {
		THIN_SQUARE(drawerFactory -> drawerFactory.thinSquareDrawer(Color.BLACK)), //
		THICK_SQUARE(drawerFactory -> drawerFactory.thickSquareDrawer(Color.BLACK, 10)), //
		TARGET(drawerFactory -> drawerFactory.targetDrawer(Color.BLACK, 5)),//
		;

		private final Function<DrawerFactory, BiConsumer<Integer, Integer>> resolver;

		private PointerRenderer(Function<DrawerFactory, BiConsumer<Integer, Integer>> resolver) {
			this.resolver = resolver;
		}

		BiConsumer<Integer, Integer> createDrawer(DrawerFactory drawerFactory) {
			return resolver.apply(drawerFactory);
		}
	}

	private static record Pointer(Position position, PointerRenderer renderer) {
	}

	private void drawPointers(DrawContext ctx, Supplier<Stream<Pointer>> pointers) {
		DrawerFactory drawerFactory = new DrawerFactory(ctx);
		pointers.get().forEach(pointer -> {
			int x = (int) (pointer.position.x * ctx.cellWidth);
			int y = (int) (pointer.position.y * ctx.cellHeight);
			pointer.renderer.createDrawer(drawerFactory).accept(x, y);
		});
	}

	static class DrawerFactory {
		private final DrawContext ctx;

		public DrawerFactory(DrawContext ctx) {
			this.ctx = ctx;
		}

		public BiConsumer<Integer, Integer> thinSquareDrawer(Color color) {
			return (x, y) -> {
				ctx.graphics2D.setColor(color);
				ctx.graphics2D.drawRect(x, y, (int) ctx.cellWidth, (int) ctx.cellHeight);
			};
		}

		public BiConsumer<Integer, Integer> thickSquareDrawer(Color color, int radius) {
			return (x, y) -> {
				for (int i = 0; i < radius; i++) {
					Color rectColor = new Color(color.getRed(), color.getGreen(), color.getBlue(),
							255 * (radius - i) / radius);
					ctx.graphics2D.setColor(rectColor);
					ctx.graphics2D.drawRect(x - i, y - i, (int) ctx.cellWidth + 2 * i, (int) ctx.cellHeight + 2 * i);
				}
			};
		}

		public BiConsumer<Integer, Integer> targetDrawer(Color color, int extraRadius) {
			double cellSize = max(ctx.cellWidth, ctx.cellHeight);
			int diameter = (int) round(hypot(cellSize, cellSize)) + 2 * extraRadius;
			return (x, y) -> {
				int centerX = x + (int) round(ctx.cellWidth / 2);
				int centerY = y + (int) round(ctx.cellHeight / 2);
				int minX = centerX - diameter / 2;
				int maxX = minX + diameter;
				int minY = centerY - diameter / 2;
				int maxY = minY + diameter;
				ctx.graphics2D.setColor(color);
				ctx.graphics2D.drawOval(minX, minY, diameter, diameter);
				ctx.graphics2D.drawOval(minX, minY, diameter - 1, diameter - 1);
				ctx.graphics2D.drawOval(minX + 1, minY + 1, diameter - 2, diameter - 2);
				ctx.graphics2D.drawOval(minX + 1, minY + 1, diameter - 3, diameter - 3);
				ctx.graphics2D.drawOval(minX + 2, minY + 2, diameter - 4, diameter - 4);
				ctx.graphics2D.drawLine(minX, centerY, maxX, centerY);
				ctx.graphics2D.drawLine(centerX, minY, centerX, maxY);
			};
		}
	}

	private void drawFilters(DrawContext ctx, List<Function<Position, Color>> filters) {
		filters.forEach(filter -> drawFilter(ctx, filter));
	}

	private void drawFilter(DrawContext ctx, Function<Position, Color> filter) {
		for (int px = 0; px < ctx.terrain.width(); px++) {
			for (int py = 0; py < ctx.terrain.height(); py++) {
				Position position = Position.at(px, py);
				Color color = filter.apply(position);
				int x = (int) (position.x * ctx.cellWidth);
				int y = (int) (position.y * ctx.cellHeight);
				ctx.graphics2D.setColor(color);
				ctx.graphics2D.fillRect(x, y, (int) ctx.cellWidth, (int) ctx.cellHeight);
			}
		}
	}

	public static class Button {

		public static interface Action {
			void execute();

			default CompositeAction then(Action next) {
				Action previous = this;
				return new CompositeAction() {

					@Override
					public void execute() {
						previous.execute();
						next.execute();
					}

					@Override
					public Stream<Action> steps() {
						Stream<Action> previousSteps = previous instanceof CompositeAction comp ? comp.steps()
								: Stream.of(previous);
						Stream<Action> nextSteps = next instanceof CompositeAction comp ? comp.steps()
								: Stream.of(next);
						return Stream.concat(previousSteps, nextSteps);
					}
				};
			}

			default Action times(int count) {
				if (count < 0) {
					throw new IllegalArgumentException("count must be >0: " + count);
				} else if (count == 0) {
					return noop();
				} else {
					Action action = this;
					return new CompositeAction() {

						@Override
						public void execute() {
							for (int i = 0; i < count; i++) {
								action.execute();
							}
						}

						Function<Action, Stream<Action>> decomposer;

						@Override
						public Stream<Action> steps() {
							decomposer = act -> {
								if (act instanceof CompositeAction comp) {
									return comp.steps().flatMap(decomposer);
								} else {
									return Stream.of(act);
								}
							};
							return IntStream.range(0, count)//
									.mapToObj(i -> action)//
									.flatMap(decomposer);
						}
					};
				}
			}

			static Action noop() {
				return () -> {
					// noop
				};
			}
		}

		public static interface CompositeAction extends Action {
			Stream<Action> steps();
		}

		private final String title;
		private final Action action;

		private Button(String title, Action action) {
			this.title = title;
			this.action = action;
		}

		public static Button create(String title, Action action) {
			return new Button(title, action);
		}

	}

	public void addFilter(Function<Position, Color> filter) {
		this.filters.add(filter);
	}

	private static void logCurrentMethod() {
		System.out.println(Thread.currentThread().getStackTrace()[2].getMethodName());
	}

	@SuppressWarnings("unused")
	private static String getCurrentMethod() {
		return Thread.currentThread().getStackTrace()[2].getMethodName();
	}

	static class MousePosition {
		private Collection<Consumer<Position>> moveListeners = new LinkedList<>();

		public void listenMove(Consumer<Position> listener) {
			moveListeners.add(listener);
		}

		public void moveTo(Position position) {
			for (Consumer<Position> listener : moveListeners) {
				listener.accept(position);
			}
		}

		private Collection<Consumer<Position>> clickListeners = new LinkedList<>();

		public void listenClick(Consumer<Position> listener) {
			clickListeners.add(listener);
		}

		public void clickAt(Position position) {
			for (Consumer<Position> listener : clickListeners) {
				listener.accept(position);
			}
		}

		private Collection<Runnable> exitListeners = new LinkedList<>();

		public void listenExit(Runnable listener) {
			exitListeners.add(listener);
		}

		public void exit() {
			for (Runnable listener : exitListeners) {
				listener.run();
			}
		}

		static interface GraphicsListener extends MouseListener, MouseMotionListener {
		}

		private GraphicsListener graphicsListener(Terrain terrain, JPanel canvas) {
			Function<Point, Position> positioner = point -> Position.at(//
					point.x * terrain.width() / canvas.getWidth(), //
					point.y * terrain.height() / canvas.getHeight()//
			);
			return new GraphicsListener() {

				@Override
				public void mouseEntered(MouseEvent event) {
					moveTo(positioner.apply(event.getPoint()));
				}

				@Override
				public void mouseExited(MouseEvent event) {
					exit();
				}

				@Override
				public void mouseMoved(MouseEvent event) {
					moveTo(positioner.apply(event.getPoint()));
				}

				@Override
				public void mousePressed(MouseEvent event) {
					System.out.print(event.getPoint());
					logCurrentMethod();
				}

				@Override
				public void mouseDragged(MouseEvent event) {
					System.out.print(event.getPoint());
					logCurrentMethod();
				}

				@Override
				public void mouseReleased(MouseEvent event) {
					System.out.print(event.getPoint());
					logCurrentMethod();
				}

				@Override
				public void mouseClicked(MouseEvent event) {
					clickAt(positioner.apply(event.getPoint()));
				}
			};
		}
	}

	static class ProgramInfoBuilder implements Neural.Builder<String> {
		private final Runnable flusher;
		private final Consumer<String> neuronAdder;
		private final Consumer<Integer> neuronTargeter;
		private final Consumer<Integer> neuronReader;
		private final BiConsumer<String, Integer> outputAssigner;
		private final ByteArrayOutputStream stream = new ByteArrayOutputStream(); //

		public ProgramInfoBuilder() {
			PrintStream out = new PrintStream(stream);

			int[] indexOf = { 0, 0 };
			int reader = 0;
			int lastAdded = 1;

			List<String> neuronNames = new LinkedList<String>();
			List<Integer> readIndexes = new LinkedList<Integer>();

			Function<Integer, String> neuronIdentifier = neuronIndex -> {
				return neuronIndex + ":" + neuronNames.get(neuronIndex);
			};
			this.neuronTargeter = neuronIndex -> {
				indexOf[reader] = neuronIndex;
			};
			this.neuronReader = neuronIndex -> {
				readIndexes.add(neuronIndex);
			};
			this.flusher = () -> {
				if (!readIndexes.isEmpty()) {
					int readerIndex = indexOf[reader];
					String readerName = neuronIdentifier.apply(readerIndex);
					if (indexOf[lastAdded] != readerIndex) {
						out.println(readerName);
					}
					readIndexes.forEach(index -> {
						out.println("→" + neuronIdentifier.apply(index));
					});
					readIndexes.clear();
				}
			};
			this.neuronAdder = name -> {
				flusher.run();
				int index = neuronNames.size();
				neuronNames.add(name);
				indexOf[lastAdded] = index;
				out.println(index + " = " + name);
			};
			this.outputAssigner = (name, index) -> {
				flusher.run();
				out.println(name + " = " + neuronIdentifier.apply(index));
			};

			neuronAdder.accept("X");
			neuronAdder.accept("Y");
		}

		@Override
		public Builder<String> createNeuronWithFixedSignal(double signal) {
			neuronAdder.accept("CONST(" + signal + ")");
			return this;
		}

		@Override
		public Builder<String> createNeuronWithRandomSignal() {
			neuronAdder.accept("RAND");
			return this;
		}

		@Override
		public Builder<String> createNeuronWithSumFunction() {
			neuronAdder.accept("SUM");
			return this;
		}

		@Override
		public Builder<String> createNeuronWithWeightedSumFunction(double weight) {
			neuronAdder.accept("WEIGHT(" + weight + ")");
			return this;
		}

		@Override
		public Builder<String> createNeuronWithMinFunction() {
			neuronAdder.accept("MIN");
			return this;
		}

		@Override
		public Builder<String> createNeuronWithMaxFunction() {
			neuronAdder.accept("MAX");
			return this;
		}

		@Override
		public Builder<String> moveTo(int neuronIndex) {
			neuronTargeter.accept(neuronIndex);
			return this;
		}

		@Override
		public Builder<String> readSignalFrom(int neuronIndex) {
			neuronReader.accept(neuronIndex);
			return this;
		}

		@Override
		public Builder<String> setDXAt(int neuronIndex) {
			outputAssigner.accept("DX", neuronIndex);
			return this;
		}

		@Override
		public Builder<String> setDYAt(int neuronIndex) {
			outputAssigner.accept("DY", neuronIndex);
			return this;
		}

		@Override
		public String build() {
			flusher.run();
			return stream.toString().trim();
		}
	}

	private int parentAvailableWidth(JComponent component) {
		JComponent parent = (JComponent) component.getParent();
		Border border = parent.getBorder();
		Insets insets = border == null ? new Insets(0, 0, 0, 0) : border.getBorderInsets(parent);
		return parent.getBounds().width - insets.left - insets.right;
	}
}
