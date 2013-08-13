#!/bin/bash

# Handles launching and controlling a PHP instance that is kept running while waiting for input

# Change this to the command to execute PHP
PHPD_CMD="php5"
PHPD_SCRIPT="daemon.php"
PHPD_OPTS="-f"

CONFIG_FILE="vars.cfg"

# Do not change below this line (unless you need to)

PID=

PID_FILE=/tmp/phpd.pid
PHPD_OUTPUT=/tmp/phpd.out
TEMP_OUTPUT=/tmp/phpd_sock.out

show_help ()
{
	echo "Syntax: $1 <cmd> <cmd-params>"
	echo
	echo "Valid commands:"
	echo "start"
	echo "stop"
	echo "send-end"
	echo "input <code>"
	echo "file <filename>"
	echo "var <var name>"
	echo "dump"
}

start_daemon ()
{
	if [ -e "$PID_FILE" ]
	then
		echo "phpd already appears to be running"
		echo "To stop it, do "'`'"$1 stop"'`'
	else
		{
			"$PHPD_CMD" "$DIR"/"$PHPD_SCRIPT" "$PHPD_OPTS" >> "$PHPD_OUTPUT" 2> /dev/null
			rm "$PID_FILE" "$PHPD_OUTPUT";
		} &
		echo $! > "$PID_FILE"
		echo "Started successfully."
	fi
}

show_not_running ()
{
	echo "phpd does not appear to be running"
	echo "To start it, do "'`'"$1 start"'`'
}

stop_daemon ()
{
	if [ ! -e "$PID_FILE" ]
	then
		show_not_running $1
	else
		"$DIR"/send.sh "$PARTE_PHPD_CMD_END"
		while [ -e "$PID_FILE" ]
		do
			sleep 1
		done
		echo "Stopped successfully."
	fi
}

send_input ()
{
	echo "$2"
	if [ ! -e "$PID_FILE" ]
	then
		show_not_running $1
	else
		nc -Ul $PARTE_PHPD_OUT_SOCKET_DOMAIN > "$TEMP_OUTPUT" &
		"$DIR"/send.sh "$2"
		wait $!
		local RESPONSE=$(cat "$TEMP_OUTPUT")
		if [ "$RESPONSE" = "$PARTE_PHPD_REPLY_BAD_CODE" ]
		then
			echo "Syntax error in given code"
		else
			echo "Executed PHP code"
		fi
		rm "$TEMP_OUTPUT"
	fi
}

get_var ()
{
	if [ ! -e "$PID_FILE" ]
	then
		show_not_running $1
	else
		nc -Ul $PARTE_PHPD_OUT_SOCKET_DOMAIN > "$TEMP_OUTPUT" &
		"$DIR"/send.sh $PARTE_PHPD_CMD_VAR "$2"
		wait $!
		local RESPONSE=$(cat "$TEMP_OUTPUT")
		if [ "$RESPONSE" = "$PARTE_PHPD_REPLY_BAD_VAR" ]
		then
			echo "Invalid variable name" >&2
		else
			VAL=$(echo $RESPONSE | sed s/^$PARTE_PHPD_REPLY_VAR//g)
			echo $VAL
		fi
		rm "$TEMP_OUTPUT"
	fi
}

send_file ()
{
	if [ ! -e "$PID_FILE" ]
	then
		show_not_running $1
	else
		nc -Ul $PARTE_PHPD_OUT_SOCKET_DOMAIN > "$TEMP_OUTPUT" &
		"$DIR"/send.sh $PARTE_PHPD_CMD_FILE "$2"
		wait $!
		local RESPONSE=$(cat "$TEMP_OUTPUT")
		if [ "$RESPONSE" = "$PARTE_PHPD_REPLY_BAD_FILE" ]
		then
			echo "Could not execute file"
		else
			echo "Executed file"
		fi
		rm "$TEMP_OUTPUT"
	fi
}

dump_output ()
{
	if [ ! -e "$PID_FILE" ]
	then
		show_not_running $1
	else
		local PHP_OUTPUT_TMP="$PHPD_OUTPUT"_tmp
		mv "$PHPD_OUTPUT" "$PHPD_OUTPUT_TMP"
		cat "$PHPD_OUTPUT_TMP"
		rm "$PHPD_OUTPUT_TMP"
		touch "$PHPD_OUTPUT"
	fi
}

DIR=$(dirname $(readlink -f "$0"))

. "$DIR"/config.sh
parse_config "$DIR"/"$CONFIG_FILE"

case "$1" in
start)
	start_daemon "$0"
	;;

stop)
	stop_daemon "$0"
	;;

input)
	send_input "$0" "$2"
	;;

file)
	send_file "$0" "$2"
	;;

var)
	get_var "$0" "$2"
	;;

send-end)
	"$DIR"/send.sh "$PARTE_PHPD_CMD_END"
	echo "Sent end command to daemon."
	;;

dump)
	dump_output "$0"
	;;

*)
	show_help "$0"
	;;
esac
