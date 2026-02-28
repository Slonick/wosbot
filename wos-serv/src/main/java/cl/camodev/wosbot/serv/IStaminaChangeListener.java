package cl.camodev.wosbot.serv;

/**
 * Interface for listening to stamina changes.
 */
public interface IStaminaChangeListener {

    /**
     * Called when stamina changes for a profile.
     *
     * @param profileId the profile ID
     * @param newStamina the new stamina value
     */
    void onStaminaChanged(Long profileId, int newStamina);
}

