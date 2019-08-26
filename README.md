# document-parser

для сборки и запуска надо:
1. Java 11+
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
  "documentFileType": "<DOCX или DOC>"
}

GET к /document-parser?filePath=<относительный путь от корневой папки с документами к файлу>

Для запуска консольной версии надо распаковать document-parser-1.0.1-distribution.zip
и из распакованной директории запустить 

под Windows: java -cp classes;lib/* com.nemo.document.parser.App -i <путь к файлу>

под Linux: java -cp classes:lib/* com.nemo.document.parser.App -i <путь к файлу>
