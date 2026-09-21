@echo off
rem Setup script to recreate virtual environment and install requirements for Windows

echo === OpenSplatServer Environment Setup ===
if not exist "venv" (
    echo Creating Python virtual environment...
    python -m venv venv
)

echo Activating virtual environment...
call venv\Scripts\activate

echo Installing requirements from requirements.txt...
python -m pip install --upgrade pip
pip install -r requirements.txt

echo.
echo === Setup complete! You can now start the server with: python main.py ===
