# document-parser

для сборки и запуска надо:
1. Java 12+
2. Maven 3+

Собрать командой: mvn clean install
Запускать: java -jar document-parser-1.0.jar

в application.properties
server.port - порт на котором поднимется сервис
root.file.path - корневая папка с документами

сервис умеет:
POST к /document-parser

Headers:
Content-type: application/json

Body:
{
  "base64Content": "..."
  "documentType": "<DOCX или DOC>"
}

GET к /document-parser?filePath=<относительный путь от корневой папки с документами к файлу>
