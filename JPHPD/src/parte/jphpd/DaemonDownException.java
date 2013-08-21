package parte.jphpd;

/**
 * Raised when the daemon is not running when a method is called that requires
 * it to be running.
 */
public class DaemonDownException extends IllegalStateException {
	
	/**
	 * Creates a new DaemonDownException.
	 */
	public DaemonDownException() {
		super("daemon is not running");
	}

}
