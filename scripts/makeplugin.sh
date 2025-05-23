#!/bin/sh
#
#  basic packaging up of a plugin
#
#  usage: makeplugin.sh plugindir
#
#  zzz 2010-02
#  zzz 2014-08 added support for su3 files
#

if [ -z "$I2P" -a -d "$PWD/../i2p.i2p/pkg-temp" ]; then
	export I2P=../i2p.i2p/pkg-temp
fi

if [ ! -d "$I2P" ]; then
	echo "Can't locate your I2P installation. Please add a environment variable named I2P with the path to the folder as value"
	echo "On OSX this solved with running: export I2P=/Applications/i2p if default install directory is used."
	exit 1
fi

PUBKEYDIR=$HOME/.i2p-plugin-keys
PUBKEYFILE=$PUBKEYDIR/plugin-public-signing.key
PRIVKEYFILE=$PUBKEYDIR/plugin-private-signing.key
B64KEYFILE=$PUBKEYDIR/plugin-public-signing.txt
PUBKEYSTORE=$PUBKEYDIR/plugin-su3-public-signing.crt
PRIVKEYSTORE=$PUBKEYDIR/plugin-su3-keystore.ks
KEYTYPE=RSA_SHA512_4096

PLUGINDIR=${1:-plugin}

PC=plugin.config
PCT=${PC}.tmp

if [ ! -d $PLUGINDIR ]
then
	echo "You must have a $PLUGINDIR directory"
	exit 1
fi

if [ ! -f $PLUGINDIR/$PC ]
then
	echo "You must have a $PLUGINDIR/$PC file"
	exit 1
fi

SIGNER=`grep '^signer=' $PLUGINDIR/$PC`
if [ "$?" -ne "0" ]
then
	echo "You must have a plugin name in $PC"
	echo 'For example name=foo'
	exit 1
fi
SIGNER=`echo $SIGNER | cut -f 2 -d '='`

if [ ! -f $PRIVKEYSTORE ]
then
	echo "Creating new SU3 $KEYTYPE keys for $SIGNER"
	java -cp $I2P/lib/i2p.jar net.i2p.crypto.SU3File keygen -t $KEYTYPE $PUBKEYSTORE $PRIVKEYSTORE $SIGNER || exit 1
	echo '*** Save your password in a safe place!!! ***'
	rm -rf logs/
	# copy to the router dir so verify will work
        CDIR=$I2P/certificates/plugin
	mkdir -p $CDIR || exit 1
	CFILE=$CDIR/`echo $SIGNER | sed s/@/_at_/`.crt
	cp $PUBKEYSTORE $CFILE
	chmod 444 $PUBKEYSTORE
	chmod 400 $PRIVKEYSTORE
	chmod 644 $CFILE
	echo "Created new SU3 keys: $PUBKEYSTORE $PRIVKEYSTORE"
	echo "Copied public key to $CFILE for testing"
fi

rm -f plugin.zip

OPWD=$PWD
cd $PLUGINDIR

grep -q '^name=' $PC
if [ "$?" -ne "0" ]
then
	echo "You must have a plugin name in $PC"
	echo 'For example name=foo'
	exit 1
fi

grep -q '^version=' $PC
if [ "$?" -ne "0" ]
then
	echo "You must have a version in $PC"
	echo 'For example version=0.1.2'
	exit 1
fi

# update the date
grep -v '^date=' $PC > $PCT
DATE=`date '+%s000'`
echo "date=$DATE" >> $PCT
mv $PCT $PC || exit 1

# add our Base64 key
grep -v '^key=' $PC > $PCT
B64KEY=`cat $B64KEYFILE`
echo "key=$B64KEY" >> $PCT || exit 1
mv $PCT $PC || exit 1

# zip it
zip -r $OPWD/plugin.zip * || exit 1

# get the version and use it for the sud header
VERSION=`grep '^version=' $PC | cut -f 2 -d '='`
# get the name and use it for the file name
NAME=`grep '^name=' $PC | cut -f 2 -d '='`
SU3=${NAME}.su3
cd $OPWD

# sign it
echo 'Signing. ...'
java -cp $I2P/lib/i2p.jar net.i2p.crypto.SU3File sign -c PLUGIN -t $KEYTYPE plugin.zip $SU3 $PRIVKEYSTORE $VERSION $SIGNER || exit 1
rm -f plugin.zip

# verify
echo "Verifying with $PUBKEYSTORE ..."
java -cp $I2P/lib/i2p.jar net.i2p.crypto.SU3File showversion $SU3 || exit 1
java -cp $I2P/lib/i2p.jar net.i2p.crypto.SU3File verifysig -k $PUBKEYSTORE $SU3 || exit 1
rm -rf logs/

echo 'Plugin files created: '
wc -c $SU3

exit 0
