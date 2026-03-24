from pyspark.sql import SparkSession
from pyspark.sql.functions import col, trim, lit, expr, first, when
from pyspark.sql.types import IntegerType, StringType
from pyspark.sql import Window
import re

TARGET_SPOTS = ["D-06", "D-23", "D-44", "D-43", "A-12", "D-04", "D-28", "D-17"]

spark = SparkSession.builder \
    .appName("ETL_Traffic") \
    .getOrCreate()

# HDFS에서 xlsx 파일 목록 가져오기 (spark-excel은 디렉토리/글로브 미지원)
hadoop = spark._jvm.org.apache.hadoop.fs
conf = spark._jsc.hadoopConfiguration()
fs = hadoop.FileSystem.get(spark._jvm.java.net.URI("hdfs://namenode:9000"), conf)
status = fs.listStatus(hadoop.Path("/data/traffic/"))
xlsx_files = [str(f.getPath()) for f in status if str(f.getPath()).endswith(".xlsx")]
print(f"Found {len(xlsx_files)} xlsx files: {xlsx_files}")

if not xlsx_files:
    print("ERROR: No xlsx files found in /data/traffic/")
    spark.stop()
    raise SystemExit(1)

# 엑셀 파일 구조:
# - 시트 0: 범례 (스킵)
# - 시트 1: 실제 교통량 데이터 (컬럼: 일자, 요일, 지점명, 지점번호, 방향, 방향설명, 0시~23시)
# - 시트 2+: 지점 주소 등 참고 정보 (스킵)
# dataAddress로 두 번째 시트 지정

from functools import reduce
from pyspark.sql import DataFrame

dfs = []
for path in xlsx_files:
    match = re.search(r"seoul_traffic_(\d{4})_(\d{2})\.xlsx$", path)
    if not match:
        print(f"SKIP: could not parse sheet name from path={path}")
        continue

    year = match.group(1)
    month_padded = match.group(2)
    month = str(int(month_padded))
    candidate_sheets = [
        f"'{year}년 {month_padded}월'!A1",
        f"'{year}년 {month}월'!A1",
    ]

    loaded = False
    last_error = None
    for data_address in candidate_sheets:
        try:
            _df = spark.read.format("com.crealytics.spark.excel") \
                .option("header", "true") \
                .option("inferSchema", "false") \
                .option("maxRowsInMemory", "200") \
                .option("dataAddress", data_address) \
                .load(path)
            dfs.append(_df)
            print(f"Loaded traffic sheet {data_address} from {path}")
            loaded = True
            break
        except Exception as exc:
            last_error = exc

    if not loaded:
        raise RuntimeError(f"Failed to load traffic workbook path={path}, candidates={candidate_sheets}, error={last_error}")

if not dfs:
    print("ERROR: No traffic workbooks could be loaded")
    spark.stop()
    raise SystemExit(1)

df = reduce(DataFrame.unionByName, dfs)
print("Traffic columns:", df.columns)
print("Traffic row count (raw):", df.count())

# 컬럼 구조: 일자, 요일, 지점명, 지점번호, 방향, 방향설명, 0시, 1시, ..., 23시
# 시간별 컬럼(0시~23시)을 unpivot하여 HH, VOL 형태로 변환
hour_cols = [c for c in df.columns if c.endswith("시") and c[:-1].isdigit()]
print(f"Hour columns found: {hour_cols}")

if not hour_cols:
    print("ERROR: Could not find hour columns (0시~23시)")
    print("Available columns:", df.columns)
    spark.stop()
    raise SystemExit(1)

# 컬럼 찾기
spot_col = None
ymd_col = None
dir_col = None
for c in df.columns:
    cl = c.strip()
    if "지점번호" in cl:
        spot_col = c
    elif "일자" in cl:
        ymd_col = c
    elif "방향" == cl:
        dir_col = c

if not spot_col or not ymd_col or not dir_col:
    print(f"ERROR: spot_col={spot_col}, ymd_col={ymd_col}, dir_col={dir_col}")
    print("Available columns:", df.columns)
    spark.stop()
    raise SystemExit(1)

print(f"Using spot_col='{spot_col}', ymd_col='{ymd_col}', dir_col='{dir_col}'")

# stack()으로 unpivot: 시간별 컬럼 → (HH, VOL) 행, 방향 포함
stack_expr = ", ".join([f"'{c[:-1]}', `{c}`" for c in hour_cols])
stack_sql = f"stack({len(hour_cols)}, {stack_expr}) as (HH, VOL)"

df_unpivot = df.select(
    trim(col(spot_col)).cast(StringType()).alias("SPOT_NUM"),
    col(ymd_col).cast(IntegerType()).cast(StringType()).alias("YMD"),
    trim(col(dir_col)).alias("DIR"),
    expr(stack_sql)
).withColumn("HH", col("HH").cast(IntegerType())) \
 .withColumn("VOL", col("VOL").cast(IntegerType()))

# 타겟 지점만 필터링
df_target = df_unpivot.filter(col("SPOT_NUM").isin(TARGET_SPOTS))

# 유입/유출 중 하나만 선택: 유입 우선, 없으면 유출
# 방향 우선순위: 유입=1, 유출=2
df_ranked = df_target.withColumn(
    "has_data", when(col("VOL").isNotNull(), 0).otherwise(1)
).withColumn(
    "dir_priority",
    when(col("DIR") == "유입", 1).otherwise(2)
)
from pyspark.sql.window import Window
w = Window.partitionBy("SPOT_NUM", "YMD", "HH").orderBy("has_data", "dir_priority")
from pyspark.sql.functions import row_number
df_dedup = df_ranked.withColumn("rn", row_number().over(w)) \
    .filter(col("rn") == 1) \
    .drop("rn", "has_data", "dir_priority", "DIR")

df_filtered = df_dedup

df_filtered.write.mode("overwrite").parquet(
    "hdfs://namenode:9000/processed/traffic/"
)

print(f"Traffic ETL complete. Rows: {df_filtered.count()}")
spark.stop()
