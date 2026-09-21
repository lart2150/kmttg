#!/bin/sh
# Captures TiVo test fixtures for kmttg. Put this and kmttg-fixture-capture.jar in
# your kmttg folder - the one holding config.ini - and run it from there.
DIRNAME=`dirname "$0"`

if ! command -v java >/dev/null 2>&1; then
    echo "Java was not found."
    echo
    echo "This needs Java 11 or newer, the same as kmttg itself. If kmttg runs on"
    echo "this machine, run this from the folder kmttg is installed in."
    exit 1
fi

exec java -Djava.net.preferIPv4Stack=true -jar "$DIRNAME/kmttg-fixture-capture.jar" "$@"
