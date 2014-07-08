#/bin/sh
# cat src/java/net/djarvur/vmbix/VmBix.java |grep "Pattern p[A-Z]"|cut -d "*" -f 3|sed -E 's/\\//g;s/(.*)\(.*\)(.*)"(.*)/\1name\2/g'
file="src/java/net/djarvur/vmbix/VmBix.java"
cat $file|grep "Pattern p[A-Z]"|cut -d "*" -f 3|sed -E 's/\\//g;s/(.*)\(.*\)(.*)"(.*)\);.*\/\//\1name\2\3/g'