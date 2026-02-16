#!/bin/bash
# Setup script for Road Hazard Detection Backend Server
# This script creates a virtual environment with Python 3.12 and installs dependencies

set -e  # Exit on error

echo "🚀 Setting up Road Hazard Detection Backend..."
echo ""

# Check if Python 3.12 is available
if ! command -v python3.12 &> /dev/null; then
    echo "❌ Error: Python 3.12 is required but not found."
    echo "Please install Python 3.12:"
    echo "  brew install python@3.12"
    echo "  OR download from https://www.python.org/downloads/"
    exit 1
fi

echo "✅ Found Python 3.12"

# Create virtual environment if it doesn't exist
if [ ! -d "venv" ]; then
    echo "📦 Creating virtual environment with Python 3.12..."
    python3.12 -m venv venv
    echo "✅ Virtual environment created"
else
    echo "✅ Virtual environment already exists"
fi

# Activate virtual environment
echo "🔧 Activating virtual environment..."
source venv/bin/activate

# Upgrade pip
echo "📥 Upgrading pip..."
pip install --upgrade pip

# Install dependencies
echo "📥 Installing dependencies..."
pip install -r requirements.txt

echo ""
echo "✅ Setup complete!"
echo ""
echo "📝 Next steps:"
echo "1. Place your best.pt model file in: backend/models/best.pt"
echo "2. Review configuration in .env file"
echo "3. Start the server:"
echo "   source venv/bin/activate"
echo "   uvicorn app:app --host 0.0.0.0 --port 8000"
echo ""
