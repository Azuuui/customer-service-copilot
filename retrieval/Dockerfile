FROM python:3.12-slim
WORKDIR /app
ARG PIP_INDEX_URL=https://pypi.org/simple
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HF_HOME=/models/huggingface \
    EMBEDDING_LOCAL_FILES_ONLY=true
COPY requirements.txt ./
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements.txt
COPY app app
COPY scripts scripts
COPY data/demo-knowledge.json data/demo-knowledge.json
EXPOSE 8765
CMD ["uvicorn", "app.api:app", "--host", "0.0.0.0", "--port", "8765"]
