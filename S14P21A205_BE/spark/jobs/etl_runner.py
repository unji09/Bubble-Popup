import json
import random
import subprocess
import sys
import time
import traceback
from datetime import datetime, timedelta

import pymysql

from db_env import resolve_db_config

POLL_INTERVAL_SECONDS = 10
SPARK_MASTER_URL = 'spark://spark-master:7077'
RUN_ETL_COMMAND = ['bash', '/opt/spark-jobs/run-etl.sh']


def log(message):
    print(f"[etl-runner] {datetime.now().isoformat(timespec='seconds')} {message}", flush=True)


def ensure_connection(connection):
    connection.ping(reconnect=True)


def db_connection(db):
    return pymysql.connect(
        host=db['db_host'],
        port=db['db_port'],
        user=db['db_username'],
        password=db['db_password'],
        database=db['db_name'],
        charset='utf8mb4',
        autocommit=True,
        cursorclass=pymysql.cursors.DictCursor,
    )


def run_command(command):
    process = subprocess.run(command, capture_output=True, text=True)
    if process.returncode != 0:
        message = process.stderr.strip() or process.stdout.strip() or 'command failed'
        raise RuntimeError(message)
    return process.stdout


def spark_submit(script_name, *args):
    command = [
        '/spark/bin/spark-submit',
        '--master',
        SPARK_MASTER_URL,
        f'/opt/spark-jobs/{script_name}',
        *[str(arg) for arg in args],
    ]
    return run_command(command)


def fetch_pending_request(connection):
    ensure_connection(connection)
    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT request_id, season_id
            FROM etl_job_request
            WHERE status = 'PENDING'
            ORDER BY requested_at ASC, request_id ASC
            LIMIT 1
            """
        )
        return cursor.fetchone()


def claim_request(connection, request_id):
    ensure_connection(connection)
    with connection.cursor() as cursor:
        cursor.execute(
            """
            UPDATE etl_job_request
            SET status = 'RUNNING', started_at = NOW(6), error_message = NULL
            WHERE request_id = %s AND status = 'PENDING'
            """,
            (request_id,),
        )
        return cursor.rowcount == 1


def load_total_days(connection, season_id):
    ensure_connection(connection)
    with connection.cursor() as cursor:
        cursor.execute('SELECT total_days FROM season WHERE season_id = %s', (season_id,))
        row = cursor.fetchone()
    if not row or not row['total_days']:
        raise RuntimeError(f'season total_days not found. seasonId={season_id}')
    return int(row['total_days'])


def parse_available_dates(output):
    dates = []
    for line in output.splitlines():
        if line.startswith('ERROR:'):
            raise RuntimeError(line[6:].strip() or 'list_available_dates failed')
        if line.startswith('DATE:'):
            dates.append(line[5:].strip())
    return sorted(dates)


def pick_start_date(available_dates, required_days, season_id):
    if not available_dates:
        raise RuntimeError('available dates are empty')

    valid = []
    for index in range(0, len(available_dates) - required_days + 1):
        start = datetime.strptime(available_dates[index], '%Y%m%d')
        end = datetime.strptime(available_dates[index + required_days - 1], '%Y%m%d')
        if (end - start).days == required_days - 1:
            valid.append(available_dates[index])

    if not valid:
        return available_dates[0]

    return random.Random(season_id).choice(valid)


def parse_mentions_json(output):
    lines = [line.strip() for line in output.splitlines() if line.strip()]
    if not lines:
        return {}
    return json.loads(lines[-1])


def load_aggregate_mentions(connection, start_date_str, total_days):
    ensure_connection(connection)
    start_date = datetime.strptime(start_date_str, '%Y%m%d').date()
    result = {str(day): [] for day in range(1, total_days + 1)}

    with connection.cursor() as cursor:
        cursor.execute(
            """
            SELECT source_date, menu_name, mention_count
            FROM news_daily_menu_aggregate
            WHERE source_date BETWEEN %s AND %s
            ORDER BY source_date ASC, mention_count DESC, menu_name ASC
            """,
            (start_date, start_date + timedelta(days=total_days - 1)),
        )
        rows = cursor.fetchall()

    if not rows:
        raise RuntimeError(
            f'news aggregate is empty. startDate={start_date_str}, totalDays={total_days}'
        )

    day_by_date = {
        start_date + timedelta(days=offset): str(offset + 1)
        for offset in range(total_days)
    }
    for row in rows:
        day_key = day_by_date.get(row['source_date'])
        if day_key is None:
            continue
        result[day_key].append(
            {
                'menuName': row['menu_name'],
                'mentionCount': int(row['mention_count']),
            }
        )

    return result


def save_mentions(connection, batch_key, start_date_str, mentions_by_day):
    ensure_connection(connection)
    with connection.cursor() as cursor:
        cursor.execute('DELETE FROM news_menu_mention WHERE source_batch_key = %s', (batch_key,))
        rows = []
        start_date = datetime.strptime(start_date_str, '%Y%m%d').date()
        for day_text, mentions in mentions_by_day.items():
            day = int(day_text)
            source_date = start_date + timedelta(days=day - 1)
            for mention in mentions:
                rows.append((
                    batch_key,
                    day,
                    source_date,
                    mention['menuName'],
                    int(mention['mentionCount']),
                ))
        if rows:
            cursor.executemany(
                """
                INSERT INTO news_menu_mention (source_batch_key, day, source_date, menu_name, mention_count)
                VALUES (%s, %s, %s, %s, %s)
                """,
                rows,
            )
        return len(rows)


def mark_request_succeeded(connection, request_id, start_date_str, batch_key):
    ensure_connection(connection)
    with connection.cursor() as cursor:
        cursor.execute(
            """
            UPDATE etl_job_request
            SET status = 'SUCCEEDED', selected_start_date = %s, source_batch_key = %s,
                completed_at = NOW(6), error_message = NULL
            WHERE request_id = %s
            """,
            (datetime.strptime(start_date_str, '%Y%m%d').date(), batch_key, request_id),
        )


def mark_request_failed(connection, request_id, message):
    truncated = (message or 'unknown error')[:1000]
    ensure_connection(connection)
    with connection.cursor() as cursor:
        cursor.execute(
            """
            UPDATE etl_job_request
            SET status = 'FAILED', completed_at = NOW(6), error_message = %s
            WHERE request_id = %s
            """,
            (truncated, request_id),
        )


def process_request(connection, request):
    season_id = request['season_id']
    request_id = request['request_id']
    log(f"Processing requestId={request_id} seasonId={season_id}")
    total_days = load_total_days(connection, season_id)
    log(f"Loaded totalDays={total_days}")

    log("Running raw ETL pipeline")
    run_command(RUN_ETL_COMMAND)
    log("Raw ETL pipeline completed")

    log("Listing available ETL dates")
    available_dates_output = spark_submit('list_available_dates.py')
    available_dates = parse_available_dates(available_dates_output)
    log(f"Resolved availableDates={len(available_dates)}")
    start_date = pick_start_date(available_dates, total_days, season_id)
    batch_key = f"spark-{start_date}-{datetime.now().strftime('%Y%m%d%H%M%S')}"
    log(f"Selected startDate={start_date} batchKey={batch_key}")

    log("Running etl_population_score.py")
    spark_submit('etl_population_score.py', start_date, batch_key)
    log("Completed etl_population_score.py")

    log("Running etl_traffic_score.py")
    spark_submit('etl_traffic_score.py', start_date, batch_key)
    log("Completed etl_traffic_score.py")

    log("Loading precomputed news aggregate")
    mentions_by_day = load_aggregate_mentions(connection, start_date, total_days)
    mention_row_count = sum(len(mentions) for mentions in mentions_by_day.values())
    log(f"Parsed news mentions days={len(mentions_by_day)} rows={mention_row_count}")

    saved_row_count = save_mentions(connection, batch_key, start_date, mentions_by_day)
    log(f"Saved news_menu_mention rows={saved_row_count}")

    mark_request_succeeded(connection, request_id, start_date, batch_key)
    log(f"Marked requestId={request_id} as SUCCEEDED")


def main():
    db = resolve_db_config()
    while True:
        connection = None
        try:
            connection = db_connection(db)
            pending = fetch_pending_request(connection)
            if not pending:
                time.sleep(POLL_INTERVAL_SECONDS)
                continue

            if not claim_request(connection, pending['request_id']):
                time.sleep(1)
                continue

            try:
                process_request(connection, pending)
            except Exception as exc:
                log(f"Request failed. requestId={pending['request_id']} error={exc}")
                traceback.print_exc()
                mark_request_failed(connection, pending['request_id'], str(exc))
        except Exception as exc:
            log(f"Runner loop error: {exc}")
            traceback.print_exc()
            time.sleep(POLL_INTERVAL_SECONDS)
        finally:
            if connection is not None:
                connection.close()


if __name__ == '__main__':
    main()
