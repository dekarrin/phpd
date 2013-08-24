package parte.jphpd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Runs the PHP daemon script and redirects its output streams.
 */
class DaemonScript implements Runnable {

	/**
	 * The reader for the script's output stream.
	 */
	private BufferedReader outStream;

	/**
	 * The reader for the script's error stream.
	 */
	private BufferedReader errStream;

	/**
	 * The process running the script.
	 */
	private Process phpd;

	/**
	 * The executor that will run this DaemonScript.
	 */
	private ScheduledExecutorService executor = null;

	/**
	 * The output from running PHP.
	 */
	private StringBuilder output;

	/**
	 * Creates a new DaemonScript.
	 *
	 * @param php The path to the PHP command to use to run the daemon script.
	 * @param script The path to the daemon script.
	 * @param config The file that the script should use for configuration.
	 * @param inSocket The path to the input socket.
	 * @param outSocket The path to the output socket.
	 * @param phpdOutput The file to write output to.
	 * @throws IOException
	 */
	public DaemonScript(String php, String script, String config,
			String inSocket, String outSocket, String phpdOutput) throws
			IOException {
		String[] command = new String[9];
		command[0] = php;
		command[1] = script;
		command[2] = "-f";
		command[3] = "-c";
		command[4] = config;
		command[5] = "-i";
		command[6] = inSocket;
		command[7] = "-o";
		command[8] = outSocket;
		output = new StringBuilder();
		spawn(command);
	}

	/**
	 * Deletes the output so far.
	 *
	 * @return The output.
	 */
	public String getOutput() {
		String out = null;
		synchronized (this) {
			out = output.toString();
			output = new StringBuilder();
		}
		return out;
	}

	/**
	 * Checks whether the main process is running.
	 */
	public boolean isRunning() {
		try {
			phpd.exitValue();
			return true;
		} catch (IllegalThreadStateException e) {
			return false;
		}
	}

	/**
	 * Attempts to read the streams from the program, or starts it if it hasn't
	 * yet been started.
	 */
	@Override
	public void run() {
		if (isRunning()) {
			try {
				readStreams();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else if (executor != null) {
			executor.shutdown();
		}
	}

	/**
	 * Sets the executor for this DaemonScript so that it may be halted when the
	 * process stops running.
	 *
	 * @param executor
	 */
	public void setExecutor(ScheduledExecutorService executor) {
	    this.executor = executor;
    }

	/**
	 * Reads the streams from the program, redirecting them as appropriate.
	 *
	 * @throws IOException If an I/O error occurs while reading.
	 */
	private void readStreams() throws IOException {
		if (outStream.ready()) {
			synchronized (this) {
				output.append((char) outStream.read());
			}
		}
		if (errStream.ready()) {
			errStream.read(); // throw away the error stream
		}
	}

	/**
	 * Creates the sub-process to run the daemon script.
	 *
	 * @param command The subprocess command to use.
	 * @throws IOException
	 */
	private void spawn(String[] command) throws IOException {
		Runtime r = Runtime.getRuntime();
		phpd = r.exec(command);
		InputStreamReader isr = new InputStreamReader(phpd.getInputStream());
		InputStreamReader esr = new InputStreamReader(phpd.getErrorStream());
		errStream = new BufferedReader(esr);
		outStream = new BufferedReader(isr);
	}

}
