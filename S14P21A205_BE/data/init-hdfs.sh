#!/bin/bash
echo "Waiting for HDFS..."
sleep 20

hdfs dfs -mkdir -p /data

uploaded_any=false

for dataset in news population traffic; do
  local_path="/local-data/${dataset}"
  hdfs_path="/data/${dataset}"

  if [ -e "$local_path" ]; then
    if hdfs dfs -test -d "$hdfs_path"; then
      echo "${dataset} already exists in HDFS. Skipping upload."
    else
      echo "Uploading ${dataset} to HDFS..."
      hdfs dfs -put "$local_path" /data/
      uploaded_any=true
    fi
  else
    echo "Local dataset not found: ${local_path}. Skipping."
  fi
done

if [ "$uploaded_any" = false ]; then
  echo "No local datasets uploaded."
fi

hdfs dfs -ls -R /data/
