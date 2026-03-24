import os
import sys
from datetime import datetime, timedelta

from db_env import resolve_db_config
from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, lit, when, concat_ws, to_timestamp, create_map,
)
from pyspark.sql.types import LongType, StringType

if len(sys.argv) < 2:
    print("Usage: etl_traffic_score.py <start_date:yyyyMMdd>")
    sys.exit(1)

start_date_str = sys.argv[1]
batch_key = sys.argv[2] if len(sys.argv) >= 3 else f"spark-{start_date_str}"
start_date = datetime.strptime(start_date_str, "%Y%m%d")
last_date = start_date + timedelta(days=6)
date_range = [(start_date + timedelta(days=i)).strftime("%Y%m%d") for i in range(7)]

print(f"2차 정제 기간: {start_date_str} ~ {last_date.strftime('%Y%m%d')} (7일)")

SPOT_TO_NAME = {
    "D-06": "서울숲/성수",
    "D-23": "신도림",
    "D-44": "잠실",
    "D-43": "강남",
    "A-12": "명동",
    "D-04": "이태원",
    "D-28": "여의도",
    "D-17": "홍대",
}

db_config = resolve_db_config()

MYSQL_URL = db_config["db_url"]
MYSQL_PROPS = {
    "user": db_config["db_username"],
    "password": db_config["db_password"],
    "driver": "com.mysql.cj.jdbc.Driver",
}

spark = SparkSession.builder \
    .appName("ETL_Traffic_Score") \
    .config("spark.jars", "/spark/jars/mysql-connector-j-8.4.0.jar") \
    .config("spark.sql.session.timeZone", "Asia/Seoul") \
    .getOrCreate()

# 지역 테이블 조회 후 id 매핑
df_location = spark.read.jdbc(
    url=MYSQL_URL,
    table="location",
    properties=MYSQL_PROPS,
)
location_rows = df_location.select("location_id", "location_name").collect()
name_to_id = {row["location_name"]: row["location_id"] for row in location_rows}

spot_to_id = {}
for spot, name in SPOT_TO_NAME.items():
    if name in name_to_id:
        spot_to_id[spot] = name_to_id[name]
    else:
        print(f"Location 테이블에 '{name}' 없음 → 스킵")

if not spot_to_id:
    print("매핑할 location이 없습니다. Location 테이블을 먼저 채워주세요.")
    spark.stop()
    sys.exit(1)

# 10~22시까지의 교통량 필터링
df = spark.read.parquet("hdfs://namenode:9000/processed/traffic/")
df = df.filter(col("YMD").isin(date_range) & (col("HH") >= 10) & (col("HH") < 22))

# 교통량 등급 변환 (VOL → traffic_status)
df_scored = df.withColumn(
    "traffic_status",
    when(col("VOL") <= 300, lit("VERY_SMOOTH"))
    .when(col("VOL") <= 700, lit("SMOOTH"))
    .when(col("VOL") <= 1500, lit("NORMAL"))
    .when(col("VOL") <= 2200, lit("CONGESTED"))
    .otherwise(lit("VERY_CONGESTED"))
    .cast(StringType())
)

# SPOT_NUM → location_id 매핑
spot_to_id_map = create_map(
    *[item for spot, loc_id in spot_to_id.items()
      for item in (lit(spot), lit(loc_id))]
)

df_mapped = df_scored.withColumn(
    "location_id", spot_to_id_map[col("SPOT_NUM")].cast(LongType())
)

df_final = df_mapped.withColumn(
    "date",
    to_timestamp(
        concat_ws(" ", col("YMD"), col("HH").cast("string")),
        "yyyyMMdd H"
    )
)

# TRUNCATE 후 INSERT
jdbc_conn = spark._jvm.java.sql.DriverManager.getConnection(
    MYSQL_URL, MYSQL_PROPS["user"], MYSQL_PROPS["password"]
)
stmt = jdbc_conn.createStatement()
stmt.execute("TRUNCATE TABLE traffic")
print("Traffic 테이블 TRUNCATE 완료")
stmt.close()
jdbc_conn.close()

df_traffic = df_final.select(
    col("location_id"),
    col("date"),
    col("traffic_status"),
    lit(batch_key).alias("source_batch_key"),
).filter(
    col("location_id").isNotNull() & col("date").isNotNull()
)

df_traffic.write.jdbc(
    url=MYSQL_URL,
    table="traffic",
    mode="append",
    properties=MYSQL_PROPS,
)

row_count = df_traffic.count()
print(f"Traffic 테이블 INSERT 완료: {row_count}행")

spark.stop()
