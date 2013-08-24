package parte.jphpd;

/**
 * Indicates that a problem occurred while trying to execute an external
 * command.
 */
class DaemonExecutionException extends RuntimeException {
	
    private static final long serialVersionUID = 3223990784204697483L;

	/**
	 * Creates a new DaemonExecutionException with the given cause.
	 *
	 * @param cause The Throwable that caused the exception to be thrown.
	 */
	public DaemonExecutionException(Throwable cause) {
		super(cause);
	}

}
