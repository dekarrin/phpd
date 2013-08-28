package parte.jphpd;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

import org.newsclub.net.unix.AFUNIXServerSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * Listens for output from the Daemon.
 */
class DaemonListener {

	/**
	 * Handles the actual listening.
	 */
	private class ListenerRunner implements Runnable {

		@Override
		public void run() {
			try {
				Socket sock = server.accept();
				InputStreamReader is = null;
				is = new InputStreamReader(sock.getInputStream());
				int c = 0;
				try {
					while ((c = is.read()) != -1) {
						output.append((char) c);
					}
				} catch (IOException e) {
					throw e;
				} finally {
					is.close();
					sock.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			runner = null;
		}
	}

	/**
	 * Runs the listening in its own thread.
	 */
	private ListenerRunner runner = null;

	/**
	 * The socket that phpd is being listened to on.
	 */
	private AFUNIXServerSocket server;

	/**
	 * Holds output from phpd.
	 */
	private StringBuilder output;

	/**
	 * Creates a new DaemonListener and binds it to the given output socket.
	 *
	 * @param outSock The path to the output socket for the daemon.
	 * @throws IOException
	 */
	public DaemonListener(String outSock) throws IOException {
		server = AFUNIXServerSocket.newInstance();
		server.bind(new AFUNIXSocketAddress(new File(outSock)));
	}

	/**
	 * Waits for the runner to finish listening for output, then returns the
	 * output. Blocks until output is ready. After this method is called, this
	 * DaemonListener will be unusable.
	 *
	 * @return The output.
	 * @throws IOException 
	 */
	public String getOutput() throws InterruptedException, IOException {
		while (runner != null) {
			Thread.sleep(20);
		}
		server.close();
		return output.toString();
	}

	/**
	 * Begins listening to the daemon.
	 */
	public void listen() {
		output = new StringBuilder();
		runner = new ListenerRunner();
		(new Thread(runner, "DaemonListenerThread")).start();
	}
}
