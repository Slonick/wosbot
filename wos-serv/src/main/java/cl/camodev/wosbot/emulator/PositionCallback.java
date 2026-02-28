package cl.camodev.wosbot.emulator;

@FunctionalInterface
public interface PositionCallback {
	void onPositionUpdate(Thread thread, int position);
}