package ia.window;

import static java.awt.Color.*;
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
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
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
import javax.swing.JTextField;

import ia.agent.Agent;
import ia.terrain.Position;
import ia.terrain.Terrain;
import ia.window.Window.Button.Action;

public class Window {

	private final JFrame frame;
	private Optional<Function<Position, Color>> filter = Optional.empty();
	private boolean isWindowClosed = false;

	private Window(Terrain terrain, int cellSize, AgentColorizer agentColorizer, List<List<Button>> buttons,
			int compositeActionsPerSecond) {

		JPanel simulationPanel = createSimulationPanel(terrain, cellSize, agentColorizer, buttons,
				compositeActionsPerSecond);

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
			List<List<Button>> buttons, int compositeActionsPerSecond) {
		MousePosition mousePosition = new MousePosition();

		Position[] pointer = { null };
		mousePosition.listenMove(position -> pointer[0] = position);
		JPanel canvas = createCanvas(terrain, agentColorizer, () -> Optional.ofNullable(pointer[0]));
		int preferredWidth = terrain.width() * cellSize - 1;
		int preferredHeight = terrain.height() * cellSize - 1;
		canvas.setPreferredSize(new Dimension(preferredWidth, preferredHeight));

		JPanel[] buttonsPanel = { null };
		Consumer<Boolean> buttonsEnabler = enable -> Stream.of(buttonsPanel[0].getComponents())
				.forEach(component -> component.setEnabled(enable));
		buttonsPanel[0] = createButtonsPanel(withRepaint(buttons, canvas, buttonsEnabler, compositeActionsPerSecond));

		MousePosition.GraphicsListener listener = mousePosition.graphicsListener(terrain, canvas);
		canvas.addMouseListener(listener);
		canvas.addMouseMotionListener(listener);

		JComponent agentPanel = createAgentPanel(mousePosition, terrain, canvas);

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
		terrainPanel.add(agentPanel, cst);

		return terrainPanel;
	}

	private JComponent createAgentPanel(MousePosition mousePosition, Terrain terrain, JPanel canvas) {
		JLabel coordLabel = new JLabel();
		mousePosition.listenMove(position -> {
			if (position == null) {
				coordLabel.setText("");
			} else {
				coordLabel.setText(position.toString());
			}
			canvas.repaint();
		});

		JLabel agentLabel = new JLabel();
		mousePosition.listenClick(position -> {
			Optional<Agent> agent = terrain.getAgentAt(position);
			String agentString;
			if (agent.isPresent()) {
				// TODO Retrieve agent info
				agentString = agent.get().toString();
			} else {
				agentString = "-";
			}
			agentLabel.setText(position + " " + agentString);
		});

		JPanel agentPanel = new JPanel();
		agentPanel.setLayout(new GridBagLayout());

		GridBagConstraints cst = new GridBagConstraints();
		cst.insets = new Insets(5, 5, 5, 5);
		cst.gridx = GridBagConstraints.RELATIVE;
		cst.gridy = 0;

		cst.fill = GridBagConstraints.NONE;
		cst.weightx = 0;
		cst.weighty = 0;
		agentPanel.add(new JLabel("Mouse"), cst);
		agentPanel.add(new JLabel(":"), cst);

		cst.fill = GridBagConstraints.HORIZONTAL;
		cst.weightx = 1;
		cst.weighty = 0;
		agentPanel.add(coordLabel, cst);

		cst.gridy++;

		cst.fill = GridBagConstraints.NONE;
		cst.weightx = 0;
		cst.weighty = 0;
		agentPanel.add(new JLabel("Clicked"), cst);
		agentPanel.add(new JLabel(":"), cst);

		cst.fill = GridBagConstraints.HORIZONTAL;
		cst.weightx = 1;
		cst.weighty = 0;
		agentPanel.add(agentLabel, cst);

		return new JScrollPane(agentPanel);
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

	@SuppressWarnings("serial")
	private JPanel createCanvas(Terrain terrain, AgentColorizer agentColorizer, Supplier<Optional<Position>> pointer) {
		return new JPanel() {
			@Override
			public void paint(Graphics graphics) {
				drawTerrain(this, graphics, terrain, agentColorizer, pointer);
				filter.ifPresent(filter -> drawFilter(this, graphics, terrain, filter));
			}
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
			int compositeActionsPerSecond, List<List<Button>> buttons) {
		return new Window(terrain, cellSize, agentColorizer, buttons, compositeActionsPerSecond);
	}

	public void setSize(int width, int height) {
		frame.setSize(width, height);
	}

	private void drawTerrain(JPanel panel, Graphics graphics, Terrain terrain, AgentColorizer agentColorizer,
			Supplier<Optional<Position>> pointer) {
		Graphics2D graphics2D = (Graphics2D) graphics;
		Rectangle clipBounds = panel.getBounds();
		// TODO Scale with panel bounds, but don't draw out of graphics
		int clipWidth = clipBounds.width;
		int clipHeight = clipBounds.height;

		// Background
		graphics2D.setColor(WHITE);
		graphics2D.fillRect(0, 0, clipWidth, clipHeight);

		// Agents
		double width = (double) clipWidth / terrain.width();
		double height = (double) clipHeight / terrain.height();
		terrain.agents().forEach(agent -> {
			Color color = agentColorizer.colorize(agent);
			Position position = terrain.getAgentPosition(agent);
			int x = (int) (position.x * width);
			int y = (int) (position.y * height);
			graphics2D.setColor(color);
			graphics2D.fillRect(x, y, (int) width, (int) height);
		});

		pointer.get().ifPresent(position -> {
			Color color = Color.BLACK;
			int x = (int) (position.x * width);
			int y = (int) (position.y * height);
			graphics2D.setColor(color);
			graphics2D.drawRect(x, y, (int) width, (int) height);
		});
	}

	private void drawFilter(JPanel panel, Graphics graphics, Terrain terrain, Function<Position, Color> filter) {
		Graphics2D graphics2D = (Graphics2D) graphics;
		Rectangle clipBounds = panel.getBounds();
		// TODO Scale with panel bounds, but don't draw out of graphics
		int clipWidth = clipBounds.width;
		int clipHeight = clipBounds.height;

		// Filters
		double width = (double) clipWidth / terrain.width();
		double height = (double) clipHeight / terrain.height();
		for (int px = 0; px < terrain.width(); px++) {
			for (int py = 0; py < terrain.height(); py++) {
				Position position = Position.at(px, py);
				Color color = filter.apply(position);
				int x = (int) (position.x * width);
				int y = (int) (position.y * height);
				graphics2D.setColor(color);
				graphics2D.fillRect(x, y, (int) width, (int) height);
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

	public void setFilter(Function<Position, Color> filter) {
		this.filter = Optional.of(filter);
	}

	private static void logCurrentMethod() {
		System.out.println(Thread.currentThread().getStackTrace()[2].getMethodName());
	}

	static class MousePosition {
		private Collection<Consumer<Position>> moveListeners = new LinkedList<>();
		private Collection<Consumer<Position>> clickListeners = new LinkedList<>();

		public void listenMove(Consumer<Position> listener) {
			moveListeners.add(listener);
		}

		public void listenClick(Consumer<Position> listener) {
			clickListeners.add(listener);
		}

		public void set(Position position) {
			for (Consumer<Position> listener : moveListeners) {
				listener.accept(position);
			}
		}

		public void click(Position position) {
			for (Consumer<Position> listener : clickListeners) {
				listener.accept(position);
			}
		}

		public void enable(Position position) {
			set(position);
		}

		public void disable() {
			set(null);
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
					enable(positioner.apply(event.getPoint()));
				}

				@Override
				public void mouseExited(MouseEvent event) {
					disable();
				}

				@Override
				public void mouseMoved(MouseEvent event) {
					set(positioner.apply(event.getPoint()));
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
					click(positioner.apply(event.getPoint()));
				}
			};
		}
	}
}
