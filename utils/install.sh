#!/usr/bin/env sh

type=""
url=""
filename=""


# Function to display help
show_help() {
  echo "Usage: $0 [options]"
  echo ""
  echo "Options:"
  echo "  --type <local|remote>  Specify 'local' or 'remote' to set the type."
  echo "  --url <URL>            Specify the URL of the FHIR server, ex http://localhost:8008."
  echo "  --file <filename>      Specify the filename, local : file.tgz or remote : http://server.com/file.tgz."
  echo "  --help                 Display this help message and exit."
  exit 0
}

# Check if no parameters were provided
if [ "$#" -eq 0 ]; then
  show_help
fi

# Parse command-line options
while [ "$#" -gt 0 ]; do
  case "$1" in
    --help)
      show_help
      ;;
    --type)
      if [ -n "$2" ]; then
        type="$2"
        shift 2
      else
        echo "Error: --type requires an argument."
        exit 1
      fi
      ;;
    --url)
      if [ -n "$2" ]; then
        url="$2"
        shift 2
      else
        echo "Error: --url requires an argument."
        exit 1
      fi
      ;;
    --file)
      if [ -n "$2" ];  then
        filename="$2"
        shift 2
      else
        echo "Error: --file requires a filename."
        exit 1
      fi
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done


base64String=""
temp_filename="downloaded_file"

# get the base64 string from the file
if [ "$type" = "local" ]; then
  base64String=$(base64 -w 0 "$filename")
elif [ "$type" = "remote" ]; then
  curl -o "$url" "temp_filename"
  base64String=$(base64 -w 0 "temp_filename")
  rm "$temp_filename"
fi

echo '{"resourceType": "Parameters","parameter": [{"name": "npmContent","valueBase64Binary": "'$base64String'"}]}' > temp.json

curl --location "${url}/fhir/ImplementationGuide/\$install" \
--header 'Content-Type: application/fhir+json' \
--data-binary @temp.json

# remove temporary files
rm temp.json

