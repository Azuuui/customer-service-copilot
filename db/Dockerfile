FROM python:3.12-slim
WORKDIR /app
RUN pip install --no-cache-dir "psycopg[binary]==3.2.3"
COPY scripts/migrate.py /app/scripts/migrate.py
COPY db/migrations /app/db/migrations
ENTRYPOINT ["python", "/app/scripts/migrate.py"]
