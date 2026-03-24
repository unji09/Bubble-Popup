from pyspark.sql import SparkSession
from pyspark.sql.functions import col, trim, when, sum as spark_sum
from pyspark.sql.types import DoubleType, IntegerType

TARGET_CELLS = [
    "다사57254925",  # 성수
    "다사45254500",  # 신도림
    "다사62504550",  # 잠실
    "다사57504625",  # 강남
    "다사53755100",  # 명동
    "다사56504825",  # 이태원
    "다사47754725",  # 여의도
    "다사49505300",  # 홍대
]

spark = SparkSession.builder \
    .appName("ETL_Population") \
    .getOrCreate()

df = spark.read.csv(
    "hdfs://namenode:9000/data/population/",
    header=True,
    encoding="euc-kr",
    recursiveFileLookup="true",
)

cols = df.columns
# 컬럼 인덱스: [0]날짜, [1]시간, [3]250M격자, [4]활동인구합계
col_ymd = cols[0]
col_tt = cols[1]
col_cell = cols[3]
col_spop = cols[4]

df_clean = df.select(
    trim(col(col_cell)).alias("CELL_ID"),
    trim(col(col_ymd)).alias("YMD"),
    trim(col(col_tt)).cast(IntegerType()).alias("TT"),
    when(col(col_spop) == "*", None)
        .otherwise(col(col_spop))
        .cast(DoubleType())
        .alias("SPOP"),
)

df_filtered = df_clean.filter(col("CELL_ID").isin(TARGET_CELLS))

# 같은 격자가 여러 행정동에 걸치면 행이 복수 → SPOP 합산
df_agg = df_filtered.groupBy("CELL_ID", "YMD", "TT").agg(
    spark_sum("SPOP").alias("SPOP")
)

df_agg.write.mode("overwrite").parquet(
    "hdfs://namenode:9000/processed/population/"
)

print(f"Population ETL complete. Rows: {df_agg.count()}")
spark.stop()
