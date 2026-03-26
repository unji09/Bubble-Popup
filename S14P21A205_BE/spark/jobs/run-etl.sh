#!/bin/bash
set -euo pipefail

echo "Starting seasonal source ETL jobs..."

has_success() {
  curl -sf "http://namenode:9870/webhdfs/v1/$1/_SUCCESS?op=GETFILESTATUS" > /dev/null 2>&1
}

if has_success "processed/population" && has_success "processed/traffic"; then
  echo "Processed population/traffic already exist. Skipping seasonal source ETL."
  exit 0
fi

if has_success "processed/population"; then
  echo "Processed population already exists. Skipping population ETL."
else
  /spark/bin/spark-submit --master spark://spark-master:7077 /opt/spark-jobs/etl_population.py
fi

if has_success "processed/traffic"; then
  echo "Processed traffic already exists. Skipping traffic ETL."
else
  /spark/bin/spark-submit --master spark://spark-master:7077 /opt/spark-jobs/etl_traffic.py
fi

echo "Seasonal source ETL jobs complete!"
