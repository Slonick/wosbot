package cl.camodev.wosbot.console.controller;

import cl.camodev.wosbot.console.list.ILogListener;
import cl.camodev.wosbot.console.view.ConsoleLogLayoutController;
import cl.camodev.wosbot.ot.DTOLogMessage;
import cl.camodev.wosbot.serv.impl.ServLogs;

public class ConsoleLogActionController implements ILogListener {

	private ConsoleLogLayoutController layoutController;

	public ConsoleLogActionController(ConsoleLogLayoutController controller) {
		this.layoutController = controller;
		ServLogs.getServices().setLogListener(this);
	}

	@Override
	public void onLogReceived(DTOLogMessage message) {
		layoutController.appendMessage(message);
	}

}
