package cl.camodev.wosbot.console.list;

import cl.camodev.wosbot.ot.DTOLogMessage;

public interface ILogListener {

	void onLogReceived(DTOLogMessage message);

}
