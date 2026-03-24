import os
import sys
from datetime import datetime, timedelta

from db_env import resolve_db_config
from pyspark.sql import SparkSession
from pyspark.sql.functions import (
    col, lit, round as spark_round, least, concat_ws,
    to_timestamp, create_map,
    min as spark_min, avg as spark_avg, max as spark_max,
)
from pyspark.sql.types import IntegerType, LongType

if len(sys.argv) < 2:
    print("Usage: etl_population_score.py <start_date:yyyyMMdd>")
    sys.exit(1)

start_date_str = sys.argv[1]
batch_key = sys.argv[2] if len(sys.argv) >= 3 else f"spark-{start_date_str}"
start_date = datetime.strptime(start_date_str, "%Y%m%d")
last_date = start_date + timedelta(days=6)
date_range = [(start_date + timedelta(days=i)).strftime("%Y%m%d") for i in range(7)]

print(f"2차 정제 기간: {start_date_str} ~ {last_date.strftime('%Y%m%d')} (7일)")

CELL_TO_NAME = {
    "다사57254925": "서울숲/성수",
    "다사45254500": "신도림",
    "다사62504550": "잠실",
    "다사57504625": "강남",
    "다사53755100": "명동",
    "다사56504825": "이태원",
    "다사47754725": "여의도",
    "다사49505300": "홍대",
}

K = 2432.42
SCALE = 1.29
MAX_SCORE = 20

db_config = resolve_db_config()

MYSQL_URL = db_config["db_url"]
MYSQL_PROPS = {
    "user": db_config["db_username"],
    "password": db_config["db_password"],
    "driver": "com.mysql.cj.jdbc.Driver",
}

# spark 타임존 설정
spark = SparkSession.builder \
    .appName("ETL_Population_Score") \
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

cell_to_id = {}
for cell_id, name in CELL_TO_NAME.items():
    if name in name_to_id:
        cell_to_id[cell_id] = name_to_id[name]
    else:
        print(f"⚠️ Location 테이블에 '{name}' 없음 → 스킵")

if not cell_to_id:
    print("❌ 매핑할 location이 없습니다. Location 테이블을 먼저 채워주세요.")
    spark.stop()
    sys.exit(1)

# 10~22시까지의 유동인구 필터링
df = spark.read.parquet("hdfs://namenode:9000/processed/population/")
df = df.filter(col("YMD").isin(date_range) & (col("TT") >= 10) & (col("TT") < 22))

df_scored = df.withColumn(
    "raw_score",
    (lit(20) * col("SPOP") / (col("SPOP") + lit(K))) * lit(SCALE)
).withColumn(
    "floating_population",
    least(lit(MAX_SCORE), spark_round(col("raw_score"), 0)).cast(IntegerType())
)

cell_to_id_map = create_map(
    *[item for cell_id, loc_id in cell_to_id.items()
      for item in (lit(cell_id), lit(loc_id))]
)

cell_to_name_map = create_map(
    *[item for cell_id, name in CELL_TO_NAME.items()
      for item in (lit(cell_id), lit(name))]
)

df_mapped = df_scored.withColumn(
    "location_id", cell_to_id_map[col("CELL_ID")].cast(LongType())
).withColumn(
    "location_name", cell_to_name_map[col("CELL_ID")]
)

df_final = df_mapped.withColumn(
    "date",
    to_timestamp(
        concat_ws(" ", col("YMD"), col("TT").cast("string")),
        "yyyyMMdd H"
    )
)

jdbc_conn = spark._jvm.java.sql.DriverManager.getConnection(
    MYSQL_URL, MYSQL_PROPS["user"], MYSQL_PROPS["password"]
)
stmt = jdbc_conn.createStatement()
stmt.execute("TRUNCATE TABLE population")
print("Population 테이블 TRUNCATE 완료")
stmt.close()
jdbc_conn.close()

df_population = df_final.select(
    col("location_id"),
    col("date"),
    col("floating_population"),
    lit(batch_key).alias("source_batch_key"),
).filter(
    col("location_id").isNotNull() & col("date").isNotNull()
)

df_population.write.jdbc(
    url=MYSQL_URL,
    table="population",
    mode="append",
    properties=MYSQL_PROPS,
)

row_count = df_population.count()
print(f"Population 테이블 INSERT 완료: {row_count}행")

spark.stop()
