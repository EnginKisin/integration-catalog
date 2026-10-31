# Shared system catalog and frontend contract

The API root is `/api/v1`. Follow the [frontend guide](frontend-integration.md)
for sessions and CSRF. System management uses one shared catalog. Source, target,
and middleware choices all come from the same system list.

## IDs and relationships

- Old `/sources`, `/destinations`, and `/middlewares` endpoints were removed.
- Integration requests use `sourceSystemId`, `destinationSystemId`, and
  `middlewareSystemId`. Old ID fields are not supported.
- List/detail responses include the system ID and name for each role.
- `environmentTypes` lists each configured environment once, in
  `DEV`, `TEST`, `PROD` order. It is `[]` when no settings exist.
- Use system IDs for selection, relationships, and unique map nodes, not system names.

## System management

| Method / path | Body | Success |
|---|---|---|
| GET `/systems` | None | 200 system array |
| GET `/systems?active=true` or `?active=false` | None | 200 filtered system array |
| GET `/systems/{id}` | None | 200 system |
| POST `/systems` | `{name,description}` | 201 system |
| PUT `/systems/{id}` | `{name,description}` | 200 system |
| PATCH `/systems/{id}/active` | `{active}` | 200 system |
| DELETE `/systems/{id}` | None | 204 empty |

POST/PUT example:

```json
{"name":"SAP","description":"Enterprise ERP"}
```

System response:

```json
{"id":1,"name":"SAP","description":"Enterprise ERP","active":true,"usageCount":2}
```

`usageCount` counts distinct integrations across all three roles. Each integration
is counted once. `name` is required and has a 100-character limit. `description`
must not be null and has a 500-character limit. New systems are active; use PATCH
to change active status. DELETE requires ADMIN. Other writes require EDITOR/ADMIN;
all three roles can read. An in-use system cannot be deleted (409). See the usage
and active-state rules below.

## Usage counts and deletion

`usageCount` counts integrations that use the system as a source, target, or
middleware. It includes drafts and completed records. If one system has all
three roles in one integration, that integration is counted once. List counts
are read with a batch query, not a separate count query for each system.

An in-use system cannot be deleted even if inactive. Deleting a system never
clears an integration reference. Example error:

```json
{
  "timestamp": "2026-10-31T18:00:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "SYSTEM_IN_USE",
  "message": "System is used by integrations; deactivate it instead",
  "path": "/api/v1/systems/1",
  "requestId": "8e602015-ea22-4a62-bc33-cda40345b09d",
  "details": [],
  "violations": [],
  "params": {
    "usageCount": 5
  }
}
```

Catalog updates, active-state changes, and deletion lock the system row for the
transaction. Integration assignments lock selected shared systems once each,
in increasing ID order. Usage is checked again at deletion time.

## Inactive records and role changes

Deactivating a system does not change existing integrations or their status.
Its name and ID still appear in list/detail responses. An existing inactive
selection may stay in the same field of the same integration. Using it in a new
integration, selecting it as a replacement, or moving it to another role returns 409.

Do not drop the current inactive selection from edit screens. Use `active=true`
for new choices, and show the existing inactive system from the integration
response or a system detail request. Renaming a shared system updates the names
returned by all related integration list/detail requests.

## Creating and updating integrations

`POST /integrations` returns 201; `PUT /integrations/{id}` returns 200.
PUT updates all basic information. Do not send only the changed field.

```json
{
  "name":"Order transfer",
  "description":"Transfer SAP orders to the CRM system",
  "sourceSystemId":1,
  "destinationSystemId":2,
  "middlewareSystemId":3,
  "sourcePayloadFormat":"JSON",
  "destinationPayloadFormat":"XML",
  "hasMiddleware":true,
  "isSync":false
}
```

Direct flows use `hasMiddleware=false` and `middlewareSystemId=null`. Systems have
no fixed role: another integration can use `sourceSystemId=2` and
`destinationSystemId=1` without creating SAP again. One system ID can also appear
in several roles in a single integration.

List/detail response format, with dates and environment details shortened:

```json
{
  "id":10,
  "name":"Order transfer",
  "description":"Transfer SAP orders to the CRM system",
  "sourceSystemId":1,
  "sourceSystemName":"SAP",
  "destinationSystemId":2,
  "destinationSystemName":"CRM",
  "middlewareSystemId":3,
  "middlewareSystemName":"Integration Platform",
  "sourcePayloadFormat":"JSON",
  "destinationPayloadFormat":"XML",
  "hasMiddleware":true,
  "isSync":false,
  "status":"DRAFT",
  "reviewRequired":false,
  "environmentTypes":["DEV","PROD"]
}
```

Direct flows return null middleware ID/name. List/detail responses also include
`environmentConfigs`; normal responses do not include secret values.
`GET /integrations?keyword=...` searches integration names and system names in
all three roles.

## Validation and form behavior

- `sourceSystemId` and `destinationSystemId` are required positive IDs.
- `hasMiddleware=true` requires a positive `middlewareSystemId`. When false, it
  must be null or omitted. `hasMiddleware` and `isSync` are required.
- A missing system returns 404. A new inactive selection returns 409. An existing
  inactive selection may stay only in the same role of the same integration.
- Show existing inactive choices on edit screens; get new choices with `active=true`.
- Names are required and limited to 100 characters. Descriptions must not be null
  and are limited to 500 characters.
- Payload formats and flow/schema rules follow the [payload contract](payload-content.md).
  Environment `configTarget` values remain `DESTINATION` and `MIDDLEWARE`.
- Technical changes keep the DRAFT/review workflow. The first successful new sample
  removes incompatible old-flow data. Reload related lists after successful saves.
- Technical PUT keeps current settings, credentials, schemas, and mappings. If a
  review is needed, send `{ "confirmed": true }` to
  `POST /integrations/{id}/review/confirm`, then call complete separately.
  System name/active changes do not return integrations to draft. See the [workflow guide](integration-workflow.md).

## Map and environment filter

There is no separate map endpoint. Use `GET /integrations`. A node key must be the
system ID; do not add the role or integration ID to it. Keep the integration ID
in edge keys so different integrations between the same systems stay separate.
Example transformation:

```javascript
function graphData(integrations, environment = 'ALL') {
  const nodes = new Map();
  const edges = [];
  for (const row of integrations) {
    if (environment !== 'ALL' && !row.environmentTypes.includes(environment)) continue;
    const addNode = (id, name) => nodes.set(String(id), {id: String(id), label: name});
    const addEdge = (from, to, step) => edges.push({
      id: `${row.id}:${step}`, source: String(from), target: String(to), integrationId: row.id
    });
    addNode(row.sourceSystemId, row.sourceSystemName);
    addNode(row.destinationSystemId, row.destinationSystemName);
    if (row.hasMiddleware) {
      addNode(row.middlewareSystemId, row.middlewareSystemName);
      addEdge(row.sourceSystemId, row.middlewareSystemId, 'source-middleware');
      addEdge(row.middlewareSystemId, row.destinationSystemId, 'middleware-destination');
    } else {
      addEdge(row.sourceSystemId, row.destinationSystemId, 'source-destination');
    }
  }
  return {nodes: [...nodes.values()], edges};
}
```

This draws the request direction. Use `isSync` to show response directions
separately. Field mapping directions are unchanged. Apply the environment filter
on the client with `environmentTypes.includes(selectedEnvironment)`. `ALL` includes
every record. Records without environment settings appear only in ALL.
