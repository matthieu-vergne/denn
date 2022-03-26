package ia.agent;

import ia.agent.adn.Chromosome;
import ia.terrain.Move;
import ia.terrain.Position;

public interface Brain {

	public Move decideMoveFrom(Position position);

	public Chromosome chromosome();

}
