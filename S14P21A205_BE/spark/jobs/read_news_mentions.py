"""
HDFS의 news_mentions parquet을 읽어서 JSON으로 stdout에 출력한다.
인자: start_date(yyyyMMdd), total_days (int)

출력 형식 (JSON):
{
  "1": [{"menuName": "떡볶이", "mentionCount": 152}, ...],
  "2": [{"menuName": "햄버거", "mentionCount": 98}, ...],
  ...
}
"""
import json
import sys
from datetime import datetime, timedelta

from pyspark.sql import SparkSession
from pyspark.sql.functions import col, lit

spark = SparkSession.builder \
    .appName("Read_News_Mentions") \
    .config("spark.sql.session.timeZone", "Asia/Seoul") \
    .getOrCreate()

if len(sys.argv) < 3:
    print(json.dumps({}))
    spark.stop()
    sys.exit(0)

start_date = datetime.strptime(sys.argv[1], "%Y%m%d").date()
total_days = int(sys.argv[2])
selected_dates = [start_date + timedelta(days=offset) for offset in range(total_days)]
result = {str(day_idx): [] for day_idx in range(1, total_days + 1)}

try:
    df = spark.read.parquet("hdfs://namenode:9000/processed/news_mentions/")
except Exception:
    print(json.dumps(result, ensure_ascii=False))
    spark.stop()
    sys.exit(0)

if selected_dates:
    day_key_by_date = {
        date_value: str(day_idx)
        for day_idx, date_value in enumerate(selected_dates, start=1)
    }
    rows = (
        df.filter(
            (col("date") >= lit(selected_dates[0].isoformat()))
            & (col("date") <= lit(selected_dates[-1].isoformat()))
        )
        .select("date", "menu_name", "mention_count")
        .orderBy(col("date").asc(), col("mention_count").desc(), col("menu_name").asc())
        .collect()
    )
    for row in rows:
        day_key = day_key_by_date.get(row["date"])
        if day_key is None:
            continue
        result[day_key].append(
            {"menuName": row["menu_name"], "mentionCount": int(row["mention_count"])}
        )

print(json.dumps(result, ensure_ascii=False))

spark.stop()
