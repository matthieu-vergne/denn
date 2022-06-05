package ia.window;

import static java.util.stream.Collectors.*;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.swing.JPanel;

import ia.agent.LayeredNetwork;
import ia.utils.Position;

@SuppressWarnings("serial")
public class LayeredNetworkPanel extends JPanel {

	private final Infos infos;
	private Optional<Position> mousePosition = Optional.empty();

	record Settings(Supplier<Alignment> alignment) {
	}

	public LayeredNetworkPanel(LayeredNetwork.Description description, Settings settings) {
		Collection<Collection<Integer>> layers = description.layers();
		this.infos = new Infos(//
				computeInfosForNodes(layers, description::name, settings.alignment), //
				computeInfosForLinks(layers, description::weight), //
				layerMaxSize(layers)//
		);
		MouseMoveController mouseMoveController = new MouseMoveController();
		MouseMoveController.Listener listener = mouseMoveController.pointListener();
		this.addMouseListener(listener);
		this.addMouseMotionListener(listener);
		mouseMoveController.listenMove(position -> {
			mousePosition = Optional.of(position);
			repaint();
		});
		mouseMoveController.listenExit(() -> {
			mousePosition = Optional.empty();
			repaint();
		});
	}

	private static int layerMaxSize(Collection<Collection<Integer>> layers) {
		return layers.stream().mapToInt(layer -> layer.size()).max().getAsInt();
	}

	@Override
	public Dimension getPreferredSize() {
		int parentWidth = Utils.parentAvailableDimension(this).width;
		return new Dimension(parentWidth, parentWidth);// TODO height depending on description
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		super.paintComponent(graphics);
		Graphics2D graphics2D = (Graphics2D) graphics;

		FontMetrics fontMetrics = graphics2D.getFontMetrics();
		int ascent = fontMetrics.getAscent();
		int descent = fontMetrics.getDescent();
		int maxTextLength = infos.ofNodes.values().stream()//
				.map(NodeInfo::name)//
				.mapToInt(String::length)//
				.max().getAsInt();
		int maxTextWidth = infos.ofNodes.values().stream()//
				.map(NodeInfo::name)//
				.mapToInt(fontMetrics::stringWidth)//
				.max().getAsInt();

		int nodeBorders = 4;
		int nodeWidth = maxTextWidth + nodeBorders;
		int nodeHeight = ascent + descent + nodeBorders;
		int nodeSpace = Math.max(2, computeSpaceToDistribute(nodeWidth, infos.layerMaxSize));
		int layerSpace = nodeHeight * 3;

		// Adapt nodes positions to graphics
		Position.Conversion conversion = Position.Conversion.createFromConversion(position -> Position.at(//
				position.x() * (nodeWidth + nodeSpace) + nodeWidth / 2, //
				position.y() * (nodeHeight + layerSpace) + nodeHeight / 2//
		));
		Map<Object, NodeInfo> nodeInfos = infos.ofNodes.entrySet().stream().collect(toMap(//
				entry -> entry.getKey(), //
				entry -> entry.getValue().convertPosition(conversion)//
		));
		Function<Position, Rectangle> nodeBounder = position -> new Rectangle(//
				position.x() - nodeWidth / 2, //
				position.y() - nodeHeight / 2, //
				nodeWidth, //
				nodeHeight//
		);

		Collection<Runnable> nodeRenderers = new LinkedList<Runnable>();
		Collection<Runnable> standardLinkRenderers = new LinkedList<Runnable>();
		Collection<Runnable> highlightedLinkRenderers = new LinkedList<Runnable>();

		// Draw links
		interface LinkRenderer {
			void render(Color color, Stroke stroke, Position upPosition, Position downPosition);
		}
		LinkRenderer linkRenderer = (color, stroke, upPosition, downPosition) -> {
			graphics2D.setColor(color);
			graphics2D.setStroke(stroke);
			graphics2D.drawLine(upPosition.x(), upPosition.y(), downPosition.x(), downPosition.y());
		};
		infos.ofLinks.forEach(info -> {
			NodeInfo upInfo = nodeInfos.get(info.upNode);
			NodeInfo downInfo = nodeInfos.get(info.downNode);
			Position upPosition = upInfo.position;
			Position downPosition = downInfo.position;
			Rectangle upNodeBounds = nodeBounder.apply(upPosition);
			Rectangle downNodeBounds = nodeBounder.apply(downPosition);

			if (mousePosition.filter(inside(upNodeBounds)).isPresent()) {
				Position weightPosition = downInfo.position.move(0, -nodeHeight);
				Rectangle weightBounds = nodeBounder.apply(weightPosition);
				Position weightTop = downPosition.move(0, -nodeHeight * 3 / 2);
				highlightedLinkRenderers
						.add(() -> linkRenderer.render(Color.RED, new BasicStroke(3), upPosition, weightTop));
				nodeRenderers.add(() -> drawNode(graphics2D, formatWeight(info, maxTextLength), weightBounds, Color.RED,
						Color.WHITE));
			} else if (mousePosition.filter(inside(downNodeBounds)).isPresent()) {
				Position weightPosition = upInfo.position.move(0, nodeHeight);
				Rectangle weightBounds = nodeBounder.apply(weightPosition);
				Position weightBottom = upPosition.move(0, nodeHeight * 3 / 2);
				highlightedLinkRenderers
						.add(() -> linkRenderer.render(Color.RED, new BasicStroke(3), weightBottom, downPosition));
				nodeRenderers.add(() -> drawNode(graphics2D, formatWeight(info, maxTextLength), weightBounds, Color.RED,
						Color.WHITE));
			} else {
				standardLinkRenderers
						.add(() -> linkRenderer.render(Color.BLACK, new BasicStroke(1), upPosition, downPosition));
			}

		});

		// Draw nodes
		nodeInfos.values().forEach(info -> {
			Rectangle nodeBounds = nodeBounder.apply(info.position);
			if (mousePosition.filter(inside(nodeBounds)).isPresent()) {
				nodeRenderers.add(() -> {
					drawNode(graphics2D, info.name, nodeBounds, Color.WHITE, Color.BLACK);
					graphics2D.setColor(Color.RED);
					graphics2D.setStroke(new BasicStroke(3));
					graphics2D.drawRect(nodeBounds.x, nodeBounds.y, nodeBounds.width, nodeBounds.height);
				});
			} else {
				nodeRenderers.add(() -> drawNode(graphics2D, info.name, nodeBounds, Color.BLACK, Color.WHITE));
			}
		});

		standardLinkRenderers.forEach(Runnable::run);
		highlightedLinkRenderers.forEach(Runnable::run);
		nodeRenderers.forEach(Runnable::run);
	}

	private String formatWeight(LinkInfo info, int maxTextLength) {
		double weight = info.weight;
		String format;
		if (weight % 1 == 0) {
			// Avoid decimal part if integer
			format = String.format("%d", (int) weight);
		} else {
			// Add limited decimal part if non-integer
			format = String.format("%.3f", weight);
		}
		if (format.length() > maxTextLength) {
			// Use scientific format to focus on scale
			format = String.format("%." + (maxTextLength - 6) + "e", weight);
		}
		return format;
	}

	private int computeSpaceToDistribute(int nodeWidth, int layerSize) {
		return (getWidth() - layerSize * nodeWidth) / (layerSize - 1);
	}

	private void drawNode(Graphics2D graphics2D, String text, Rectangle bounds, Color nodeColor, Color textColor) {
		FontMetrics fontMetrics = graphics2D.getFontMetrics();
		int descent = fontMetrics.getDescent();
		int textWidth = fontMetrics.stringWidth(text);
		int textHeight = fontMetrics.getHeight();
		Point textPoint = new Point(//
				bounds.x + (bounds.width - textWidth) / 2, //
				bounds.y + (bounds.height + textHeight - 2 * descent) / 2//
		);
		graphics2D.setStroke(new BasicStroke(1));
		graphics2D.setColor(nodeColor);
		graphics2D.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
		graphics2D.setColor(textColor);
		graphics2D.drawString(text, textPoint.x, textPoint.y);
	}

	private Predicate<Position> inside(Rectangle nodeBounds) {
		return mousePosition -> nodeBounds.contains(new Point(mousePosition.x(), mousePosition.y()));
	}

	private Map<Object, NodeInfo> computeInfosForNodes(Collection<Collection<Integer>> layers,
			Function<Integer, String> nodeNamer, Supplier<Alignment> nodeAlignment) {
		Aligner aligner = nodeAlignment.get().compute(layers);
		Map<Object, NodeInfo> infos = new HashMap<>();
		Iterator<Collection<Integer>> layerIterator = layers.iterator();
		for (int y = 0; layerIterator.hasNext(); y++) {
			Collection<Integer> layer = layerIterator.next();
			Iterator<Integer> nodeIterator = layer.iterator();
			for (int x = aligner.xStartFor(layer); nodeIterator.hasNext(); x = aligner.next(layer)) {
				Integer node = nodeIterator.next();
				String name = nodeNamer.apply(node);
				Position position = Position.at(x, y);
				infos.put(node, new NodeInfo(name, position));
			}
		}
		return infos;
	}

	private Collection<LinkInfo> computeInfosForLinks(Collection<Collection<Integer>> layers,
			BiFunction<Integer, Integer, Double> weighter) {
		// Up layers start from 1st
		Iterator<Collection<Integer>> upLayers = layers.iterator();
		// Down layers start from 2nd
		Iterator<Collection<Integer>> downLayers = layers.iterator();
		downLayers.next();

		Collection<LinkInfo> infos = new LinkedList<>();
		while (downLayers.hasNext()) {
			Collection<Integer> upLayer = upLayers.next();
			Collection<Integer> downLayer = downLayers.next();
			linksBetween(upLayer, downLayer).forEach(link -> {
				Double weight = weighter.apply(link.upNode, link.downNode);
				infos.add(new LinkInfo(link.upNode, link.downNode, weight));
			});

		}
		return infos;
	}

	record Link(Integer upNode, Integer downNode) {
	}

	private Stream<Link> linksBetween(Collection<Integer> upLayer, Collection<Integer> downLayer) {
		return upLayer.stream().flatMap(upNode -> //
		downLayer.stream().flatMap(downNode -> //
		Stream.of(new Link(upNode, downNode))//
		));
	}

	record NodeInfo(String name, Position position) {
		NodeInfo convertPosition(Position.Conversion conversion) {
			return new NodeInfo(name, conversion.convert(position));
		}
	}

	record LinkInfo(Object upNode, Object downNode, double weight) {
	}

	record Infos(Map<Object, NodeInfo> ofNodes, Collection<LinkInfo> ofLinks, int layerMaxSize) {
	}

	interface Aligner {

		int xStartFor(Collection<Integer> layer);

		int next(Collection<Integer> layer);

	}

	public static enum Alignment {
		LEFT(layers -> new Aligner() {

			@Override
			public int xStartFor(Collection<Integer> layer) {
				return 0;
			}

			Collection<Integer> currentLayer;
			int x;

			@Override
			public int next(Collection<Integer> layer) {
				if (currentLayer != layer) {
					currentLayer = layer;
					x = xStartFor(layer);
				}
				return ++x;
			}
		}), //
		RIGHT(layers -> new Aligner() {
			int layerMaxSize = layerMaxSize(layers);

			@Override
			public int xStartFor(Collection<Integer> layer) {
				return layerMaxSize - layer.size();
			}

			Collection<Integer> currentLayer;
			int x;

			@Override
			public int next(Collection<Integer> layer) {
				if (currentLayer != layer) {
					currentLayer = layer;
					x = xStartFor(layer);
				}
				return ++x;
			}
		}), //
		CENTER(layers -> new Aligner() {
			int layerMaxSize = layerMaxSize(layers);

			@Override
			public int xStartFor(Collection<Integer> layer) {
				return (layerMaxSize - layer.size()) / 2;
			}

			Collection<Integer> currentLayer;
			int x;

			@Override
			public int next(Collection<Integer> layer) {
				if (currentLayer != layer) {
					currentLayer = layer;
					x = xStartFor(layer);
				}
				return ++x;
			}
		}), //
		JUSTIFY(layers -> new Aligner() {
			int layerMaxSize = layerMaxSize(layers);

			@Override
			public int xStartFor(Collection<Integer> layer) {
				return 0;
			}

			Collection<Integer> currentLayer;
			int index;

			@Override
			public int next(Collection<Integer> layer) {
				if (currentLayer != layer) {
					currentLayer = layer;
					index = 0;
				}
				index++;
				return (int) Math.round((double) index * (layerMaxSize - 1) / (layer.size() - 1));
			}
		}),//
		;

		private final Function<Collection<Collection<Integer>>, Aligner> alignerComputer;

		private Alignment(Function<Collection<Collection<Integer>>, Aligner> alignerComputer) {
			this.alignerComputer = alignerComputer;
		}

		Aligner compute(Collection<Collection<Integer>> layers) {
			return alignerComputer.apply(layers);
		}
	}
}
