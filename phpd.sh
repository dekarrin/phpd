#!/bin/bash

# Handles launching and controlling a PHP instance that is kept running while waiting for input

CONFIG_FILE="vars.cfg"

# Do not change below this line (unless you need to)

PID=

PID_FILE=/tmp/phpd.pid
PHPD_OUTPUT=/tmp/phpd.out
TEMP_OUTPUT=/tmp/phpd_sock.out

PHPD_STOP_TIMEOUT=3

DIR=$(dirname $(readlink -f "$0"))

EXIT_STATUS=0

. "$DIR"/config.sh
parse_config "$DIR"/"$CONFIG_FILE"

case "$1" in
start)
	if [ -e "$PID_FILE" ]
	then
		echo "phpd already appears to be running"
		echo "To stop it, do "'`'"$0 stop"'`'
		EXIT_STATUS=1
	else
		{
			"$PARTE_PHP_CMD" "$DIR"/"$PARTE_PHPD_SCRIPT" "$PARTE_PHPD_OPTS" >> "$PHPD_OUTPUT" 2> /dev/null
			rm "$PID_FILE" "$PHPD_OUTPUT";
		} &
		echo $! > "$PID_FILE"
		echo "Started successfully."
	fi
	;;

stop)
	if [ ! -e "$PID_FILE" ]
	then
		echo "phpd does not appear to be running"
		echo "To start it, do "'`'"$0 start"'`'
		EXIT_STATUS=1
	else
		"$DIR"/send.sh "$PARTE_PHPD_CMD_END"
		tries=0
		while [[ -e "$PID_FILE" && "$tries" -lt "$PHPD_STOP_TIMEOUT" ]]
		do
			sleep 1
			((tries += 1))
		done
		if [ -e "$PID_FILE" ]
		then
			echo "Could not stop daemon"
			EXIT_STATUS=2
		else
			echo "Stopped successfully."
		fi
	fi
	;;

input)
	if [ ! -e "$PID_FILE" ]
	then
		echo "phpd does not appear to be running"
		echo "To start it, do "'`'"$0 start"'`'
		EXIT_STATUS=1
	else
		nc -Ul $PARTE_PHPD_OUT_SOCKET_DOMAIN > "$TEMP_OUTPUT" &
		"$DIR"/send.sh "$2"
		wait $!
		rm "$PARTE_PHPD_OUT_SOCKET_DOMAIN"
		local RESPONSE=$(cat "$TEMP_OUTPUT")
		if [ "$RESPONSE" = "$PARTE_PHPD_REPLY_BAD_CODE" ]
		then
			echo "Syntax error in given code"
			EXIT_STATUS=2
		else
			echo "Executed PHP code"
		fi
		rm "$TEMP_OUTPUT"
	fi
	;;

read)
	if [ ! -e "$PID_FILE" ]
	then
		echo "phpd does not appear to be running"
		echo "To start it, do "'`'"$0 start"'`'
		EXIT_STATUS=1
	else
		nc -Ul $PARTE_PHPD_OUT_SOCKET_DOMAIN > "$TEMP_OUTPUT" &
		"$DIR"/send.sh $PARTE_PHPD_CMD_FILE "$2"
		wait $!
		rm "$PARTE_PHPD_OUT_SOCKET_DOMAIN"
		local RESPONSE=$(cat "$TEMP_OUTPUT")
		if [ "$RESPONSE" = "$PARTE_PHPD_REPLY_BAD_FILE" ]
		then
			echo "Could not execute file"
			EXIT_STATUS=2
		else
			echo "Executed file"
		fi
		rm "$TEMP_OUTPUT"
	fi
	;;

var)
	if [ ! -e "$PID_FILE" ]
	then
		echo "phpd does not appear to be running"
		echo "To start it, do "'`'"$0 start"'`'
		EXIT_STATUS=1
	else
		nc -Ul $PARTE_PHPD_OUT_SOCKET_DOMAIN > "$TEMP_OUTPUT" &
		"$DIR"/send.sh $PARTE_PHPD_CMD_VAR "$2"
		wait $!
		rm "$PARTE_PHPD_OUT_SOCKET_DOMAIN"
		local RESPONSE=$(cat "$TEMP_OUTPUT")
		if [ "$RESPONSE" = "$PARTE_PHPD_REPLY_BAD_VAR" ]
		then
			echo "Invalid variable name" >&2
			EXIT_STATUS=2
		else
			echo $RESPONSE | sed s/^$PARTE_PHPD_REPLY_VAR//g
		fi
		rm "$TEMP_OUTPUT"
	fi
	;;

parse)
	if [ ! -e "$PID_FILE" ]
	then
		echo "phpd does not appeaar to be running"
		echo "To start it, do "'`'"$0 start"'`'
		EXIT_STATUS=1
	else
		nc -U1 $PARTE_PHPD_OUT_SOCKET_DOMAIN > "$TEMP_OUTPUT" &
		"$DIR"/send.sh $PARTE_PHPD_CMD_PARSE "$2"
		wait $!
		rm "$PARTE_PHPD_OUT_SOCKET_DOMAIN"
		local RESPONSE=$(cat "$TEMP_OUTPUT")
		if [ "$RESPONSE" = "$PARTE_PHPD_REPLY_BAD_PARSE" ]
		then
			echo "Invalid syntax" >&2
			EXIT_STATUS=2
		else
			echo $RESPONSE | sed s/^$PARTE_PHPD_REPLY_PARSE//g
		fi
		rm "$TEMP_OUTPUT"
	fi
	;;

send-end)
	"$DIR"/send.sh "$PARTE_PHPD_CMD_END"
	echo "Sent end command to daemon."
	;;

dump)
	if [ ! -e "$PID_FILE" ]
	then
		echo "phpd does not appear to be running"
		echo "To start it, do "'`'"$0 start"'`'
		exit EXIT_STATUS=1
	else
		local PHPD_OUTPUT_TMP="$PHPD_OUTPUT"_tmp
		touch "$PHPD_OUTPUT"
		mv "$PHPD_OUTPUT" "$PHPD_OUTPUT_TMP"
		cat "$PHPD_OUTPUT_TMP"
		rm "$PHPD_OUTPUT_TMP"
		touch "$PHPD_OUTPUT"
	fi
	;;

status)
	if [ -e "$PID_FILE" ]
	then
		echo "Status: up"
		echo "PID: $(cat $PID_FILE)"
	else
		echo "Status: down"
	fi
	;;

-h|help|*)
	if [ -z "$2" ]
	then
		echo "Syntax: $0 <cmd> <cmd-params>"
		echo
		echo "Valid commands:"
		echo "start"
		echo "stop"
		echo "status"
		echo "parse <string>"
		echo "input <code>"
		echo "read <filename>"
		echo "var <var name>"
		echo "dump"
		echo
		echo "For help with a command, type \`$0 help <command>'"
	else
		case "$2" in
		start)
			echo "Syntax: $0 start"
			echo
			echo "Starts the PHP executor daemon. This command will fail if the daemon is"
			echo "already running."
			echo
			echo "Exits with status 0 if the daemon was successfully started."
			echo "Exits with status 1 if the daemon is already running."
			;;

		stop)
			echo "Syntax: $0 stop"
			echo
			echo "Stops the PHP executor daemon. This command will fail if the daemon is"
			echo "not yet running."
			echo
			echo "Exits with status 0 if the daemon was successfully stopped."
			echo "Exits with status 1 if the daemon is not yet running."
			echo "Exits with status 2 if the daemon did not stop after $PHPD_STOP_TIMEOUT seconds."
			;;

		input)
			echo "Syntax: $0 input <code>"
			echo
			echo "Passes PHP code to the executor daemon. The code is executed and the"
			echo "output (if any) is sent to a temporary buffer. The output can be"
			echo "retrieved by using the dump command."
			echo
			echo "This command will fail if the daemon is not yet running."
			echo
			echo "Parameter: code"
			echo "The PHP code to send to the executor. The code is interpreted as if it"
			echo "were at the beginning of a PHP source code file, so PHP sections must"
			echo "begin with a PHP open tag, such as <?php."
			echo
			echo "Exits with status 0 if the code was successfully executed."
			echo "Exits with status 1 if the daemon is not running."
			echo "Exits with status 2 if there is a syntax error in the given code."
			;;

		parse)
			echo "Syntax: $0 parse <string>"
			echo
			echo "Parses a PHP string."
			echo
			echo "Parameter: string"
			echo "The PHP string to parse."
			echo
			echo "Exits with status 0 if the string was successfully parsed."
			echo "Exits with status 1 if the daemon is not running."
			echo "Exits with status 2 if the given string is not valid."
			;;

		read)
			echo "Syntax: $0 read <filename>"
			echo
			echo "Passes the contents of a file to the executor daemon. The file is"
			echo "executed and the output (if any) is sent to a temporary buffer. The"
			echo "output can be retrieved by using the dump command."
			echo
			echo "Parameter: filename"
			echo "The file to execute."
			echo
			echo "Exits with status 0 if the code was successfully executed."
			echo "Exits with status 1 if the daemon is not running."
			echo "Exits with status 2 if the given file could not be executed."
			;;

		var)
			echo "Syntax: $0 var <var name>"
			echo
			echo "Gets the current value of a variable. This command will fail if the"
			echo "daemon is not yet running."
			echo
			echo "Parameter: var name"
			echo "The name of the variable to get the value of. This must be a variable"
			echo "that exists in the global scope of the code that has been executed."
			echo
			echo "Exits with status 0 if the code was successfully executed."
			echo "Exits with status 1 if the daemon is not running."
			echo "Exits with status 2 if the given variable name is invalid."
			;;

		dump)
			echo "Syntax: $0 dump"
			echo
			echo "Outputs and then truncates the stored output from PHP. This command"
			echo "will fail if the daemon is not yet running."
			echo
			echo "Exits with status 0 if the current output is successfully dumped to"
			echo "stdout."
			echo "Exits with status 1 if the daemon is not running."
			;;

		status)
			echo "Syntax: $0 status"
			echo
			echo "Checks whether the daemon is up or down. If it is up, outputs the PID"
			echo "of the subshell that is running the daemon."
			echo
			echo "Exits with status 0."
			;;

		help)
			echo "Syntax: $0 help [command]"
			echo
			echo "Outputs command help. If a command is specified, outputs information"
			echo "about that command."
			echo
			echo "Exits with status 0."
			;;

		*)
			echo "'$2' is not a valid command. Type \`$0 -h' for a list of commands."
			;;

		esac
	fi
	;;
esac

exit $EXIT_STATUS
