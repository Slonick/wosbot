package cl.camodev.wosbot.ot;

public class DTOPoint {
	private int x;
	private int y;

	public DTOPoint(int x, int y) {
		this.x = x;
		this.y = y;
	}

	public DTOPoint(DTOPoint other) {
	this.x = other.x;
	this.y = other.y;
	}
	
	public int getX() {
		return x;
	}

	public int addX(int offset) {
		this.x += offset;
		return this.x;
	}

	public int addY(int offset) {
		this.y += offset;
		return this.y;
	}
	
	public int getY() {
		return y;
	}

	@Override
	public String toString() {
		return "DTOPoint [x=" + x + ", y=" + y + "]";
	}
}
