package cl.camodev.wosbot.ex;

public class StopExecutionException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5590610329283682736L;

	public StopExecutionException(String message) {
		super(message);
	}
}