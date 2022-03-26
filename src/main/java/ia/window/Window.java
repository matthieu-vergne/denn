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
import java.util.List;
import java.util.function.Function;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import ia.terrain.Position;
import ia.terrain.Terrain;

public class Window {

	private final JFrame frame;

	private Window(Terrain terrain, int cellSize, AgentColorizer agentColorizer, List<Button> buttons) {
		this.frame = new JFrame("AI");

		JPanel canvas = createCanvas(terrain, agentColorizer);
		JPanel buttonsPanel = createButtonsPanel(withRepaint(buttons, canvas));

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

	private List<Button> withRepaint(List<Button> buttons, JPanel canvas) {
		return buttons.stream().map(toButtonWithRepaint(canvas)).collect(toList());
	}

	private Function<? super Button, ? extends Button> toButtonWithRepaint(JPanel canvas) {
		return button -> Button.create(button.title, () -> {
			button.action.run();
			canvas.repaint();
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
				button.action.run();
			}
		});
	}

	public static Window create(Terrain terrain, int cellSize, AgentColorizer agentColorizer, List<Button> buttons) {
		return new Window(terrain, cellSize, agentColorizer, buttons);
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

		private final String title;
		private final Runnable action;

		private Button(String title, Runnable action) {
			this.title = title;
			this.action = action;
		}

		public static Button create(String title, Runnable action) {
			return new Button(title, action);
		}

	}
}
