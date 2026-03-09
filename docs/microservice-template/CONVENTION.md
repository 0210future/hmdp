# Service Communication Convention

## Gateway to service headers

Gateway validates token and injects these headers:

- `X-User-Id`
- `X-User-NickName`
- `X-User-Icon`

Services read these headers via interceptor and store into `UserContext`.

## Auth token

- Request header: `Authorization`
- Redis key: `login:token:{token}`

## API result wrapper

Use a unified result object:

- `success` boolean
- `errorMsg` string
- `data` object
- `total` number (optional)

## Feign calling rules

- Internal APIs should use `/internal/**` path prefix.
- All cross-service write APIs must be idempotent.
- Do not allow service A direct DB write into service B tables.