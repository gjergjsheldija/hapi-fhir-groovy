#!/bin/bash
set +ex

envsubst < /etc/metricbeat/metricbeat.yml | tee /etc/metricbeat/metricbeat.yml

metricbeat modules enable postgresql
metricbeat setup -e
service metricbeat start

./docker-entrypoint.sh postgres -c shared_preload_libraries=pg_stat_statements

