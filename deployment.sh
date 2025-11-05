#!/usr/bin/env bash

error()
{
    echo "Error:" "$@" 1>&2
}

# If Docker socket is not mounted
if [[ ! -S /var/run/docker.sock ]] ; then
    error "Please bind mount in the docker socket to /var/run/docker.sock"
    error "docker run -v /var/run/docker.sock:/var/run/docker.sock"
    error "...or make sure you have access to the docker socket at /var/run/docker.sock"
      exit 1
fi

ENV=".env"
source ".env"

remove_container_and_untag_image() {
  local NAME=$1
  local IMAGE=$2

  echo "Removing the container $NAME (if it exists)..."
  docker container stop $NAME >/dev/null 2>&1
  docker container rm $NAME >/dev/null 2>&1
  echo "Untagging the image $IMAGE..."
  docker image rm $IMAGE >/dev/null 2>&1
}

# Check if the -r flag is provided
REMOVE=false

while getopts ":r" opt; do
    case ${opt} in
        r)
            REMOVE=true
            ;;
        \?)
            echo "Invalid option: $OPTARG" 1>&2
            exit 1
            ;;
    esac
done
shift $((OPTIND -1))

# Create volumes used by the profile
# https://stackoverflow.com/a/45674488/13179591
VOLUMES=("rabbitmq" "postgres" "keycloak" "prometheus")
for volume in "${VOLUMES[@]}"
do
  docker volume create --name "${volume}"-"${PROFILE}"
done

# Create the network for the profile
NETWORK="carp-${PROFILE}"
docker network inspect "${NETWORK}" >/dev/null 2>&1 || docker network create --driver bridge "${NETWORK}"

# Prepare database script
# https://unix.stackexchange.com/questions/112023/how-can-i-replace-a-string-in-a-files/112024#112024
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
  sed -i "s/    OWNER = .*/    OWNER = ${ADMIN_USER}/g" ./deployment/postgres/init.sql || exit 1
elif [[ "$OSTYPE" == "darwin"* ]]; then
  sed -i "" "s/    OWNER = .*/    OWNER = ${ADMIN_USER}/g" ./deployment/postgres/init.sql || exit 1
fi

if [ "$REMOVE" = true ]; then
    # If the -r flag is provided, remove the container and untag the image
    remove_container_and_untag_image "carp-ws-${PROFILE}" "carp-ws:${profile}"
fi

# Instantiate containers
# https://docs.docker.com/compose/environment-variables/
# https://docs.docker.com/compose/profiles/
docker compose --profile "${PROFILE}" -p "carp-webservices-${PROFILE}" up -d
