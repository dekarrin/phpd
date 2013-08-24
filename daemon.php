<?php

define('PARTE_PHPD_CONFIG_FILE', 'vars.cfg');

define('PARTE_PHPD_MODE_NORMAL', 0);
define('PARTE_PHPD_MODE_VAR', 1);
define('PARTE_PHPD_MODE_FILE', 2);
define('PARTE_PHPD_MODE_PARSE', 3);

function parte_phpd_read_config($filename) {
	$f = fopen($filename, 'r');
	while (($line = fgets($f)) !== false) {
		$line = rtrim($line, "\t\n\r\0\x0B");
		$line = preg_replace('/\s*#.*$/', '', $line);
		if (!empty($line) && substr($line, 0, 1) !== '#') {
			$parts = explode('=', $line, 2);
			$name = $parts[0];
			$val = $parts[1];
			define($name, $val);
		}
	}
}

function parte_phpd_sock_err($msg) {
	return "ERROR - ".$msg.socket_strerror(socket_last_error())."\n";
}

function parte_phpd_create() {
	if (($conn = @socket_create(AF_UNIX, SOCK_STREAM, 0)) === FALSE) {
		die(parte_phpd_sock_err("Could not create socket: "));
	}
	return $conn;
}

function parte_phpd_bind($conn, $domain) {
	if (!@socket_bind($conn, $domain)) {
		socket_close($conn);
		parte_phpd_write_stderr(parte_phpd_sock_err("Could not bind socket: "));
		die("Use -f option to force unlink socket stream");
	}
}

function parte_phpd_listen($conn) {
	if (!@socket_listen($conn)) {
		die(parte_phpd_sock_err("Could not listen on socket: "));
	}
}

function parte_phpd_connect($conn, $domain) {
	if (!@socket_connect($conn, $domain)) {
		parte_phpd_write_stderr(parte_phpd_sock_err("Could not connect to socket: "));
	}
}

function parte_phpd_read_msg($sock, &$msg) {
	if(($msg_length = @socket_read($sock, 64, PHP_NORMAL_READ)) === FALSE) {
		return 2;
	}
	if ($msg_length == "\n") {
		return 1;
	}
	if ($msg_length == '') {
		return 2;
	}
	$msg_length = trim($msg_length);
	if (!is_numeric($msg_length)) {
		return -1;
	}
	$msg = '';
	// msg_length + 1 to account for 
	if (!@socket_recv($sock, $msg, $msg_length, MSG_WAITALL)) {
		return -2;
	}
	return 0;
}

function parte_phpd_check_args($argv, $argc) {
	global $_PARTE_PHPD;
	if ($argc > 1) {
		for ($i = 1; $i < $argc; $i++) {
			if ($argv[$i] == '-h') {
				echo "Options:\n";
				echo "-h:  this help\n";
				echo "-f:  force unlink/creation of socket domain\n";
				echo "-v:  verbose mode\n";
				die();
			} else if ($argv[$i] == '-f') {
				$_PARTE_PHPD['force_unlink'] = true;
			} else if ($argv[$i] == '-v') {
				$_PARTE_PHPD['verbose'] = true;
			} else {
				echo "Unrecognized option/argument '" . $argv[1] . "'. Use -h for help\n";
			}
		}
	}
}

function parte_phpd_verbose($output, $non_verbose_output = NULL) {
	global $_PARTE_PHPD;
	if ($_PARTE_PHPD['verbose']) {
		echo $output;
	} else if (!is_null($non_verbose_output)) {
		echo $non_verbose_output;
	}
}

function parte_phpd_write_out($output) {
	if (file_exists(PARTE_PHPD_OUT_SOCKET_DOMAIN)) {
		$out = parte_phpd_create();
		parte_phpd_connect($out, PARTE_PHPD_OUT_SOCKET_DOMAIN);
		if (@socket_send($out, $output, strlen($output), MSG_EOF) === false) {
			parte_phpd_write_stderr(parte_phpd_sock_err("Could not send reply: "));
		}
		socket_close($out);
	} else {
		parte_phpd_write_stderr("Could not write out: socket domain does not exist");
	}
}

function parte_phpd_write_err($output) {
	parte_phpd_write_stderr("ERROR - " . $output . "\n");
	parte_phpd_write_out($output);
}

function parte_phpd_write_stderr($msg) {
	$f = fopen('php://stderr', 'a');
	fwrite($f, $msg);
	fclose($f);
}

$_PARTE_PHPD = array('force_unlink' => false, 'verbose' => false);

parte_phpd_read_config(PARTE_PHPD_CONFIG_FILE);
parte_phpd_check_args($argv, $argc);

if ($_PARTE_PHPD['force_unlink']) {
	$_PARTE_PHPD['fexists'] = file_exists(PARTE_PHPD_IN_SOCKET_DOMAIN);
	@unlink(PARTE_PHPD_IN_SOCKET_DOMAIN);
	if ($_PARTE_PHPD['fexists']) {
		parte_phpd_verbose('unlinked existing socket domain' . "\n");
	} else {
		parte_phpd_verbose('socket domain did not exist; did not require unlinking' . "\n");
	}
}
$_PARTE_PHPD['in_conn'] = parte_phpd_create();
parte_phpd_bind($_PARTE_PHPD['in_conn'], PARTE_PHPD_IN_SOCKET_DOMAIN);
parte_phpd_listen($_PARTE_PHPD['in_conn']);
$_PARTE_PHPD['keep_open'] = true;
parte_phpd_verbose('listening for connections on ' . PARTE_PHPD_IN_SOCKET_DOMAIN . "\n", PARTE_PHPD_IN_SOCKET_DOMAIN . "\n");
while ($_PARTE_PHPD['keep_open']) {
	$_PARTE_PHPD['sock'] = socket_accept($_PARTE_PHPD['in_conn']);
	parte_phpd_verbose('connection established' . "\n");
	$_PARTE_PHPD['mode'] = PARTE_PHPD_MODE_NORMAL;
	$_PARTE_PHPD['msg'] = '';
	$_PARTE_PHPD['connected'] = true;
	while ($_PARTE_PHPD['connected']) {
		$_PARTE_PHPD['status'] = parte_phpd_read_msg($_PARTE_PHPD['sock'], $_PARTE_PHPD['msg']);
		if ($_PARTE_PHPD['status'] == -1) {
			parte_phpd_write_err(PARTE_PHPD_REPLY_BAD_MSG_LEN);
			socket_close($_PARTE_PHPD['sock']);
			$_PARTE_PHPD['connected'] = false;
		} else if ($_PARTE_PHPD['status'] == -2) {
			parte_phpd_write_err(PARTE_PHPD_REPLY_BAD_MSG);
			socket_close($_PARTE_PHPD['sock']);
			$_PARTE_PHPD['connected'] = false;
		} else if ($_PARTE_PHPD['status'] == 1) {
			parte_phpd_write_out(PARTE_PHPD_REPLY_CLOSE);
			socket_close($_PARTE_PHPD['sock']);
			$_PARTE_PHPD['connected'] = false;
		} else if ($_PARTE_PHPD['status'] == 2) {
			parte_phpd_verbose('socket closed by client' . "\n");
			socket_close($_PARTE_PHPD['sock']);
			$_PARTE_PHPD['connected'] = false;
		} else if ($_PARTE_PHPD['status'] == 0) {
			parte_phpd_verbose('received "' . $_PARTE_PHPD['msg'] . '"' . "\n");
			switch ($_PARTE_PHPD['mode']) {
			case PARTE_PHPD_MODE_NORMAL:
				if ($_PARTE_PHPD['msg'] == PARTE_PHPD_CMD_END) {
					$_PARTE_PHPD['keep_open'] = false;
					socket_close($_PARTE_PHPD['sock']);
					$_PARTE_PHPD['connected'] = false;
				} else if ($_PARTE_PHPD['msg'] == PARTE_PHPD_CMD_VAR) {
					$_PARTE_PHPD['mode'] = PARTE_PHPD_MODE_VAR;
				} else if ($_PARTE_PHPD['msg'] == PARTE_PHPD_CMD_FILE) {
					$_PARTE_PHPD['mode'] = PARTE_PHPD_MODE_FILE;
				} else if ($_PARTE_PHPD['msg'] == PARTE_PHPD_CMD_PARSE) {
					$_PARTE_PHPD['mode'] = PARTE_PHPD_MODE_PARSE;
				} else {
					$_PARTE_PHPD['good_code'] = @eval('?'.">{$_PARTE_PHPD['msg']}");
					if ($_PARTE_PHPD['good_code'] === false) {
						parte_phpd_write_err(PARTE_PHPD_REPLY_BAD_CODE);
					} else {
						parte_phpd_write_out(PARTE_PHPD_REPLY_CODE);
					}
				}
				break;

			case PARTE_PHPD_MODE_VAR:
				if (array_key_exists($_PARTE_PHPD['msg'], get_defined_vars())) {
					$_PARTE_PHPD['full_msg'] = PARTE_PHPD_REPLY_VAR . $$_PARTE_PHPD['msg'];
					parte_phpd_write_out($_PARTE_PHPD['full_msg']);
				} else {
					parte_phpd_write_err(PARTE_PHPD_REPLY_BAD_VAR);
				}
				$_PARTE_PHPD['mode'] = PARTE_PHPD_MODE_NORMAL;
				break;

			case PARTE_PHPD_MODE_FILE:
				if (file_exists($_PARTE_PHPD['msg'])) {
					require $_PARTE_PHPD['msg'];
					parte_phpd_write_out(PARTE_PHPD_REPLY_FILE);
				} else {
					parte_phpd_write_err(PARTE_PHPD_REPLY_BAD_FILE);
				}
				$_PARTE_PHPD['mode'] = PARTE_PHPD_MODE_NORMAL;
				break;

			case PARTE_PHPD_MODE_PARSE:
				$_PARTE_PHPD['good_code'] = @eval('$_PARTE_PHPD[\'parsed\'] = '.$_PARTE_PHPD['msg'].';');
				if ($_PARTE_PHPD['good_code'] === false) {
					parte_phpd_write_err(PARTE_PHPD_REPLY_BAD_PARSE);
				} else {
					parte_phpd_write_out(PARTE_PHPD_REPLY_PARSE . $_PARTE_PHPD['parsed']);
				}
				break;
			}
		}
	}
	parte_phpd_verbose('connection closed' . "\n");
}
parte_phpd_verbose('shutting down' . "\n");
@unlink(PARTE_PHPD_IN_SOCKET_DOMAIN);
?>
