#!/bin/bash

# Directory to search for .ijx files.
SEARCH_DIR="/Users/mgajek/Projects/business/shared-plugin-demo/indexes"

# Start the JSON output
echo '{ "shared-indexes": [' > output.json

# Find all .ijx files and append them to the array in JSON
find "$SEARCH_DIR" -type f -name '*.ijx' -print0 | while IFS= read -r -d '' file; do
    # Getting the absolute path of the file and escaping backslashes for JSON compatibility
    ABS_PATH=$(realpath "$file" | sed 's/\\/\\\\/g')
    # Append an entry to the JSON array
    echo " \"$ABS_PATH\"," >> output.json
done

# Properly close the JSON array after removing the trailing comma
sed -i '' '$ s/,$//' output.json
echo '  ]' >> output.json
echo '}' >> output.json

cp output.json /Users/mgajek/Desktop/chunks-tests/predefined-folder/indexes.json
# output.json now contains the JSON data