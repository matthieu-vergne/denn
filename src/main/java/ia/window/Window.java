package ia.window;

import static ia.window.TerrainPanel.Drawer.*;
import static java.lang.Math.*;
import static java.util.stream.Collectors.*;
import static javax.swing.SwingUtilities.*;
import static javax.swing.WindowConstants.*;

import java.awt.Color;
import java.awt.Composite;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.RenderingHints.Key;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import ia.window.SettingsPanel.Settings;
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
			NeuralNetwork.Factory networkFactory) {
		RepaintManager.setCurrentManager(createRepaintManager());

		SettingsPanel settingsPanel = new SettingsPanel(terrain);
		Settings settings = settingsPanel.settings();

		JPanel simulationPanel = createSimulationPanel(terrain, cellSize, agentColorizer, buttons, networkFactory,
				settings.forAttractors());

		JTabbedPane tabs = new JTabbedPane();
		// TODO Add agent tab
		tabs.add("Simulation", simulationPanel);
		tabs.add("Settings", settingsPanel);

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

	private RepaintManager createRepaintManager() {
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
			}
		};
		availablePainter.set(painter);
		RepaintManager repaintManager = new RepaintManager() {

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
					Runnable painter = availablePainter.getAndSet(null);
					if (painter != null) {
						invokeLater(painter);
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

		};
		return repaintManager;
	}

	private JPanel createSimulationPanel(Terrain terrain, int cellSize, AgentColorizer agentColorizer,
			List<List<Button>> buttons, NeuralNetwork.Factory networkFactory,
			AttractorsPanel.Settings attractorsSettings) {
		MouseOnTerrain mouseOnTerrain = new MouseOnTerrain();

		TerrainPanel terrainPanel = createTerrainPanel(terrain, cellSize, agentColorizer, mouseOnTerrain);

		JPanel[] buttonsPanel = { null };
		Consumer<Boolean> buttonsEnabler = enable -> Stream.of(buttonsPanel[0].getComponents())
				.forEach(component -> component.setEnabled(enable));
		buttonsPanel[0] = createButtonsPanel(withRepaint(buttons, terrainPanel, buttonsEnabler));
		buttonsPanel[0].setBorder(new TitledBorder("Actions"));

		MouseOnTerrain.TerrainPanelListener listener = mouseOnTerrain.terrainPanelListener(terrain, terrainPanel);
		terrainPanel.addMouseListener(listener);
		terrainPanel.addMouseMotionListener(listener);
		JComponent agentPanel = createAgentInfoPanel(mouseOnTerrain, terrain, networkFactory, attractorsSettings);
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
		rendererOf[mouse] = PointerRenderer.THIN_SQUARE;
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
			Function<Pointer, Drawer> drawerFactory = pointer -> ctx -> {
				pointer.renderer().createDrawer(new DrawerFactory(ctx)).accept(pointer.position());
			};
			Drawer pointersDrawer = pointersSupplier.get().map(drawerFactory).collect(toCompositeDrawer());

			return backgroundDrawer.then(filterDrawer).then(agentDrawer).then(pointersDrawer);
		});
		mouseOnTerrain.listenMove(position -> {
			Position previousPosition = positionOf[mouse];
			positionOf[mouse] = position;

			PointerRenderer pointerRenderer = rendererOf[mouse];
			if (previousPosition != null) {
				terrainPanel.repaint(retrieveDrawnPositions(terrain, terrainPanel, previousPosition, pointerRenderer));
			}
			terrainPanel.repaint(retrieveDrawnPositions(terrain, terrainPanel, position, pointerRenderer));
		});
		mouseOnTerrain.listenExit(() -> {
			Position previousPosition = positionOf[mouse];
			positionOf[mouse] = null;

			Bounds drawnPositions = retrieveDrawnPositions(terrain, terrainPanel, previousPosition, rendererOf[mouse]);
			System.out.println(drawnPositions);
			terrainPanel.repaint(drawnPositions);
		});
		mouseOnTerrain.listenClick(position -> {
			Position previousPosition = positionOf[selection];
			positionOf[selection] = position;

			if (previousPosition != null) {
				terrainPanel.repaint(
						retrieveDrawnPositions(terrain, terrainPanel, previousPosition, rendererOf[selection]));
			}
			terrainPanel.repaint(retrieveDrawnPositions(terrain, terrainPanel, position, rendererOf[selection]));
		});

		terrainPanel.setPreferredSize(new Dimension(//
				terrain.width() * cellSize - 1, //
				terrain.height() * cellSize - 1//
		));
		return terrainPanel;
	}

	// TODO Simplify
	private Position.Bounds retrieveDrawnPositions(Terrain terrain, TerrainPanel terrainPanel, Position position,
			PointerRenderer pointerRenderer) {
		GraphicsCatcher graphics = new GraphicsCatcher();
		Position minPixel = Position.ORIGIN;
		Position maxPixel = minPixel.move(terrainPanel.getWidth() - 1, terrainPanel.getHeight() - 1);
		Bounds componentBounds = minPixel.boundsTo(maxPixel);
		DrawContext dc = new DrawContext(graphics, terrain, componentBounds);
		DrawerFactory df = new DrawerFactory(dc);
		Consumer<Position> drawer = pointerRenderer.createDrawer(df);
		drawer.accept(position);
		Position.Bounds drawnBounds = graphics.catchedBounds();
		Bounds drawn = dc.pixelToTerrain().convert(drawnBounds//
				.reduce(0, 1, 0, 1)//
				)//
//				.extend(pointerRenderer.extraStroke())//
				;
		// FIXME Should be 1 position bound on thin square
		System.out.println(drawn);
		return drawn;
	}

	// TODO Simplify
	private JPanel createAgentInfoPanel(MouseOnTerrain mouseOnTerrain, Terrain terrain,
			NeuralNetwork.Factory networkFactory, AttractorsPanel.Settings attractorsSettings) {
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
					AttractorsPanel attractorsPanel = AttractorsPanel.on(attractors, networkFactory,
							attractorsSettings);
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
			Consumer<Boolean> buttonsEnabler) {
		return buttons.stream()
				.map(row -> row.stream().map(toButtonWithRepaint(terrainPanel, buttonsEnabler)).collect(toList()))
				.collect(toList());
	}

	private Function<Button, Button> toButtonWithRepaint(TerrainPanel terrainPanel, Consumer<Boolean> buttonsEnabler) {
		return button -> {
			Button.Action action = button.action;
			if (action instanceof Button.CompositeAction comp) {
				return Button.create(button.title, () -> {
					buttonsEnabler.accept(false);
					invokeLater(stopIfWindowClosed(new Runnable() {
						Iterator<Button.Action> iterator = comp.steps().iterator();

						@Override
						public void run() {
							if (iterator.hasNext()) {
								iterator.next().execute();
								terrainPanel.repaint();
								invokeLater(this);
							} else {
								buttonsEnabler.accept(true);
							}
						}
					}));
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
			List<List<Button>> buttons, NeuralNetwork.Factory networkFactory) {
		return new Window(terrain, cellSize, agentColorizer, buttons, networkFactory);
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
		String values = Stream.of(args).map(Objects::toString).collect(joining(", "));
		return methodName + "(" + values + ")";
	}

	private static class GraphicsCatcher extends Graphics2D {
		private int minX = Integer.MAX_VALUE;
		private int minY = Integer.MAX_VALUE;
		private int maxX = Integer.MIN_VALUE;
		private int maxY = Integer.MIN_VALUE;
		private Stroke stroke;

		public Position.Bounds catchedBounds() {
			return Position.Bounds.between(Position.at(minX, minY), Position.at(maxX, maxY));
		}

		private void update(int x, int y) {
			minX = min(minX, x);
			maxX = max(maxX, x);
			minY = min(minY, y);
			maxY = max(maxY, y);
		}

		@Override
		public void draw(Shape s) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public boolean drawImage(Image img, AffineTransform xform, ImageObserver obs) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawImage(BufferedImage img, BufferedImageOp op, int x, int y) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawRenderedImage(RenderedImage img, AffineTransform xform) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawRenderableImage(RenderableImage img, AffineTransform xform) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawString(String str, int x, int y) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawString(String str, float x, float y) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawString(AttributedCharacterIterator iterator, int x, int y) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawString(AttributedCharacterIterator iterator, float x, float y) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawGlyphVector(GlyphVector g, float x, float y) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void fill(Shape s) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public boolean hit(Rectangle rect, Shape s, boolean onStroke) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public GraphicsConfiguration getDeviceConfiguration() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void setComposite(Composite comp) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void setPaint(Paint paint) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void setStroke(Stroke s) {
			stroke = s;
		}

		@Override
		public void setRenderingHint(Key hintKey, Object hintValue) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Object getRenderingHint(Key hintKey) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void setRenderingHints(Map<?, ?> hints) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void addRenderingHints(Map<?, ?> hints) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public RenderingHints getRenderingHints() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void translate(int x, int y) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void translate(double tx, double ty) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void rotate(double theta) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void rotate(double theta, double x, double y) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void scale(double sx, double sy) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void shear(double shx, double shy) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void transform(AffineTransform Tx) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void setTransform(AffineTransform Tx) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public AffineTransform getTransform() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Paint getPaint() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Composite getComposite() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void setBackground(Color color) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Color getBackground() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Stroke getStroke() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void clip(Shape s) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public FontRenderContext getFontRenderContext() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Graphics create() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Color getColor() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void setColor(Color c) {
			// Nothing to do
		}

		@Override
		public void setPaintMode() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void setXORMode(Color c1) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Font getFont() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void setFont(Font font) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public FontMetrics getFontMetrics(Font f) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Rectangle getClipBounds() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void clipRect(int x, int y, int width, int height) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void setClip(int x, int y, int width, int height) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public Shape getClip() {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void setClip(Shape clip) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void copyArea(int x, int y, int width, int height, int dx, int dy) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawLine(int x1, int y1, int x2, int y2) {
			update(x1, y1);
			update(x2, y2);
		}

		@Override
		public void fillRect(int x, int y, int width, int height) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void clearRect(int x, int y, int width, int height) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void fillRoundRect(int x, int y, int width, int height, int arcWidth, int arcHeight) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawOval(int x, int y, int width, int height) {
			update(x, y);
			update(x + width - 1, y + height - 1);
		}

		@Override
		public void fillOval(int x, int y, int width, int height) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public boolean drawImage(Image img, int x, int y, ImageObserver observer) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public boolean drawImage(Image img, int x, int y, int width, int height, ImageObserver observer) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public boolean drawImage(Image img, int x, int y, Color bgcolor, ImageObserver observer) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public boolean drawImage(Image img, int x, int y, int width, int height, Color bgcolor,
				ImageObserver observer) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
				ImageObserver observer) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public boolean drawImage(Image img, int dx1, int dy1, int dx2, int dy2, int sx1, int sy1, int sx2, int sy2,
				Color bgcolor, ImageObserver observer) {
			throw new RuntimeException("Not implemented");
		}

		@Override
		public void dispose() {
			throw new RuntimeException("Not implemented");
		}

	}
}
