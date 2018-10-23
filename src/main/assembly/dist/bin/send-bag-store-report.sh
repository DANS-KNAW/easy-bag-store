#!/usr/bin/env bash
#
# Helper script to create and send a list of all DOIs stored in a given bag-store
#
# Usage: ./send-archived-datasets-report.sh <bag-store> <from-email> <to-email> [<bcc-email>]
#

BAGSTORES_BASEDIR=$1
DARK_HOST=$2
BAGSTORE=$3
FROM=$4
TO=$5
BCC=$6
TMPDIR=/tmp
DATE=$(date +%Y-%m-%d)
REPORT=$TMPDIR/$BAGSTORE-dois-$DATE.csv


if [ "$FROM" == "" ]; then
    FROM_EMAIl=""
else
    FROM_EMAIL="-r $FROM"
fi

if [ "$BCC" == "" ]; then
    BCC_EMAILS=""
else
    BCC_EMAILS="-b $BCC"
fi

TO_EMAILS="$TO"

exit_if_failed() {
    local EXITSTATUS=$?
    if [ $EXITSTATUS != 0 ]; then
        echo "ERROR: $1, exit status = $EXITSTATUS"
        echo "Report generation FAILED. Contact the system administrator." |
        mail -s "FAILED: Report: DOIs of datasets archived in bag-store $BAGSTORE" \
             $FROM_EMAIL $BCC_EMAILS $TO
        exit 1
    fi
    echo "OK"
}

echo -n "Creating list of DOIs in bag-store $BAGSTORE..."
find $BAGSTORES_BASEDIR/$BAGSTORE/ -name 'dataset.xml' | xargs cat | grep 'id-type:DOI' | sed -r 's/^.*>(.*)<.*$/\1/' > $REPORT
exit_if_failed "DOI list creation failed"

echo -n "Getting total disk usage of bag-store $BAGSTORE..."
DISK_USAGE=$(du -sh $BAGSTORES_BASEDIR/$BAGSTORE)
exit_if_failed "disk space usage report failed"

echo -n "Sending e-mail..."
echo -e "bag-store: $BAGSTORE\ndisk usage: $DISK_USAGE\nDOI list: see attached file" | \
mail -s "$DARK_HOST Report: status of bag-store: $BAGSTORE" -a $REPORT $BCC_EMAILS $FROM_EMAIL $TO_EMAILS
exit_if_failed "sending of e-mail failed"
