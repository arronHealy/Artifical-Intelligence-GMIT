package ie.gmit.sw.ai.search;

import org.jsoup.nodes.Document;

public class DocumentNode {
	
	private Document doc;
	
	private double score;
	
	public DocumentNode(Document d, double s) {
		this.doc = d;
		this.score = s;
	}

	public Document getDoc() {
		return doc;
	}

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

}
