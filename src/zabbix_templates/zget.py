#!/usr/bin/env python
import socket
import struct
import sys

HOST="127.0.0.1"
PORT=10050

class ZGet:
  def __init__(self, host=HOST, port=PORT):
    self.host = host
    self.port = port
    #self.key = ""
   
  def str2packed(self, data):
      header_field =  struct.pack('<4sBQ', 'ZBXD', 1, len(data))
      return header_field + data
   
  def packed2str(self, packed_data):
      header, version, length = struct.unpack('<4sBQ', packed_data[:13])
      (data, ) = struct.unpack('<%ds'%length, packed_data[13:13+length])
      return data
   
  def get(self, key):
      key = key + "\n"
      s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
      s.connect((self.host, self.port))
      s.sendall(self.str2packed(key))
   
      data = ''
      while True:
          buff = s.recv(1024)
          if not buff:
              break
          data += buff
   
      if data.startswith('ZBXD\x01'):
        response = self.packed2str(data)
      else:
        response = data
   
      s.close()
      return response
