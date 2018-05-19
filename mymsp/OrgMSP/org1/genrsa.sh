#!/usr/bin/env bash
openssl genrsa -out key.pem 2048
openssl pkcs8 -topk8 -in key.pem -out pkcs8_key.pem -nocrypt
openssl rsa -in pkcs8_key.pem -pubout -out pub.pem
rm key.pem
mv pkcs8_key.pem key.pem