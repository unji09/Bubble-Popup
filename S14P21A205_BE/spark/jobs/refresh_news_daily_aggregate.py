import pymysql
from pyspark.sql import SparkSession

from db_env import resolve_db_config


AGGREGATE_TABLE = "news_daily_menu_aggregate"
NEWS_MENTIONS_PATH = "hdfs://namenode:9000/processed/news_mentions/"


def db_connection(db):
    return pymysql.connect(
        host=db["db_host"],
        port=db["db_port"],
        user=db["db_username"],
        password=db["db_password"],
        database=db["db_name"],
        charset="utf8mb4",
        autocommit=True,
        cursorclass=pymysql.cursors.DictCursor,
    )


spark = (
    SparkSession.builder
    .appName("Refresh_News_Daily_Aggregate")
    .config("spark.sql.session.timeZone", "Asia/Seoul")
    .getOrCreate()
)

df = (
    spark.read.parquet(NEWS_MENTIONS_PATH)
    .select("date", "menu_name", "mention_count")
    .orderBy("date", "menu_name")
)

rows = [
    (row["date"], row["menu_name"], int(row["mention_count"]))
    for row in df.collect()
]

db = resolve_db_config()
connection = db_connection(db)

try:
    with connection.cursor() as cursor:
        cursor.execute(f"DELETE FROM {AGGREGATE_TABLE}")
        if rows:
            cursor.executemany(
                f"""
                INSERT INTO {AGGREGATE_TABLE} (source_date, menu_name, mention_count)
                VALUES (%s, %s, %s)
                """,
                rows,
            )
finally:
    connection.close()
    spark.stop()

print(f"Refreshed {AGGREGATE_TABLE}. rows={len(rows)}")
