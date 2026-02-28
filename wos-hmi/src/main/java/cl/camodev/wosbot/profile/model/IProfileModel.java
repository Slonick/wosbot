package cl.camodev.wosbot.profile.model;

import java.util.List;

import cl.camodev.wosbot.ot.DTOProfiles;
import cl.camodev.wosbot.serv.IProfileStatusChangeListener;

public interface IProfileModel {

	public List<DTOProfiles> getProfiles();

	public boolean addProfile(DTOProfiles profile);

	public boolean saveProfile(DTOProfiles profile);
	public boolean deleteProfile(DTOProfiles profile);

	public boolean bulkUpdateProfiles(DTOProfiles templateProfile);

	public void addProfileStatusChangeListerner(IProfileStatusChangeListener listener);

}
