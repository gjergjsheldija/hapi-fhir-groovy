#!/usr/bin/env sh

ig_name=""
version=""
url=""


# Function to display help
show_help() {
  echo "Usage: $0 [options]"
  echo ""
  echo "Options:"
  echo "  --url <URL>            Specify the URL of the FHIR server, ex http://localhost:8008."
  echo "  --ig_name              Specify the IG name, ex: fhir.mona.icu.r4"
  echo "  --version              Specify the IG version, ex: 0.0.1"
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
    --url)
      if [ -n "$2" ]; then
        url="$2"
        shift 2
      else
        echo "Error: --url requires an argument."
        exit 1
      fi
      ;;
    --ig_name)
      if [ -n "$2" ];  then
        ig_name="$2"
        shift 2
      else
        echo "Error: --ig_name requires an argument."
        exit 1
      fi
      ;;
    --version)
      if [ -n "$2" ];  then
        version="$2"
        shift 2
      else
        echo "Error: --version requires an argument."
        exit 1
      fi
      ;;
    *)
      echo "Unknown option: $1"
      exit 1
      ;;
  esac
done

curl --location "${url}/fhir/ImplementationGuide/\$uninstall" \
--header 'Content-Type: application/fhir+json' \
--data '{
            "resourceType": "Parameters",
            "parameter": [
                {
                    "name": "name",
                    "valueString": "'"${ig_name}"'"
                },
                {
                    "name": "version",
                    "valueString": "'"${version}"'"
                }
            ]
        }'