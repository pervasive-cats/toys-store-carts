FROM eclipse-temurin:17.0.8_7-jre

RUN mkdir /opt/app
COPY application.conf /opt/app/
RUN curl -s https://api.github.com/repos/pervasive-cats/toys-store-carts/releases/latest \
    | grep "browser_download_url.*main.jar"  \
    | head -1  \
    | cut -d : -f 2,3  \
    | tr -d \"  \
    | wget -q -i - -O /opt/app/main.jar
CMD ["java", "-jar", "/opt/app/main.jar", "/opt/app/application.conf"]