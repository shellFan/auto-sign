FROM mcr.microsoft.com/playwright:v1.35.0-focal

# 安装 noVNC
RUN apt-get update && apt-get install -y \
    novnc \
    websockify \
    xvfb \
    x11vnc \
    && rm -rf /var/lib/apt/lists/*

# 安装中文字体
RUN apt-get update && apt-get install -y \
    fonts-wqy-zenhei \
    fonts-wqy-microhei \
    && rm -rf /var/lib/apt/lists/*

# 创建启动脚本
RUN echo '#!/bin/bash\n\
export DISPLAY=:99\n\
Xvfb :99 -screen 0 1920x1080x24 &\n\
sleep 1\n\
x11vnc -display :99 -nopw -listen 0.0.0.0 -xkb -ncache 10 -ncache_cr -forever &\n\
websockify --web /usr/share/novnc 6080 localhost:5900 &\n\
# 启动 Chrome 远程调试\n\
/opt/google/chrome/chrome --no-sandbox --disable-setuid-sandbox --remote-debugging-port=9222 --remote-debugging-address=0.0.0.0 &\n\
wait\n\
' > /start.sh && chmod +x /start.sh

WORKDIR /app
RUN mkdir -p /app/storage/profiles /app/storage/cookies /app/storage/screenshots

EXPOSE 6080 9222

CMD ["/start.sh"]
