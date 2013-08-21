package parte.jphpd;

/**
 * Indicates that a problem occured while trying to execute an external command.
 */
class DaemonExecutionException extends RuntimeException {

	/**
	 * Creates a new DaemonExecutionException with the given cause.
	 *
	 * @param cause The Throwable that caused the exception to be thrown.
	 */
	public DaemonExecutionException(Throwable cause) {
		super(cause);
	}

}
