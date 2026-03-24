"""
HDFS processed/population Parquet에서 사용 가능한 고유 날짜(YMD) 목록을 조회하여
stdout으로 출력합니다.

Java SparkEtlScheduler에서 이 스크립트의 출력을 파싱하여
ETL에 전달할 랜덤 시작 날짜를 결정합니다.

출력 형식: DATE:YYYYMMDD (한 줄에 하나씩, 오름차순)
"""

import sys
from pyspark.sql import SparkSession

spark = SparkSession.builder \
    .appName("ListAvailableDates") \
    .getOrCreate()

try:
    df = spark.read.parquet("hdfs://namenode:9000/processed/population/")
    dates = df.select("YMD").distinct().orderBy("YMD").collect()
    for row in dates:
        print(f"DATE:{row['YMD']}")
except Exception as e:
    print(f"ERROR:{e}", file=sys.stderr)
    raise
finally:
    spark.stop()
