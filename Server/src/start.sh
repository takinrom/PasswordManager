#!/bin/bash

source ./venv/bin/activate
gunicorn --certfile=cert.pem --keyfile=key.pem --bind=0.0.0.0:<port> app:app
