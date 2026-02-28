package cl.camodev.wosbot.ot;

public class DTOImageSearchResult {
	private boolean found;
	private DTOPoint point;
	private double matchPercentage;

	public DTOImageSearchResult(boolean found, DTOPoint point, double matchPercentage) {
		this.found = found;
		this.point = point;
		this.matchPercentage = matchPercentage;
	}

	// Getters y setters

	public boolean isFound() {
		return found;
	}

	public void setFound(boolean found) {
		this.found = found;
	}

	public DTOPoint getPoint() {
		return point;
	}

	public void setPoint(DTOPoint point) {
		this.point = point;
	}

	public double getMatchPercentage() {
		return matchPercentage;
	}

	public void setMatchPercentage(double matchPercentage) {
		this.matchPercentage = matchPercentage;
	}
}
