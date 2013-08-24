package parte.jphpd;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * A Java interface to the PHP execution daemon. Several methods are provided
 * to load configuration files; though this class will use all files provided,
 * the PHP daemon script that it spawns will only use the last file given before
 * it is started.
 *
 * @author nelson17
 */
public class Daemon {

	/**
	 * The location of the config file. This path must be an accessible location
	 * on the classpath.
	 */
	private static final String CONFIG_FILE = "@CONFIG_FILE@";
	
	/**
	 * The file that temporary output is placed in.
	 */
	private static final String PHPD_OUTPUT_FILE = "@PHPD_OUTPUT@";

	/**
	 * The number of milliseconds to wait for a shutdown.
	 */
    private static final long PHPD_STOP_TIMEOUT = 3000;

	/**
	 * Contains all the configuration options.
	 */
	private Map<String, String> config = new HashMap<String, String>();
	
	/**
	 * The last loaded config file.
	 */
	private String lastConfig = null;
    
    /**
     * Runs the daemon script.
     */
    private ScheduledExecutorService scriptExecutor;
    
    /**
     * THe daemon script.
     */
    private DaemonScript script;
    
    /**
     * The value of the last response that had one.
     */
    private String replyValue = null;

	/**
	 * Creates a new Daemon instance and loads settings from the default
	 * configuration file.
	 *
	 * @throws FileNotFoundException if the default configuration file could not
	 * be opened for reading.
	 * @throws IOException if an I/O error occurs while reading the default
	 * configuration file.
	 */
	public Daemon() throws FileNotFoundException, IOException {
		this(true);
	}

	/**
	 * Creates a new Daemon instance.
	 *
	 * @param useDefaultConfig Whether to immediately load settings from the
	 * default configuration file.
	 * @throws FileNotFoundException if the default configuration file is to be
	 * loaded and it cannot be opened for reading.
	 * @throws IOException if the default configuration file is to be loaded and
	 * an I/O error occurs while reading it.
	 */
	public Daemon(boolean useDefaultConfig) throws FileNotFoundException,
	IOException {
		if (useDefaultConfig) {
			loadConfig(Daemon.CONFIG_FILE);
		}
	}
	
	/**
	 * Starts running this Daemon.
	 * 
	 * @throws IOException if an I/O error occurs while starting it.
	 */
	public void start() throws IOException {
		if (isRunning()) {
			throw new DaemonUpException();
		}
		String cmd = getConfig("PARTE_PHP_CMD");
		String scr = getConfig("PARTE_PHPD_SCRIPT");
		String inSock = getConfig("PARTE_PHPD_IN_SOCKET_DOMAIN");
		String outSock = getConfig("PARTE_PHPD_OUT_SOCKET_DOMAIN");
		scriptExecutor = Executors.newScheduledThreadPool(2);
		script = new DaemonScript(cmd, scr, lastConfig, inSock,	outSock,
				Daemon.PHPD_OUTPUT_FILE);
		script.setExecutor(scriptExecutor);
		scriptExecutor.scheduleAtFixedRate(script, 0, 20, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Stops running this Daemon.
	 * 
	 * @throws IOException 
	 */
	public void stop() throws IOException {
		if (!isRunning()) {
			throw new DaemonDownException();
		}
		send(getConfig("PARTE_PHPD_CMD_END"));
		long start = System.currentTimeMillis();
		while (isRunning() &&
				System.currentTimeMillis() - start < PHPD_STOP_TIMEOUT) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		if (isRunning()) {
			throw new RuntimeException("Could not stop daemon");
		}
	}
	
	/**
	 * Sends PHP code to the daemon.
	 * 
	 * @param code The code to send. This must start with a PHP open tag if it
	 * is to be interpreted as code.
	 * @throws IOException 
	 * @return Whether the code was successfully interpreted. False indicates a
	 * syntax error.
	 */
	public boolean input(String code) throws IOException {
		return sendRequest(code, null, getConfig("PARTE_PHPD_REPLY_BAD_CODE"),
				null);
	}
	
	/**
	 * Sends a file to the daemon.
	 * 
	 * @param file The file that the daemon should read.
	 * @throws IOException 
	 * @return Whether the file was successfully read.
	 */
	public boolean read(String file) throws IOException {
		return sendRequest(getConfig("PARTE_PHPD_CMD_FILE"), file,
				getConfig("PARTE_PHPD_REPLY_BAD_FILE"), null);
	}
	
	/**
	 * Requests a variable's value from the daemon. If it is valid, its value
	 * may be retrieved by calling getReplyValue() immediately after this
	 * method.
	 * 
	 * @param name The name of the variable to get the value of.
	 * @throws IOException 
	 * @return Whether the variable is valid.
	 */
	public boolean var(String name) throws IOException {
		StringBuilder buf = new StringBuilder();
		String cmd = getConfig("PARTE_PHPD_CMD_VAR");
		String badReply = getConfig("PARTE_PHPD_REPLY_BAD_VAR");
		String goodReply = getConfig("PARTE_PHPD_REPLY_VAR");
		boolean s = sendRequest(cmd, name, badReply, buf);
		replyValue = buf.toString().replaceFirst(goodReply, "");
		return s;
	}
	
	/**
	 * Evaluates a string with the daemon. If it is valid, its evaluated value
	 * may be retrieved by calling getReplyValue() immediately after this
	 * method.
	 * 
	 * @param string The string to evaluate.
	 * @throws IOException 
	 * @return Whether the string is valid.
	 */
	public boolean parse(String string) throws IOException {
		StringBuilder buf = new StringBuilder();
		String cmd = getConfig("PARTE_PHPD_CMD_PARSE");
		String badReply = getConfig("PARTE_PHPD_REPLY_BAD_PARSE");
		String goodReply = getConfig("PARTE_PHPD_REPLY_PARSE");
		boolean s = sendRequest(cmd, string, badReply, buf);
		replyValue = buf.toString().replaceFirst(goodReply, "");
		return s;
	}
	
	/**
	 * Gets the output so far.
	 * 
	 * @return The output.
	 */
	public String dump() {
		return script.getOutput();
	}
	
	/**
	 * Gets the value of the last reply that had a value (var() or parse()).
	 * 
	 * @return The value.
	 */
	public String getReplyValue() {
		return replyValue;
	}
	
	/**
	 * Sends a message to the daemon that expects a reply that indicates whether
	 * it was successful.
	 * 
	 * @param command The primary command to send.
	 * @param arg The primary argument to the command. May be null for no args.
	 * @param badReply The reply that the daemon will send if the command was
	 * bad.
	 * @param buf A place to store the response. May be null if no response is
	 * needed.
	 * @return Whether it was successful.
	 * @throws IOException
	 */
	private boolean sendRequest(String command, String arg, String badReply,
			StringBuilder buf) throws IOException {
		if (!isRunning()) {
			throw new DaemonDownException();
		}
		String outSock = getConfig("PARTE_PHPD_OUT_SOCKET_DOMAIN");
		DaemonListener listener = new DaemonListener(outSock);
		listener.listen();
		String[] cmd = new String[arg != null ? 2 : 1];
		cmd[0] = command;
		if (arg != null) {
			cmd[1] = arg;
		}
		send(cmd);
		String output = null;
		try {
			output = listener.getOutput();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if (buf != null) {
			buf.append(output);
		}
		return !(output.equals(badReply));
	}
	
	/**
	 * Sends input to the phpd input socket.
	 * 
	 * @param input The messages to send.
	 * @throws IOException 
	 */
	private void send(String... input) throws IOException {
		StringBuilder buffer = new StringBuilder();
		for (String msg : input) {
			buffer.append(msg.length());
			buffer.append('\n');
			buffer.append(msg);
		}
		File socketFile = new File(getConfig("PARTE_PHPD_IN_SOCKET_DOMAIN"));
		AFUNIXSocket sock = AFUNIXSocket.newInstance();
		sock.connect(new AFUNIXSocketAddress(socketFile));
		PrintWriter pw = new PrintWriter(sock.getOutputStream());
		pw.write(buffer.toString());
		pw.flush();
		pw.close();
		sock.close();
	}
	
	/**
	 * Gets a configuration item.
	 * 
	 * @param name The name of the item.
	 * @return The value of the item.
	 * @throws IllegalStateException if the requested item does not exist.
	 */
	private String getConfig(String name) {
		if (config.containsKey(name)) {
			return config.get(name);
		} else {
			throw new IllegalStateException();
		}
	}
	
	/**
	 * Checks whether this Daemon is running.
	 * 
	 * @return Whether this Daemon is running.
	 */
	public boolean isRunning() {
		return (script != null && script.isRunning());
	}

	/**
	 * Loads settings from a configuration file. All settings are read from the
	 * given file and added to this Daemon's configuration. If a setting is
	 * loaded that already existed in the configuration, the old value is
	 * replaced with the newly loaded one.
	 *
	 * @param file The path to the configuration file.
	 * @throws FileNotFoundException if the given file could not be opened for
	 * reading.
	 * @throws IOException if an I/O error occurs while reading the file.
	 */
	public void loadConfig(String file) throws FileNotFoundException,
	IOException {
		FileReader fr = new FileReader(file);
		BufferedReader br = new BufferedReader(fr);
		try {
			parseConfigStream(br);
		} catch (IOException e) {
			throw e;
		} finally {
			try {
				br.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		lastConfig = file;
	}

	/**
	 * Parses settings from a character-input stream. All settings are read from
	 * stream wrapped by the given Reader and are added to the {@code config}
	 * map in this Daemon. If a setting is read that already existed in the
	 * {@code config} map, its value is replaced with the recently read value.
	 *
	 * @param stream A BufferedReader that wraps the stream to read from.
	 * @throws IOException if an I/O error occurs while reading the stream.
	 */
	private void parseConfigStream(BufferedReader stream) throws IOException {
		String line = null;
		while ((line = stream.readLine()) != null) {
			line = line.replaceFirst("\\s+#.*$", "");
			if (line.matches("^[A-Za-z_][A-Za-z0-9_]*=.*$")) {
				String[] parts = line.split("=", 2);
				String name = parts[0];
				String value = parts[1];
				config.put(name, value);
			}
		}
	}


}

