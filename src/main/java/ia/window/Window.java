package ia.window;

import static ia.window.TerrainPanel.Drawer.*;
import static java.time.Instant.*;
import static java.util.stream.Collectors.*;
import static javax.swing.SwingUtilities.*;
import static javax.swing.WindowConstants.*;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.RepaintManager;
import javax.swing.border.TitledBorder;

import ia.agent.Agent;
import ia.agent.NeuralNetwork;
import ia.agent.adn.Chromosome;
import ia.agent.adn.Program;
import ia.terrain.Position;
import ia.terrain.Position.Bounds;
import ia.terrain.Terrain;
import ia.window.TerrainPanel.DrawContext;
import ia.window.TerrainPanel.Drawer;
import ia.window.TerrainPanel.DrawerFactory;
import ia.window.TerrainPanel.Pointer;
import ia.window.TerrainPanel.PointerRenderer;

public class Window {

	private final JFrame frame;
	private List<Function<Position, Color>> filters = new LinkedList<>();
	private boolean isWindowClosed = false;
	private final List<Runnable> closeListeners = new LinkedList<>();

	private Window(Terrain terrain, int cellSize, AgentColorizer agentColorizer, List<List<Button>> buttons,
			int compositeActionsPerSecond, NeuralNetwork.Factory networkFactory) {
		WeakHashMap<TerrainPanel, List<Rectangle>> dirtyRegions = new WeakHashMap<>();
		AtomicReference<Runnable> availablePainter = new AtomicReference<Runnable>();
		Runnable painter = new Runnable() {

			@Override
			public void run() {
				if (isWindowClosed) {
					return;
				}
				availablePainter.set(this);
				dirtyRegions.entrySet().forEach(entry -> {
					TerrainPanel terrainPanel = entry.getKey();
					List<Rectangle> rectangles = dirtyRegions.get(terrainPanel);
					rectangles.forEach(rectangle -> {
						terrainPanel.paintImmediately(rectangle);
					});
					rectangles.clear();
				});
//				invokeLater(this);
			}
		};
		// invokeLater(painter);
		availablePainter.set(painter);
		RepaintManager.setCurrentManager(new RepaintManager() {

			@Override
			public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
				if (c instanceof TerrainPanel s) {
					Rectangle rectangle = new Rectangle(x, y, w, h);
					List<Rectangle> rectangles = dirtyRegions.computeIfAbsent(s, k -> new LinkedList<>());
					if (rectangles.isEmpty()) {
						rectangles.add(rectangle);
					} else {
						rectangles.add(rectangles.remove(0).union(rectangle));
					}
					Runnable updater = availablePainter.getAndSet(null);
					if (updater != null) {
						invokeLater(updater);
					}
				} else {
					super.addDirtyRegion(c, x, y, w, h);
				}
			}

			@Override
			public Rectangle getDirtyRegion(JComponent c) {
				if (c instanceof TerrainPanel s) {
					throw new RuntimeException("Not implemented");
				} else {
					return super.getDirtyRegion(c);
				}
			}

			@Override
			public void markCompletelyDirty(JComponent c) {
				if (c instanceof TerrainPanel s) {
					throw new RuntimeException("Not implemented");
				} else {
					super.markCompletelyDirty(c);
				}
			}

			@Override
			public void markCompletelyClean(JComponent c) {
				if (c instanceof TerrainPanel s) {
					throw new RuntimeException("Not implemented");
				} else {
					super.markCompletelyClean(c);
				}
			}

		});

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
				for (Runnable listener : closeListeners) {
					listener.run();
				}
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
		MouseOnTerrain mouseOnTerrain = new MouseOnTerrain();

		TerrainPanel terrainPanel = createTerrainPanel(terrain, cellSize, agentColorizer, mouseOnTerrain);

		JPanel[] buttonsPanel = { null };
		Consumer<Boolean> buttonsEnabler = enable -> Stream.of(buttonsPanel[0].getComponents())
				.forEach(component -> component.setEnabled(enable));
		buttonsPanel[0] = createButtonsPanel(
				withRepaint(buttons, terrainPanel, buttonsEnabler, compositeActionsPerSecond));
		buttonsPanel[0].setBorder(new TitledBorder("Actions"));

		MouseOnTerrain.TerrainPanelListener listener = mouseOnTerrain.terrainPanelListener(terrain, terrainPanel);
		terrainPanel.addMouseListener(listener);
		terrainPanel.addMouseMotionListener(listener);

		JComponent agentPanel = createAgentInfoPanel(mouseOnTerrain, terrain, networkFactory);
		JScrollPane agentInfoPanel = new JScrollPane(agentPanel);
		agentInfoPanel.setBorder(new TitledBorder("Agent Info"));

		JPanel simulationPanel = new JPanel();
		simulationPanel.setLayout(new GridBagLayout());

		GridBagConstraints cst = new GridBagConstraints();

		cst.gridx = 0;
		cst.gridy = 0;
		cst.gridwidth = 1;
		cst.gridheight = 2;
		cst.fill = GridBagConstraints.BOTH;
		cst.weightx = 1;
		cst.weighty = 1;
		simulationPanel.add(terrainPanel, cst);

		cst.gridx = 1;
		cst.gridy = 0;
		cst.gridwidth = 1;
		cst.gridheight = 1;
		cst.fill = GridBagConstraints.BOTH;
		cst.weightx = 0;
		cst.weighty = 0;
		simulationPanel.add(buttonsPanel[0], cst);

		cst.gridx = 1;
		cst.gridy = 1;
		cst.gridwidth = 1;
		cst.gridheight = 1;
		cst.fill = GridBagConstraints.BOTH;
		cst.weightx = 0;
		cst.weighty = 0;
		simulationPanel.add(agentInfoPanel, cst);

		return simulationPanel;
	}

	private TerrainPanel createTerrainPanel(Terrain terrain, int cellSize, AgentColorizer agentColorizer,
			MouseOnTerrain mouseOnTerrain) {
		Position[] positionOf = { null, null };
		PointerRenderer[] rendererOf = { null, null };
		int mouse = 0;
		int selection = 1;
		rendererOf[mouse] = PointerRenderer.THICK_SQUARE;
		rendererOf[selection] = PointerRenderer.TARGET;
		Supplier<Stream<Pointer>> pointersSupplier = () -> Stream.of(//
				new Pointer(positionOf[mouse], rendererOf[mouse]), //
				new Pointer(positionOf[selection], rendererOf[selection])//
		).filter(pointer -> pointer.position() != null);

		TerrainPanel terrainPanel = new TerrainPanel(terrain, () -> {
			Drawer backgroundDrawer = filler(Color.WHITE);
			Drawer filterDrawer = filters.stream()//
					.map(filter -> Drawer.forEachPosition(terrain.allPositions(), filter.andThen(Drawer::filler)))//
					.collect(toCompositeDrawer());
			Function<Agent, Color> colorize = agentColorizer::colorize;
			Drawer agentDrawer = Drawer.forEachAgent(terrain.agents(), colorize.andThen(Drawer::filler));
			// TODO Simplify pointers Drawer
			Function<Pointer, Drawer> drawerFactory = pointer -> new Drawer() {

				@Override
				public void draw(DrawContext ctx) {
					int x = (int) (pointer.position().x * ctx.cellWidth());
					int y = (int) (pointer.position().y * ctx.cellHeight());
					pointer.renderer().createDrawer(new DrawerFactory(ctx)).accept(x, y);
				}
			};
			Drawer pointersDrawer = pointersSupplier.get().map(drawerFactory).collect(toCompositeDrawer());
			return backgroundDrawer.then(filterDrawer).then(agentDrawer).then(pointersDrawer);
		});
		mouseOnTerrain.listenMove(position -> {
			Position previousPosition = positionOf[mouse];
			positionOf[mouse] = position;

			// FIXME compute resize-friendly radius
			int pixelRadius = rendererOf[mouse].radius();
			Position radius = terrainPanel.pixelToTerrain().convert(Position.at(pixelRadius, pixelRadius));
			if (previousPosition != null) {
				terrainPanel.repaint(Position.Bounds.around(previousPosition, radius.x, radius.y));
			}
			terrainPanel.repaint(Position.Bounds.around(position, radius.x, radius.y));
		});
		mouseOnTerrain.listenExit(() -> {
			Position previousPosition = positionOf[mouse];
			positionOf[mouse] = null;

			// FIXME compute resize-friendly radius
			int radius = rendererOf[mouse].radius();
			terrainPanel.repaint(Position.Bounds.around(previousPosition, radius));
		});
		mouseOnTerrain.listenClick(position -> {
			Position previousPosition = positionOf[selection];
			positionOf[selection] = position;

			// FIXME compute resize-friendly radius
			int radius = rendererOf[selection].radius();
			if (previousPosition != null) {
				terrainPanel.repaint(Position.Bounds.around(previousPosition, radius));
			}
			terrainPanel.repaint(Position.Bounds.around(position, radius));
		});

		terrainPanel.setPreferredSize(new Dimension(//
				terrain.width() * cellSize - 1, //
				terrain.height() * cellSize - 1//
		));
		return terrainPanel;
	}

	// TODO Simplify
	private JPanel createAgentInfoPanel(MouseOnTerrain mouseOnTerrain, Terrain terrain,
			NeuralNetwork.Factory networkFactory) {
		JLabel moveLabel = new JLabel(" ");
		mouseOnTerrain.listenMove(position -> moveLabel.setText(position.toString()));
		mouseOnTerrain.listenExit(() -> moveLabel.setText(" "));

		JLabel selectLabel = new JLabel(" ");
		JTextArea programInfoArea = new JTextArea();
		programInfoArea.setEditable(false);
		programInfoArea.setBackground(null);
		JPanel attractorsInfoPanel = new JPanel();
		attractorsInfoPanel.setLayout(new GridBagLayout());
		GridBagConstraints attractorsConstraint = new GridBagConstraints();
		attractorsConstraint.gridx = 0;
		attractorsConstraint.gridy = 1;
		attractorsConstraint.fill = GridBagConstraints.VERTICAL;
		attractorsConstraint.weighty = 1;
		attractorsInfoPanel.add(new JPanel(), attractorsConstraint);
		attractorsConstraint.gridy = 0;
		attractorsConstraint.fill = GridBagConstraints.HORIZONTAL;
		attractorsConstraint.weighty = 0;
		attractorsInfoPanel.add(new JPanel(), attractorsConstraint);

		NoIcon noProgressIcon = new NoIcon();
		ProgressIcon progressIcon = new ProgressIcon(16);
		ProxyIcon attractorsInfoIcon = new ProxyIcon(noProgressIcon);
		Runnable iconRepainter = () -> {
			JTabbedPane parent = (JTabbedPane) attractorsInfoPanel.getParent();
			int tabIndex = parent.indexOfTab(attractorsInfoIcon);
			Rectangle tabHeaderBounds = parent.getBoundsAt(tabIndex);
			parent.repaint(tabHeaderBounds);
		};
		Consumer<Boolean> progressIconEnabler = enabled -> {
			Icon delegate = enabled ? progressIcon : noProgressIcon;
			attractorsInfoIcon.setDelegate(delegate);
			iconRepainter.run();
		};
		Consumer<Double> progressUpdater = percent -> {
			progressIcon.setProgress(percent);
			iconRepainter.run();
		};
		Agent[] agentToComputeFor = { null };
		Runnable[] stop = { () -> {
		} };

		// TODO CardLayout
//		CardLayout cardLayout = new CardLayout();
//		JPanel card = new JPanel(cardLayout);
//
//		String noAgentNotice = "No agent there";
//		programInfoArea.setText(noAgentNotice);
//		JLabel noAgentLabel = new JLabel(noAgentNotice);
//		card.add(noAgentLabel);
//
//		cardLayout.first(card);

		mouseOnTerrain.listenClick(position -> {
			selectLabel.setText(position.toString());
			Agent previousAgent = agentToComputeFor[0];
			Optional<Agent> agent = terrain.getAgentAt(position);
			if (agent.isEmpty()) {
				if (previousAgent != null) {
					stop[0].run();
				}
				agentToComputeFor[0] = null;
				String noAgentNotice = "No agent there";
				programInfoArea.setText(noAgentNotice);
				attractorsInfoPanel.remove(1);
				attractorsInfoPanel.add(new JLabel(noAgentNotice), attractorsConstraint);
				progressIconEnabler.accept(false);
			} else {
				Agent selectedAgent = agent.get();
				if (agentToComputeFor[0] != selectedAgent) {
					agentToComputeFor[0] = selectedAgent;
					Chromosome chromosome = selectedAgent.chromosome();
					Program program = Program.deserialize(chromosome.bytes());
					ProgramInfoBuilder infoBuilder = new ProgramInfoBuilder();
					program.executeOn(infoBuilder);
					programInfoArea.setText(infoBuilder.build());
					progressIconEnabler.accept(true);

					if (previousAgent != null) {
						stop[0].run();
					}
					Terrain attractors = Terrain.createWithSize(terrain.width(), terrain.height());
					AttractorsPanel attractorsPanel = AttractorsPanel.on(attractors, networkFactory);
					attractorsPanel.listenComputingProgress(progressUpdater);
					attractorsInfoPanel.remove(1);
					attractorsInfoPanel.add(attractorsPanel, attractorsConstraint);

					attractorsPanel.startComputingAttractors(program);
					stop[0] = attractorsPanel::stopComputingAttractors;
					closeListeners.add(attractorsPanel::stopComputingAttractors);
				}
			}
			programInfoArea.setCaretPosition(0);
		});

		JTabbedPane agentInfoTabs = new JTabbedPane();
		agentInfoTabs.addTab("Program", programInfoArea);
		agentInfoTabs.addTab("Attractors", attractorsInfoIcon, attractorsInfoPanel);
		progressIconEnabler.accept(false);

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

	private List<List<Button>> withRepaint(List<List<Button>> buttons, TerrainPanel terrainPanel,
			Consumer<Boolean> buttonsEnabler, int compositeActionsPerSecond) {
		return buttons.stream()
				.map(row -> row.stream()
						.map(toButtonWithRepaint(terrainPanel, buttonsEnabler, compositeActionsPerSecond))
						.collect(toList()))
				.collect(toList());
	}

	private Function<Button, Button> toButtonWithRepaint(TerrainPanel terrainPanel, Consumer<Boolean> buttonsEnabler,
			int compositeActionsPerSecond) {
		int stepPeriodMillis = 1000 / compositeActionsPerSecond;
		return button -> {
			Button.Action action = button.action;
			if (action instanceof Button.CompositeAction comp) {
				return Button.create(button.title, () -> {
					buttonsEnabler.accept(false);

					Iterator<Button.Action> iterator = comp.steps().iterator();
					Runnable[] tasks = { null, null };
					int nextStep = 0;
					int wait = 1;
					Instant[] reservedTime = { null };

					tasks[nextStep] = stopIfWindowClosed(() -> {
						if (iterator.hasNext()) {
							reservedTime[0] = now().plusMillis(stepPeriodMillis);
							iterator.next().execute();
							terrainPanel.repaint();
							invokeLater(tasks[wait]);
						} else {
							buttonsEnabler.accept(true);
						}
					});

					tasks[wait] = stopIfWindowClosed(() -> {
						int next = now().isAfter(reservedTime[0]) ? nextStep : wait;
						invokeLater(tasks[next]);
					});

					invokeLater(tasks[nextStep]);
				});
			} else {
				return Button.create(button.title, () -> {
					action.execute();
					terrainPanel.repaint();
				});
			}
		};
	}

	private Runnable stopIfWindowClosed(Runnable runnable) {
		return () -> {
			if (isWindowClosed) {
				return;
			}
			runnable.run();
		};
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

	public void addFilter(Function<Position, Color> filter) {
		this.filters.add(filter);
	}

	public static void logCurrentMethod(Object... args) {
		System.out.println(getCurrentMethodInternal(args));
	}

	public static String getCurrentMethod(Object... args) {
		return getCurrentMethodInternal(args);
	}

	private static String getCurrentMethodInternal(Object... args) {
		String methodName = Thread.currentThread().getStackTrace()[3].getMethodName();
		String values = Stream.of(args).map(Object::toString).collect(joining(", "));
		return methodName + "(" + values + ")";
	}
}
