package cl.camodev.wosbot.serv;

import cl.camodev.wosbot.ot.DTOBotState;

public interface IBotStateListener {

	public void onBotStateChange(DTOBotState botState);

}
