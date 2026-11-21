FROM python:3.13.18-slim-bookworm@sha256:a5a7017efc5969e00ae2b91c8eb0631049a569652aa4ffc4501769049ae45c72 AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_DISABLE_PIP_VERSION_CHECK=1

FROM base AS build
WORKDIR /build
COPY requirements.lock linux-amd64-wheels.sha256 ./
RUN mkdir /wheels && python -m pip download --no-cache-dir --only-binary=:all: --no-deps \
      --dest /wheels -r requirements.lock setuptools==84.1.0 && \
    cd /wheels && sha256sum --strict -c /build/linux-amd64-wheels.sha256 && \
    test "$(ls -1 *.whl | wc -l)" = "$(wc -l < /build/linux-amd64-wheels.sha256)"
ENV PIP_NO_INDEX=1 PIP_FIND_LINKS=/wheels SOURCE_DATE_EPOCH=1704067200
RUN python -m pip install --no-deps setuptools==84.1.0
COPY pyproject.toml ./
COPY src/ ./src/
RUN python -m pip wheel --no-build-isolation --no-deps --wheel-dir /dist . && \
    python -m venv /opt/service && \
    /opt/service/bin/pip install --no-compile --no-deps -r requirements.lock /dist/*.whl && \
    /opt/service/bin/pip check

FROM base
WORKDIR /app
COPY --from=build /opt/service /opt/service
COPY contracts/movements-v1.json /app/contracts/movements-v1.json
ENV IRONVELLUM_MCP_MANIFEST=/app/contracts/movements-v1.json
USER 65532:65532
EXPOSE 8000
HEALTHCHECK --interval=60s --timeout=20s --start-period=30s --retries=3 \
    CMD ["/opt/service/bin/ironvellum-production-mcp", "healthcheck"]
ENTRYPOINT ["/opt/service/bin/ironvellum-production-mcp"]
CMD ["serve", "--host", "0.0.0.0", "--port", "8000"]
