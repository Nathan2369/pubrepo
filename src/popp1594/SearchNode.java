package popp1594;

import popp1594.Edge;
import popp1594.Vertex;

/**
 * Professors Astar class. It stores the nodes of the Astar program. It was not modified.
 * @author amy
 *
 */
public class SearchNode {
	Vertex vertex;
	Edge edge;
	
	public SearchNode(popp1594.Vertex start, Edge e) {
		vertex = start;
		edge = e;
	}

	public Edge getEdge() {
		return edge;
	}

	public Vertex getVertex() {
		return vertex;
	}
	
	

}
