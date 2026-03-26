#!/bin/bash
set -euo pipefail

echo "Refreshing news aggregate pipeline..."

/spark/bin/spark-submit --master spark://spark-master:7077 /opt/spark-jobs/etl_news.py
/spark/bin/spark-submit --master spark://spark-master:7077 /opt/spark-jobs/etl_news_score.py
/spark/bin/spark-submit --master spark://spark-master:7077 /opt/spark-jobs/refresh_news_daily_aggregate.py

echo "News aggregate refresh complete!"
