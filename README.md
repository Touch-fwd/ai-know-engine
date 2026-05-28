# ai-know-engine

Spring Boot 3.5.8 + MyBatis-Plus knowledge engine service.

## Run

```bash
mvn spring-boot:run
```

Document upload page:

```text
http://localhost:8010/upload.html
```

The service connects to:

```text
jdbc:mysql://localhost:3306/know_engine_db
username: know_engine
password: know_engine
```

Tables are initialized from `src/main/resources/db/schema.sql`.

## CRUD APIs

- `GET /api/knowledge-documents`
- `GET /api/knowledge-documents/{docId}`
- `POST /api/knowledge-documents`
- `PUT /api/knowledge-documents/{docId}`
- `DELETE /api/knowledge-documents/{docId}`
- `GET /api/knowledge-segments`
- `GET /api/knowledge-segments/{id}`
- `POST /api/knowledge-segments`
- `PUT /api/knowledge-segments/{id}`
- `DELETE /api/knowledge-segments/{id}`
- `GET /api/table-metas`
- `GET /api/table-metas/{id}`
- `POST /api/table-metas`
- `PUT /api/table-metas/{id}`
- `DELETE /api/table-metas/{id}`
