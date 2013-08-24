package parte.jphpd;

/**
 * Raised when the daemon needs a configuration option that is not availible.
 */
public class DaemonConfigurationException extends IllegalStateException {

    private static final long serialVersionUID = -5404189145551506761L;

	/**
	 * Creates a new DaemonConfigurationException.
	 * 
	 * @param name The name of the config option that doesn't exist.
	 */
	public DaemonConfigurationException(String name) {
		super("Configuration item '" + name + "' has not been loaded");
	}

}
