# Build

```bash
# Build the artifact
user@host> ./gradlew distZip
```

# Run 
Unzip the distribution and run the start script wrapping the java application providing the Json schema file to be 
sanitized as the first argument. The sanitized input file is written to standard out and a file named `input.json.san`.

```bash
user@host> unzip build/distributions/json-schema-tidier.zip
user@host> ./json-schema-tidier/bin/json-schema-tidier input.json
```
