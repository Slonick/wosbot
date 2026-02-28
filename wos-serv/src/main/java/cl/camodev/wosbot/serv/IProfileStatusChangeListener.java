package cl.camodev.wosbot.serv;

import cl.camodev.wosbot.ot.DTOProfileStatus;

public interface IProfileStatusChangeListener {

	public void onProfileStatusChange(DTOProfileStatus status);
}
