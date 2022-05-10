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
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import ia.terrain.Position;
import ia.terrain.Terrain;
import ia.window.Window.Button.Action;

public class Window {

	private final JFrame frame;
	private final int compositeActionsPerSecond;
	private final JPanel buttonsPanel;
	private Optional<Function<Position, Color>> filter = Optional.empty();
	private boolean isWindowClosed = false;

	private Window(Terrain terrain, int cellSize, AgentColorizer agentColorizer, List<Button> buttons,
			int compositeActionsPerSecond) {
		this.frame = new JFrame("AI");

		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				isWindowClosed = true;
				frame.removeWindowListener(this);
			}
		});

		this.compositeActionsPerSecond = compositeActionsPerSecond;
		JPanel canvas = createCanvas(terrain, agentColorizer);
		this.buttonsPanel = createButtonsPanel(withRepaint(buttons, canvas));

		Container contentPane = frame.getContentPane();
		contentPane.setLayout(new GridBagLayout());
		GridBagConstraints cst = new GridBagConstraints();
		cst.gridx = 0;
		cst.gridy = GridBagConstraints.RELATIVE;

		cst.fill = GridBagConstraints.BOTH;
		cst.weightx = 1;
		cst.weighty = 1;
		contentPane.add(canvas, cst);

		cst.fill = GridBagConstraints.HORIZONTAL;
		cst.weightx = 1;
		cst.weighty = 0;
		contentPane.add(buttonsPanel, cst);

		int preferredWidth = terrain.width() * cellSize;
		int preferredHeight = terrain.height() * cellSize + buttonsPanel.getPreferredSize().height;
		frame.setPreferredSize(new Dimension(preferredWidth, preferredHeight));
		frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
	}

	private void disableButtons() {
		Stream.of(buttonsPanel.getComponents()).forEach(component -> component.setEnabled(false));
	}

	private void enableButtons() {
		Stream.of(buttonsPanel.getComponents()).forEach(component -> component.setEnabled(true));
	}

	private List<Button> withRepaint(List<Button> buttons, JPanel canvas) {
		return buttons.stream().map(toButtonWithRepaint(canvas)).collect(toList());
	}

	private Function<? super Button, ? extends Button> toButtonWithRepaint(JPanel canvas) {
		int stepPeriodMillis = 1000 / compositeActionsPerSecond;
		return button -> {
			Action action = button.action;
			if (action instanceof Button.CompositeAction comp) {
				return Button.create(button.title, () -> {
					disableButtons();

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
							enableButtons();
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
	private JPanel createCanvas(Terrain terrain, AgentColorizer agentColorizer) {
		return new JPanel() {
			@Override
			public void paint(Graphics graphics) {
				drawTerrain(terrain, graphics, agentColorizer);
				filter.ifPresent(filter -> drawFilter(terrain, graphics, filter));
			}

			private void drawFilter(Terrain terrain, Graphics graphics, Function<Position, Color> filter) {
				Graphics2D graphics2D = (Graphics2D) graphics;
				Rectangle clipBounds = graphics2D.getClipBounds();
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
		};
	}

	private JPanel createButtonsPanel(List<Button> buttons) {
		JPanel buttonsPanel = new JPanel(new GridLayout(1, buttons.size()));
		buttons.forEach(button -> {
			buttonsPanel.add(createButton(button));
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
			int compositeActionsPerSecond, List<Button> buttons) {
		return new Window(terrain, cellSize, agentColorizer, buttons, compositeActionsPerSecond);
	}

	public void setSize(int width, int height) {
		frame.setSize(width, height);
	}

	private void drawTerrain(Terrain terrain, Graphics graphics, AgentColorizer agentColorizer) {
		Graphics2D graphics2D = (Graphics2D) graphics;
		Rectangle clipBounds = graphics2D.getClipBounds();
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
}
