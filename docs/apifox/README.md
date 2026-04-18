# Apifox Import Files

## Files
- hmdp_microservices_apifox_collection.postman_collection.json
- hmdp_microservices_apifox_environment.postman_environment.json

## Import Steps (Apifox)
1. Open Apifox -> Import
2. Choose Postman Collection
3. Select `hmdp_microservices_apifox_collection.postman_collection.json`
4. Optional: import `hmdp_microservices_apifox_environment.postman_environment.json` as environment
5. Keep folder structure to preserve category grouping

## Categories
- 01 User Auth
- 02 Shop
- 03 Blog
- 04 AI
- 05 Voucher & Order

## Variables to configure after import
- baseUrl (default: http://127.0.0.1:8080)
- token
- login_code
- phone
- shopId / typeId / voucherId
- x / y

## Recommended Run Order
1. Send Code
2. Login
3. Current User
4. Other folders

## Notes
- AI interfaces currently require `Authorization: Bearer <token>`
- `Seckill Voucher Order` requires a valid seckill voucher id and a restarted gateway if route config was recently changed
