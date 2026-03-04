# PR Checklist (8 Points)

Run each point before merge. Execute commands from repo root unless stated otherwise.

1. **Point 1: SEC-01 household report authorization guard**
```bash
cd pocketr-api
./gradlew test --tests '*ReportingTest*'
```

2. **Point 2: SEC-04 login redirect sanitization**
```bash
cd pocketr-ui
npm run test:unit -- src/__tests__/sanitizeRedirect.spec.ts
npm run test:unit -- src/__tests__/routerGuards.spec.ts
```

3. **Point 3: BUG-01 householdId propagation in balance requests**
```bash
cd pocketr-ui
npm run test:unit -- src/__tests__/balanceBatchPages.spec.ts
npm run test:unit -- src/__tests__/txnStrategies.spec.ts
```

4. **Point 4: BUG-02 household shared-account visibility contract**
```bash
cd pocketr-api
./gradlew test --tests '*HouseholdAccountShareIntegrationTest*'
```

5. **Point 5: AUTH fetch strategy (`User.roles` lazy + explicit auth fetch path)**
```bash
cd pocketr-api
./gradlew test --tests '*UserRepositoryAuthFetchTest*'
```

6. **Point 6: alpha schema reset workflow (`ddl-auto` policy path)**
```bash
cd pocketr-api
./gradlew bootRun --args='--spring.profiles.active=dev,alpha-reset'
# stop app, then restart in normal dev mode:
./gradlew bootRun --args='--spring.profiles.active=dev'
```

7. **Point 7: category duplicate-check indexing strategy (`lower(name)` index)**
```bash
docker compose up -d db
docker compose exec -T db psql -U "${DB_USER:-pocketr_user}" -d pocketr_db -c \
  "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'category_tag' AND indexname = 'idx_category_tag_owner_lower_name';"
```

8. **Point 8: final regression gates (backend + frontend)**
```bash
cd pocketr-api
./gradlew test
cd ../pocketr-ui
npm run test:unit
npm run build
```
