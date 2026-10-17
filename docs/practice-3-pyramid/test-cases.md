# Тест-кейсы и анализ пирамиды

## Часть (б): новые тест-кейсы

| ID | Связанное требование | Уровень пирамиды | Вид | Предусловие | Шаги | Ожидаемый результат | Источник |
|---|---|---|---|---|---|---|---|
|TC-AUTH-2.1|ТЗ Authorization, алгоритм проверки, шаг 6 (amount > availableBalance -> DECLINED, responseCode="51")|unit|негативный|AuthServiceImpl изолирован; CardManagementClient замокан и возвращает карту ACTIVE с балансом 500.00 и действующим сроком; BinLookupClient замокан и возвращает issuerId; LimitUsageRepository замокан|1. Вызвать AuthServiceImpl.authorize(request, now) с суммой 990.99<br>2. Проверить возвращённый AuthorizationResponse|status=DECLINED, responseCode=51, declineReason=INSUFFICIENT_FUNDS; LimitUsageRepository.upsertLimitUsage не вызывался; CardManagementClient.reserve не вызывался|класс эквивалентности amount > availableBalance|
|TC-AUTH-2.2|ТЗ Authorization, алгоритм проверки, шаг 2 (статус BLOCKED -> DECLINED, "CARD_BLOCKED")|unit|негативный|AuthServiceImpl изолирован; CardManagementClient замокан и возвращает карту BLOCKED; BinLookupClient и LimitUsageRepository замоканы|1. Вызвать AuthServiceImpl.authorize(request, now)<br>2. Проверить возвращённый AuthorizationResponse|status=DECLINED, responseCode=05, declineReason=CARD_BLOCKED; LimitUsageRepository.upsertLimitUsage и CardManagementClient.reserve не вызывались|класс эквивалентности card_status=BLOCKED|
|TC-AUTH-2.3|ТЗ Authorization, алгоритм проверки, шаг 3 (срок действия истёк -> DECLINED, responseCode="54")|unit|негативный|AuthServiceImpl изолирован; CardManagementClient замокан и возвращает карту ACTIVE с expiryDate = 2026-09 и балансом 1000.00; BinLookupClient и LimitUsageRepository замоканы|1. Вызвать authorize с transmissionDateTime = 2026-09-30T12:00Z<br>2. Вызвать authorize с transmissionDateTime = 2026-10-01T12:00Z<br>3. Сравнить ответы|Шаг 1: отказа по сроку нет (карта действует до конца месяца expiryDate); шаг 2: status=DECLINED, responseCode=54, declineReason=CARD_EXPIRED, reserve не вызывался|граничное значение expiryDate (последний день месяца / первый день следующего)|
|TC-CMS-2.1|ТЗ Card-Management, раздел 2 (POST /api/cards - поле bin ровно 6 цифр)|unit|негативный|Валидатор BinValidator (аннотация @Bin из common) создан без Spring-контекста|1. Проверить значения "40000", "4000000", "4000A0", "400000" через BinValidator.isValid|"40000", "4000000", "4000A0" - невалидны; "400000" - валиден|граничное значение bin (5 / 6 / 7 символов)|
|TC-CMS-2.2|ТЗ Card-Management, раздел 4 (алгоритм Луна - контрольная цифра PAN)|unit|позитивный|LuhnValidator создан без Spring-контекста|1. Вызвать LuhnValidator.generatePan("400000") 100 раз<br>2. Для каждого PAN вызвать LuhnValidator.isValid(pan)|Каждый PAN - 16 цифр, начинается с 400000; isValid(pan) = true для всех|класс эквивалентности (валидный PAN)|
|TC-CMS-2.3|ТЗ Card-Management, rollback резерва (повторный откат запрещён)|unit|негативный|Объект Reservation в статусе ROLLED_BACK|1. Вызвать reservation.startRollback(amount)|Выброшено RollbackAlreadySatisfiedException; новый ReservationRollback не создан|состояние (ReservationStatus=ROLLED_BACK)|
|TC-API-01|ТЗ Gateway, POST /api/transactions (валидация + проксирование); ТЗ Switch, п. 2 (маршрутизация по BIN)|api|позитивный|Gateway и Switch подняты; Authorization заменён WireMock-заглушкой, которая на POST /api/internal/authorize отвечает APPROVED, code 00; Merchant Acquirer и RabbitMQ доступны|1. Отправить POST /api/transactions с телом {mti: "0100", stan: "000001", pan: "4000003458730237", processingCode: "000000", amount: 990.99, currencyCode: "643", transmissionDateTime: "2026-10-08T12:00:00Z", terminalId: "TERM0001", terminalType: "POS", merchantId: "MERCH0000000002", mcc: "5411", acquirerId: "ACQ002"}<br>2. Проверить HTTP-статус и тело ответа<br>3. Проверить запрос, полученный заглушкой|HTTP 200; тело: status=APPROVED, responseCode=00; заглушка получила запрос с issuerId=ISS001 (BIN 400000 из таблицы bin-routing Switch)|контракт|
|TC-API-02|ТЗ Authorization, POST /api/internal/authorize (контракт запрос-ответ)|api|негативный|Authorization и PostgreSQL подняты; Card Management заменён заглушкой, которая на GET /api/cards/4000003458730237 возвращает карту BLOCKED|1. Отправить POST /api/internal/authorize с полным телом AuthorizationRequest (все поля как в TC-API-01 + issuerId: "ISS001")<br>2. Проверить HTTP-статус и тело ответа|HTTP 403; тело: status=DECLINED, responseCode=05, declineReason=CARD_BLOCKED; к заглушке не было запроса POST /reserve|контракт|
|TC-API-03|ТЗ Card-Management, POST /api/cards/{pan}/reserve (резервирование средств)|api|негативный|Card Management и PostgreSQL подняты; создана карта ACTIVE с балансом 500.00|1. Отправить POST /api/cards/{pan}/reserve с телом {amount: 990.99, rrn: "<валидный RRN>"}<br>2. Отправить GET /api/cards/{pan}|Шаг 1: HTTP 402 (InsufficientFundsException); шаг 2: availableBalance = 500.00, резерв не создан|класс эквивалентности amount > availableBalance|
|TC-API-04|ТЗ Card-Management, DELETE /api/cards/{pan} (мягкое удаление)|api|негативный|Card Management и PostgreSQL подняты; создана карта ACTIVE|1. Отправить DELETE /api/cards/{pan}<br>2. Отправить GET /api/cards/{pan}|Шаг 1: успешный ответ; шаг 2: HTTP 404 - карта со статусом DELETED не возвращается (@SQLRestriction status <> 'DELETED')|состояние БД (status=DELETED)|
|TC-AUTH-2.4|ТЗ Authorization, алгоритм проверки, шаг 4 (сумма доводит дневной лимит ровно до значения лимита - граница ON)|integration|позитивный|Authorization подключён к реальному PostgreSQL (Testcontainers); CardManagementClient и BinLookupClient замоканы; карта ACTIVE: баланс 2000.00, dailyLimit 15000.00, monthlyLimit 300000.00; в limit_usage на дату транзакции daily_amount = 14000.00|1. Вызвать authorize с суммой 1000.00<br>2. Прочитать строку limit_usage по pan и дате<br>3. Вызвать authorize с суммой 0.01|Шаг 1: status=APPROVED, responseCode=00; шаг 2: daily_amount = 15000.00; шаг 3: status=DECLINED, responseCode=61 (граница OFF)|граничное значение dailyLimit (ON / OFF), состояние БД|
|TC-INT-01|ТЗ Switch, п. 2–3 (публикация в smp.transactions, routing key transaction.log); ТЗ Transaction Logger, п. 2 (приём из transaction-log)|integration|позитивный|Switch, RabbitMQ, Transaction Logger, PostgreSQL подняты; Authorization и Merchant Acquirer заменены заглушками (APPROVED, fee); очередь transaction-log пуста|1. Отправить POST /api/internal/route в Switch с валидным запросом<br>2. Дождаться появления записи в БД Logger (polling, таймаут 10 с)<br>3. Проверить содержимое записи|Ответ Switch получен до появления записи (асинхронность); в БД Logger появилась одна запись с тем же stan, status=APPROVED, issuerId=ISS001, acquiringFee из заглушки|сценарий (async-доставка, eventual consistency)|
|TC-INT-02|ТЗ Switch, п. 4 (откат резерва при отказе публикации в RabbitMQ)|integration|негативный|Switch, Authorization, Card Management, PostgreSQL подняты; карта ACTIVE с балансом 1000.00; RabbitMQ остановлен (Switch при publish получает AmqpException)|1. Отправить POST /api/internal/route в Switch с суммой 990.99<br>2. Проверить ответ Switch<br>3. Проверить, что Switch вызвал POST /api/internal/rollback в Authorization (rrn, pan, amount)<br>4. Проверить резерв и баланс карты в БД Card Management|Ответ status=DECLINED, responseCode=96; резерв по rrn имеет статус ROLLED_BACK; availableBalance = 1000.00; запись в Logger не появилась|сценарий (async-отказ), состояние БД|
|TC-INT-03|ТЗ Card Management, раздел «Асинхронная публикация событий» (outbox -> smp.card-events -> Notification Service)|integration|позитивный|Card Management, RabbitMQ, Notification Service, PostgreSQL подняты; таблица outbox_event пуста|1. Создать карту: POST /api/cards (валидное тело)<br>2. Проверить outbox_event сразу после ответа<br>3. Дождаться обработки outbox (интервал 1 с, таймаут 10 с)<br>4. Дождаться записи в таблице уведомлений Notification Service|Шаг 2: запись в outbox_event со статусом PENDING; шаг 3: статус PROCESSED; шаг 4: в card_notifications запись с eventType=CardServiceCreationEvent и routing key card.CardServiceCreationEvent|сценарий (outbox + async), состояние БД|
|TC-INT-04|ТЗ RabbitMQ-топология: после 3 неудачных доставок сообщение уходит в DLQ (x-max-delivery 3, DLX smp.card-events.dlx)|integration|негативный|RabbitMQ и Notification Service подняты; очереди card-notifications и card-notifications-dlq пусты|1. Опубликовать в smp.card-events с routing key card.Test тело, не являющееся JSON<br>2. Наблюдать логи Notification Service и очереди в Management UI 30 с|Notification Service получил сообщение не более 3 раз; сообщение оказалось в card-notifications-dlq; в card_notifications записи нет|сценарий (async-отказ, DLQ)|
|TC-E2E-01|ТЗ, сквозной сценарий: happy path|e2e|позитивный|Все сервисы, PostgreSQL, RabbitMQ подняты; через Card Management создана карта ACTIVE с BIN 400000 (есть в таблице bin-routing Switch), балансом 1000.00, dailyLimit 15000.00, monthlyLimit 300000.00|1. Отправить POST /api/transactions через Gateway (сумма 990.99, полное тело как в TC-API-01)<br>2. Дождаться ответа<br>3. Дождаться записи в Logger (GET /api/transactions/search по stan, polling)<br>4. Проверить баланс: GET /api/cards/{pan}<br>5. Проверить limit_usage в БД Authorization|Ответ HTTP 200, status=APPROVED, responseCode=00; в Logger запись APPROVED с тем же stan и rrn; availableBalance = 9.01; daily_amount и monthly_amount = 990.99|сценарий (сквозной)|
|TC-E2E-02|ТЗ, сквозной сценарий: отказ по заблокированной карте|e2e|негативный|Все сервисы, PostgreSQL, RabbitMQ подняты; через Card Management создана карта с BIN 400000 и балансом 1000.00, затем переведена в BLOCKED (PATCH /api/cards/{pan})|1. Отправить POST /api/transactions через Gateway (сумма 990.99)<br>2. Дождаться записи в Logger (polling)<br>3. Проверить баланс: GET /api/cards/{pan}|Ответ HTTP 200, status=DECLINED, responseCode=05; в Logger запись DECLINED с declineReason=CARD_BLOCKED; availableBalance = 1000.00|сценарий (сквозной), состояние (CardStatus=BLOCKED)|

### Почему выбраны именно эти уровни

- **unit** - проверяет логику, которая лежит целиком в коде одного класса: проверки `AuthServiceImpl` (статус, срок, баланс), валидаторы, правило повторного отката в `Reservation`. Внешние зависимости замоканы.
- **api** - проверяют контракты взаимодействия сервисов: HTTP-статус и тело ответа.
- **integration** - для проверки взаимодействия сервисов с бд, доставки через RabbitMQ.
- **e2e** - проверяют всю цепочку прохождения транзакции.

## Часть (в): распределение тест-кейсов практики 2

| ID (пр. 2) | Название | Что проверяет тест | Уровень | Обоснование |
|---|---|---|---|---|
|TC-AUTH-01|Успешная транзакция по ACTIVE-карте|Решение APPROVED + списание баланса в Card Management + рост агрегаторов лимитов в БД Authorization|integration|Ожидаемый результат - изменение данных в двух местах: резерв в Card Management и limit_usage в PostgreSQL. Это связь сервиса с БД и соседним сервисом; Gateway, Switch и Logger для проверки не нужны|
|TC-AUTH-02|Отказ по INACTIVE|Ветка `CARD_INACTIVE` в AuthServiceImpl|unit|Чистая логика: статус карты приходит от замоканного CardManagementClient|
|TC-AUTH-03|Отказ по статусу EXPIRED|Ветка `CardModelStatus.EXPIRED` -> code 54|unit|То же: switch по статусу в AuthServiceImpl, зависимости мокаются|
|TC-AUTH-04|Отказ по BLOCKED|Ветка `CARD_BLOCKED` -> code 05|unit|То же: логика выбора отказа без обращения к БД|
|TC-AUTH-05|Карта не найдена|Обработка CardNotFoundException -> code 14|unit|Мок CardManagementClient бросает CardNotFoundException; проверяется только маппинг исключения в DeclineOutcome|
|TC-AUTH-06|Истёк срок действия|Сравнение expiryDate.atEndOfMonth() с датой транзакции -> code 54|unit|Сравнение дат в Java-коде AuthServiceImpl; нужны только моки|
|TC-AUTH-07|Превышен дневной лимит|Отказ code 61 при used + amount > dailyLimit|integration|Сравнение с лимитом выполняется в SQL, поэтому с моком репозитория проверка теряет смысл|
|TC-AUTH-08|Превышен месячный лимит|Отказ code 61 при превышении monthlyLimit|integration|То же: месячный агрегат считается и сравнивается в нативном запросе|
|TC-AUTH-09|Сумма ровно равна дневному и месячному лимиту|Граница ON для обоих лимитов|integration|Граничное значение заложено в SQL-условии, проверить можно только на реальной БД|
|TC-AUTH-10|Сумма равна балансу и дневному лимиту|Граница amount = availableBalance (Java) и граница дневного лимита (SQL)|integration|Граница лимита проверяется в SQL, поэтому берётся этот уровень тесторивания|
|TC-AUTH-11|Сумма больше баланса|Отказ code 51 до проверки лимитов|unit|Сравнение `amount > availableBalance` в Java; агрегаторы не меняются, т.к. upsertLimitUsage не вызывается|
|TC-AUTH-12|Сумма равна балансу и месячному лимиту|Граница баланса и граница месячного лимита|integration|Как TC-AUTH-10: граница месячного лимита в SQL|
|TC-AUTH-13|Сумма больше баланса (месячный лимит на границе)|Отказ code 51: баланс проверяется раньше лимитов|unit|До лимитов дело не доходит - достаточно моков; проверяется порядок проверок в AuthServiceImpl|
|TC-AUTH-14|Превышен месячный лимит при дневном на границе|Отказ code 61 по месячному лимиту|integration|Решение принимает SQL-условие по monthly_amount|
|TC-AUTH-15|Превышен дневной лимит, срок истекает в текущем месяце|Отказ code 61; срок текущего месяца ещё действителен|integration|Решение по лимиту - в SQL|
|TC-CMS-01|Создание карты с валидными полями|Контракт POST /api/cards: PAN, expiryDate, status=ACTIVE в ответе|api|Проверяется ответ одного сервиса на HTTP-запрос; внутренности (генерация PAN, срок) видны только через контракт|
|TC-CMS-02|bin неверной длины|Bean Validation `@Bin` -> HTTP 400|api|Валидация выполняется на DTO `CreateCardRequest` в CardController; результат - HTTP-ответ.|
|TC-CMS-03|bin с нецифровым символом|Bean Validation `@Bin` -> HTTP 400|api|Валидация выполняется на DTO `CreateCardRequest` в CardController; результат - HTTP-ответ.|
|TC-CMS-04|Неверный currencyCode|Валидация currencyCode -> HTTP 400|api|Проверка контракта POST /api/cards.|
|TC-CMS-05|dailyLimit = 0|Валидация dailyLimit -> HTTP 400|api|Проверка контракта.|
|TC-CMS-06|monthlyLimit < dailyLimit|Проверка соотношения лимитов при создании|api|Проверка контракта.|
|TC-CMS-07|Пустой cardholderName|`@NotBlank` -> HTTP 400|api|Bean Validation на DTO, результат - HTTP-ответ|
|TC-CMS-08|Отрицательный initialBalance|Валидация initialBalance -> HTTP 400|api|Проверка контракта.|
|TC-CMS-09|PAN созданной карты проходит Луна|Корректность контрольной цифры PAN|unit|Свойство одного класса `LuhnValidator.generatePan`; поднимать сервис не нужно|
|TC-CMS-10|Генерация 100 карт по набору BIN|Контракт POST /api/cards/generate: количество, BIN, Луна|api|Проверяется ответ эндпоинта генерации целиком (count, распределение по bins)|

## Часть (г): анализ пропорций

### Сводная таблица

| Уровень | Старые | Новые | Итого | Доля |
|---|---:|---:|---:|---:|
| unit | 8 | 6 | 14 | 33,3% |
| api / integration | 17 (api 9, integration 8) | 9 (api 4, integration 5) | 26 | 61,9% |
| e2e | 0 | 2 | 2 | 4,8% |
| **Итого** | 25 | 17 | **42** | 100% |

### Фактические пропорции

Получилось примерно **33 / 62 / 5** вместо ориентира 70 / 20 / 10: unit меньше ориентира, api/integration - больше, e2e - меньше.

### Объяснение отклонений

1. Сравнение с дневным и месячным лимитами выполняется в SQL. Из-за этого 8 кейсов практики 2 на лимиты нельзя честно сделать unit - с моком репозитория они проверяют только мок. Это главная причина роста интеграционных тестов.
2. Валидация входа сделана аннотациями на DTO и срабатывает в контроллере через `@Valid`. Требование ТЗ сформулировано как «ошибка валидации / карта не создана», то есть как HTTP-ответ - это апи-контракт. Проверку валидации можно опустить и на unit, но контрактная проверка всё равно нужна, поэтому эти тест-кейсы рассматриваются на уровне апи.
3. Для системы были сделаны unit тест кейсы только для сервисов card-management и authorization, а интеграционный/api и е2е уровень рассматривался на всей системе, поэтому unit-тестов меньшая доля.
4. E2E - только два критичных сценария. Reversal оставлен на integration: там он проверяется так же полно, а e2e был бы дороже и нестабильнее. Риска ice-cream cone нет: e2e < 5%.
5. Дефицита контрактных проверок нет - 13 api-кейсов покрывают Gateway, Authorization и Card Management. Риск в unit-слое, его можно расширить без потери смысла - unit-тестами валидаторов, как упоминалось в пункте 2 и unit-тестами других сервисов.

Итог: смещение в сторону api/integration объясняется устройством системы и заданием.
