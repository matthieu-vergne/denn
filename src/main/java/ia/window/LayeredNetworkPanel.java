package ia.window;

import static java.util.stream.Collectors.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.swing.JPanel;

import ia.agent.LayeredNetwork;
import ia.terrain.Position;

@SuppressWarnings("serial")
public class LayeredNetworkPanel extends JPanel {

	private final Infos infos;

	public LayeredNetworkPanel(LayeredNetwork.Description description) {
		Collection<Collection<Integer>> layers = description.layers();
		this.infos = new Infos(//
				computeInfosForNodes(layers, description::name), //
				computeInfosForLinks(layers, description::weight)//
		);
		// TODO Highlight on mouse hover
		// TODO Display weights on mouse hover
	}

	@Override
	public Dimension getPreferredSize() {
		int parentWidth = Utils.parentAvailableDimension(this).width;
		return new Dimension(parentWidth, parentWidth);// TODO height depending on description
	}

	@Override
	protected void paintComponent(Graphics graphics) {
		Graphics2D graphics2D = (Graphics2D) graphics;

		FontMetrics fontMetrics = graphics2D.getFontMetrics();
		int ascent = fontMetrics.getAscent();
		int descent = fontMetrics.getDescent();
		int maxTextWidth = infos.ofNodes.values().stream()//
				.map(NodeInfo::name)//
				.mapToInt(fontMetrics::stringWidth)//
				.max().getAsInt();
		int textHeight = fontMetrics.getHeight();

		int nodeBorders = 4;
		int nodeWidth = maxTextWidth + nodeBorders;
		int nodeHeight = ascent + descent + nodeBorders;
		int nodeSpace = 20;// TODO Adapt to fill the width
		int layerSpace = 100;// TODO adapt to weight display

		// Adapt nodes positions to graphics
		PositionConverter conversion = PositionConverter.createFromConversion(position -> Position.at(//
				position.x() * (nodeWidth + nodeSpace) + nodeWidth / 2, //
				position.y() * (nodeHeight + layerSpace) + nodeHeight / 2//
		));
		Map<Object, NodeInfo> nodeInfos = infos.ofNodes.entrySet().stream().collect(toMap(//
				entry -> entry.getKey(), //
				entry -> entry.getValue().convertPosition(conversion)//
		));

		// Draw links
		infos.ofLinks.forEach(info -> {
			Position upPosition = nodeInfos.get(info.upNode).position;
			Position downPosition = nodeInfos.get(info.downNode).position;
			graphics2D.drawLine(upPosition.x(), upPosition.y(), downPosition.x(), downPosition.y());
		});

		// Draw nodes
		nodeInfos.values().forEach(info -> {
			Position position = info.position;
			int x = position.x() - nodeWidth / 2;
			int y = position.y() - nodeHeight / 2;
			String text = info.name;
			int textWidth = fontMetrics.stringWidth(text);

			graphics2D.setColor(Color.BLACK);
			graphics2D.fillRect(x, y, nodeWidth, nodeHeight);
			graphics2D.setColor(Color.WHITE);
			graphics2D.drawString(text, //
					x + (nodeWidth - textWidth) / 2, //
					y + (nodeHeight + textHeight - 2 * descent) / 2//
			);
		});
	}

	private Map<Object, NodeInfo> computeInfosForNodes(Collection<Collection<Integer>> layers, Function<Integer, String> nodeNamer) {
		// TODO Align left
		// TODO Align right
		// TODO Justify
		// TODO Center
		Map<Object, NodeInfo> infos = new HashMap<>();
		Iterator<Collection<Integer>> layerIterator = layers.iterator();
		for (int y = 0; layerIterator.hasNext(); y++) {
			Collection<Integer> layer = layerIterator.next();
			Iterator<Integer> nodeIterator = layer.iterator();
			for (int x = 0; nodeIterator.hasNext(); x++) {
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
		NodeInfo convertPosition(PositionConverter conversion) {
			return new NodeInfo(name, conversion.convert(position));
		}
	}

	record LinkInfo(Object upNode, Object downNode, double weight) {
	}

	record Infos(Map<Object, NodeInfo> ofNodes, Collection<LinkInfo> ofLinks) {
	}
}
