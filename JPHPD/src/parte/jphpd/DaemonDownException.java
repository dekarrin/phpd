package parte.jphpd;

/**
 * Raised when the daemon is not running when a method is called that requires
 * it to be running.
 */
public class DaemonDownException extends IllegalStateException {

    private static final long serialVersionUID = -2247451395914070493L;

	/**
	 * Creates a new DaemonDownException.
	 */
	public DaemonDownException() {
		super("daemon is not running");
	}

}
