package parte.jphpd;

/**
 * Raised when the daemon is running when a method is called that requires it to
 * not be running.
 */
public class DaemonUpException extends IllegalStateException {
	
	/**
	 * Creates a new DaemonUpException.
	 */
	public DaemonUpException() {
		super("daemon is already running");
	}

}
