#!/bin/bash
# Setup script to recreate virtual environment and install requirements
set -e

echo "=== OpenSplatServer Environment Setup ==="
if [ ! -d "venv" ]; then
    echo "Creating Python virtual environment..."
    python3 -m venv venv
fi

echo "Activating virtual environment..."
source venv/bin/activate

echo "Installing requirements from requirements.txt..."
pip install --upgrade pip
pip install -r requirements.txt

echo ""
echo "=== Setup complete! You can now start the server with: python main.py ==="
