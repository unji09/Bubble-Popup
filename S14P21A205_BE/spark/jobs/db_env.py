import os
from urllib.parse import urlparse


def resolve_db_config():
    db_url = os.environ.get("DB_URL")
    db_host = os.environ.get("DB_HOST")
    db_port = os.environ.get("DB_PORT")
    db_name = os.environ.get("DB_NAME")
    db_username = os.environ.get("DB_USERNAME")
    db_password = os.environ.get("DB_PASSWORD")

    jdbc_url = db_url if db_url and db_url.startswith("jdbc:mysql://") else None
    if jdbc_url and (not db_host or not db_port or not db_name):
        parsed = urlparse(jdbc_url[len("jdbc:"):])
        db_host = db_host or parsed.hostname
        db_port = db_port or str(parsed.port or 3306)
        db_name = db_name or parsed.path.lstrip("/")

    if not jdbc_url and db_host and db_port and db_name:
        jdbc_url = (
            f"jdbc:mysql://{db_host}:{db_port}/{db_name}"
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul"
        )

    config = {
        "db_url": jdbc_url,
        "db_host": db_host,
        "db_port": int(db_port) if db_port else None,
        "db_name": db_name,
        "db_username": db_username,
        "db_password": db_password,
    }

    missing = [
        key for key in ("db_url", "db_host", "db_port", "db_name", "db_username", "db_password")
        if not config[key]
    ]
    if missing:
        raise RuntimeError(f"필수 DB 설정 누락: {', '.join(missing)}")

    return config
