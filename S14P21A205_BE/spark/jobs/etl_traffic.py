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

TEMP_OUTPUT_PATH = "hdfs://namenode:9000/tmp/traffic_unpivot/"
FINAL_OUTPUT_PATH = "hdfs://namenode:9000/processed/traffic/"


def delete_hdfs_path_if_exists(path_text):
    path = hadoop.Path(path_text)
    if fs.exists(path):
        fs.delete(path, True)


def find_column(columns, keyword, exact=False):
    for column_name in columns:
        stripped = column_name.strip()
        if exact and stripped == keyword:
            return column_name
        if not exact and keyword in stripped:
            return column_name
    return None


delete_hdfs_path_if_exists(TEMP_OUTPUT_PATH)
processed_any = False

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
        f"'{year}년{month_padded}월'!A1",
        f"'{year}년{month}월'!A1",
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
            hour_cols = [c for c in _df.columns if c.endswith("시") and c[:-1].isdigit()]
            if not hour_cols:
                raise RuntimeError(f"Hour columns not found in path={path}, columns={_df.columns}")

            spot_col = find_column(_df.columns, "지점번호")
            ymd_col = find_column(_df.columns, "일자")
            dir_col = find_column(_df.columns, "방향", exact=True)
            if not spot_col or not ymd_col or not dir_col:
                raise RuntimeError(
                    f"Traffic columns missing in path={path}. "
                    f"spot_col={spot_col}, ymd_col={ymd_col}, dir_col={dir_col}, columns={_df.columns}"
                )

            stack_expr = ", ".join([f"'{c[:-1]}', `{c}`" for c in hour_cols])
            stack_sql = f"stack({len(hour_cols)}, {stack_expr}) as (HH, VOL)"

            df_unpivot = _df.select(
                trim(col(spot_col)).cast(StringType()).alias("SPOT_NUM"),
                col(ymd_col).cast(IntegerType()).cast(StringType()).alias("YMD"),
                trim(col(dir_col)).alias("DIR"),
                expr(stack_sql)
            ).withColumn("HH", col("HH").cast(IntegerType())) \
             .withColumn("VOL", col("VOL").cast(IntegerType()))

            df_target = df_unpivot.filter(col("SPOT_NUM").isin(TARGET_SPOTS))
            df_target.write.mode("append").parquet(TEMP_OUTPUT_PATH)
            print(f"Loaded traffic sheet {data_address} from {path}")
            processed_any = True
            loaded = True
            break
        except Exception as exc:
            last_error = exc

    if not loaded:
        raise RuntimeError(f"Failed to load traffic workbook path={path}, candidates={candidate_sheets}, error={last_error}")

if not processed_any:
    print("ERROR: No traffic workbooks could be processed")
    spark.stop()
    raise SystemExit(1)

df_target = spark.read.parquet(TEMP_OUTPUT_PATH)
print("Traffic row count (raw target):", df_target.count())

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

delete_hdfs_path_if_exists(FINAL_OUTPUT_PATH)
df_filtered.write.mode("overwrite").parquet(FINAL_OUTPUT_PATH)

print(f"Traffic ETL complete. Rows: {df_filtered.count()}")
delete_hdfs_path_if_exists(TEMP_OUTPUT_PATH)
spark.stop()
