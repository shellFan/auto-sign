FROM mcr.microsoft.com/playwright:v1.35.0-focal

RUN rm -f /etc/apt/sources.list.d/nodesource.list \
    && apt-get update && apt-get install -y --no-install-recommends \
    novnc \
    websockify \
    xvfb \
    x11vnc \
    fonts-wqy-zenhei \
    fonts-wqy-microhei \
    && rm -rf /var/lib/apt/lists/*

COPY start.sh /start.sh
RUN chmod +x /start.sh \
    && mkdir -p /app/storage/profiles /app/storage/cookies /app/storage/screenshots

WORKDIR /app
EXPOSE 6080 9222

CMD ["/start.sh"]
