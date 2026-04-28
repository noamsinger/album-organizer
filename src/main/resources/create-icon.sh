#!/bin/bash
# Create a simple icon using ImageMagick (if available)
# This creates a 512x512 PNG with "AO" text

if command -v convert &> /dev/null; then
    convert -size 512x512 xc:white \
            -fill "#4A90E2" \
            -draw "roundrectangle 0,0 512,512 80,80" \
            -fill white \
            -pointsize 280 \
            -gravity center \
            -annotate 0 "AO" \
            src/main/resources/app-icon.png
    echo "Icon created: src/main/resources/app-icon.png"
else
    echo "ImageMagick not installed. Please provide your own app-icon.png file."
fi
