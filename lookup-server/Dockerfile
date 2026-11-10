# Budowanie z katalogu głównego repozytorium (serwer korzysta z modułu service-engine):
#   docker build -f lookup-server/Dockerfile -t lookup-server .
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY gradle gradle
COPY service-engine service-engine
COPY lookup-server lookup-server
WORKDIR /src/lookup-server
RUN ./gradlew installDist --no-daemon --console=plain

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home lookup
COPY --from=build /src/lookup-server/build/install/lookup-server /app
USER lookup
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["/app/bin/lookup-server"]
