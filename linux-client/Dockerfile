FROM python:3.11-alpine

WORKDIR /app

# Install system dependencies for PyGObject and network tools
RUN apk add --no-cache \
    gobject-introspection \
    gobject-introspection-dev \
    glib-dev \
    cairo-dev \
    pango-dev \
    gtk+3.0 \
    iptables \
    iproute2 \
    gcc \
    musl-dev \
    libffi-dev

COPY requirements.txt requirements-dev.txt ./
RUN pip install --no-cache-dir -r requirements.txt -r requirements-dev.txt

COPY . .

CMD ["pytest", "tests/"]
