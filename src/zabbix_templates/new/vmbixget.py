#!/usr/bin/env python

"""
vimbixget.py : python wrapper for zabbix_get to VMBIX
"""

import sys
from zget import ZGet

VMBIXHOST = "localhost"
VMBIXPORT = 12050
if len(sys.argv) < 2:
    sys.stderr.write('Usage: ' + sys.argv[0] + ' <request>\n')
    sys.exit(1)
zg = ZGet(host = VMBIXHOST, port = VMBIXPORT)
out = zg.get(sys.argv[1])
sys.stdout.write(out)
