package ia.window;

import static java.awt.Color.*;
import static java.util.stream.Collectors.*;
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
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import ia.terrain.Position;
import ia.terrain.Terrain;
import ia.window.Window.Button.Action;

public class Window {

	private final JFrame frame;
	private final int iterateStepsPerSecond;
	private final JPanel buttonsPanel;

	private Window(Terrain terrain, int cellSize, AgentColorizer agentColorizer, List<Button> buttons,
			int iterateStepsPerSecond) {
		this.frame = new JFrame("AI");

		JPanel canvas = createCanvas(terrain, agentColorizer);
		this.buttonsPanel = createButtonsPanel(withRepaint(buttons, canvas));
		this.iterateStepsPerSecond = iterateStepsPerSecond;

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
		return button -> Button.create(button.title, () -> {
			Action action = button.action;
			if (action instanceof Button.CompositeAction comp) {
				Iterator<Action> iterator = comp.steps().iterator();
				disableButtons();
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						if (!iterator.hasNext()) {
							enableButtons();
							return;
						}

						iterator.next().execute();
						canvas.repaint();
						try {
							Thread.sleep(1000 / iterateStepsPerSecond);
						} catch (InterruptedException cause) {
							cause.printStackTrace();
							enableButtons();
							return;
						}
						SwingUtilities.invokeLater(this);
					}
				});
			} else {
				action.execute();
				canvas.repaint();
			}
		});
	}

	@SuppressWarnings("serial")
	private JPanel createCanvas(Terrain terrain, AgentColorizer agentColorizer) {
		return new JPanel() {
			@Override
			public void paint(Graphics graphics) {
				drawTerrain(terrain, graphics, agentColorizer);
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

	public static Window create(Terrain terrain, int cellSize, AgentColorizer agentColorizer, int iterateStepsPerSecond,
			List<Button> buttons) {
		return new Window(terrain, cellSize, agentColorizer, buttons, iterateStepsPerSecond);
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

			default Action repeat(int count) {
				if (count < 0) {
					throw new IllegalArgumentException("count must be >0: " + count);
				}
				Action action = noop();
				for (int i = 0; i < count; i++) {
					action = action.then(this);
				}
				return action;
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
}
