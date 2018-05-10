package popp1594;

import java.awt.Color;

import popp1594.Vertex;
import spacesettlers.graphics.LineGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.utilities.Vector2D;

/*
 * This is part of the professors Astar program she provided for us. It was not modified
 * This class stores the edges between vertices in the Astar path that is calculated.
 */
public class Edge {
	LineGraphics graphic;
	Vertex vertex1, vertex2;
	double pathCost;
	boolean isSolution;
	
	public Edge(Vertex vertex1, Vertex vertex2, Vector2D lineVec) {
		this.vertex1 = vertex1;
		this.vertex2 = vertex2;
		vertex1.addEdge(this);
		vertex2.addEdge(this);
		pathCost = lineVec.getMagnitude();
		graphic = new LineGraphics(vertex1.getPosition(), vertex2.getPosition(), lineVec); 
		graphic.setLineColor(Color.RED);
	}
	
	public SpacewarGraphics getGraphic() {
		return graphic;
	}
	
	public double getPathCost() {
		return pathCost;
	}
	
	public Vertex getVertex1() {
		return vertex1;
	}
	
	public Vertex getVertex2() {
		return vertex2;
	}
	
	public void setSolution() {
		isSolution = true;
		graphic.setLineColor(Color.YELLOW);
		graphic.setStrokeWidth(4);
	}
}
