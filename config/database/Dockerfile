FROM postgres:16

RUN apt update && \
    apt install curl gettext-base -y && \
    curl -L -O https://artifacts.elastic.co/downloads/beats/metricbeat/metricbeat-8.14.1-amd64.deb && \
    dpkg -i metricbeat-8.14.1-amd64.deb && \
    rm metricbeat-8.14.1-amd64.deb

COPY extensions.sql /docker-entrypoint-initdb.d
COPY metricbeat.yml /etc/metricbeat/
COPY postgresql.yml /etc/metricbeat/modules.d/
COPY docker-entrypoint.sh /bin

RUN chmod +x /bin/docker-entrypoint.sh && \
    chmod go-w /etc/metricbeat/modules.d/postgresql.yml && \
    chmod go-w /etc/metricbeat/metricbeat.yml

ENTRYPOINT ["/bin/docker-entrypoint.sh"]
EXPOSE 5432

CMD ["postgres"]

