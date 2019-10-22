# document-parser


[![Build Status](https://travis-ci.com/nemoware/document-parser.svg?branch=master)](https://travis-ci.com/nemoware/document-parser)

## Build
для сборки и запуска надо:
1. Java 11+
2. Maven 3+

Собрать командой: mvn clean install

в application.properties

server.port - порт на котором поднимется сервис

root.file.path - корневая папка с документами

## Run
### Web service
Запускать сервис: `java -jar document-parser-<version>.jar`

сервис умеет:
POST к /document-parser

Headers:
```
Content-type: application/json
```
Body:
```
{
  "base64Content": "..."
  "documentFileType": "<DOCX или DOC>"
}
```

GET к /document-parser?filePath=<относительный путь от корневой папки с документами к файлу>

### CLI
Для запуска консольной версии надо распаковать `document-parser-<version>-distribution.zip` ( https://github.com/nemoware/document-parser/releases/latest )
и из распакованной директории запустить 

- под Windows: `java -cp classes;lib/* com.nemo.document.parser.App -i <путь к файлу>`

- под Linux: `java -cp classes:lib/* com.nemo.document.parser.App -i <путь к файлу>`

example: https://github.com/compartia/nlp_tools/blob/master/notebooks/Test_document_parser.ipynb

## Описание выходного json

```
{
  "documents":[//массив документов, первый основной, остальные(если есть в файле) связанные с ним суб-документы.
    {
      "documentDate": "2019-02-22", //дата документа в формате yyyy-MM-dd, если дата не найдена, то null
      "documentType": "CONTRACT", //тип документа(текущий поддерживаемый список можно найти в: https://github.com/nemoware/document-parser/blob/master/src/main/java/com/nemo/document/parser/DocumentType.java), если документа неизвестен\не определен, то будет "UNKNOWN"
      "documentNumber": "абвгд", // номер документа, если не определен, то будет пустая строка
      "documentDateSegment":{//сегмент текста в текстах параграфов, где была найдена дата документа.
        "offset": 74,//начало сегмента в символах от начала документа в текстах параграфов, если дата не определена, то -1
        "text": "22» февраля 2019",//соответствующий текст с датой, если дата не определена, то пустая строка
        "length": 16, //длина текста, 0 если дата не определена
      },
      "documentNumberSegment":{"offset": -1, "text": "абвгд", "length": 5},//тоже самое, что и в documentDateSegment, только относительно номера документа
      "paragraphs":[//массив параграфов
        {
          "paragraphHeader":{//заголовок параграфа, внутри такой же сегмент текста, как и в documentDateSegment
            "offset": 0,
            "text": "Договор N 1\r\nпожертвования",
            "length": 24
          },
            "paragraphBody":{// тело параграфа, внутри такой же сегмент текста, как и в documentDateSegment
            "offset": 24,
            "text": " г. Санкт-Петербург \t\t\t\t «22» февраля 2019 г.\r\nОбщество с ограниченной ответственностью «Ромашка», именуемое в дальнейшем \"Жертвователь\", в лице Генерального директора Сидорова С.С., ...",
            "length": 481
          }
        },
     ...
      ]
    }
    ...
  ],
  "version": "1.1.2"//версия document-parser
}
```
