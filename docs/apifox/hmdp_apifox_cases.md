# HM-DianPing Test Cases (Markdown)

> Generated from `hmdp_apifox_cases.csv`

## BlogFeed

| CaseID | Method | Path | Precondition | Expected |
|---|---|---|---|---|
| B01 | POST | `/blog` | Login token | blog id returned |
| B03 | GET | `/blog/{id}` | Blog exists | detail returned |
| B04 | PUT | `/blog/like/{id}` | Login token | like toggled |
| B06 | GET | `/blog/likes/{id}` | Blog exists | top users list |
| B07 | GET | `/blog/of/user?id=&current=` | User exists | paged list |
| B08 | GET | `/blog/of/me?current=` | Login token | paged list |
| B09 | GET | `/blog/hot?current=` | None | paged hot list |
| B10 | GET | `/blog/of/follow?lastId=&offset=` | Login token | ScrollResult |

## Comment

| CaseID | Method | Path | Precondition | Expected |
|---|---|---|---|---|
| C01 | POST | `/blog-comments` | Login token | comment created |
| C02 | POST | `/blog-comments` | Login token | reply created |
| C03 | GET | `/blog-comments/of/blog?blogId=&current=` | Blog exists | paged comments |

## Follow

| CaseID | Method | Path | Precondition | Expected |
|---|---|---|---|---|
| F01 | PUT | `/follow/{id}/true` | Login token | success=true |
| F03 | PUT | `/follow/{id}/false` | Login token | success=true |
| F05 | GET | `/follow/or/not/{id}` | Login token | boolean |
| F07 | GET | `/follow/common/{id}` | Login token | list |

## Seckill

| CaseID | Method | Path | Precondition | Expected |
|---|---|---|---|---|
| V04 | POST | `/voucher-order/seckill/{id}` | Login token | order id or business fail |

## Security

| CaseID | Method | Path | Precondition | Expected |
|---|---|---|---|---|
| A01 | GET | `/blog/hot` | None | public access |
| A02 | GET | `/blog/of/me` | No token | 401 |
| A03 | GET | `/user/me` | Token provided | ttl refreshed |

## Shop

| CaseID | Method | Path | Precondition | Expected |
|---|---|---|---|---|
| S01 | GET | `/shop/{id}` | Shop exists | shop detail |
| S04 | PUT | `/shop` | Login token | updated and cache deleted |
| S05 | GET | `/shop/of/type?typeId=&current=` | None | DB page |
| S06 | GET | `/shop/of/type?typeId=&current=&x=&y=` | Geo params | nearby sorted |
| S08 | GET | `/shop/of/name?name=&current=` | None | search list |

## ShopType

| CaseID | Method | Path | Precondition | Expected |
|---|---|---|---|---|
| T01 | GET | `/shop-type/list` | None | db->cache |
| T02 | GET | `/shop-type/list` | Cache exists | cache hit |

## UserAuth

| CaseID | Method | Path | Precondition | Expected |
|---|---|---|---|---|
| U01 | POST | `/user/code?phone={phone_valid}` | None | success=true |
| U02 | POST | `/user/code?phone={phone_invalid}` | None | success=false |
| U03 | POST | `/user/login` | Code exists | token returned |
| U04 | POST | `/user/login` | Wrong code | success=false |
| U05 | GET | `/user/me` | Login token | return current user |
| U06 | GET | `/user/me` | No token | 401 |
| U07 | POST | `/user/logout` | Login token | success=true |
| U09 | POST | `/user/sign` | Login token | success=true |
| U10 | GET | `/user/sign/count` | Signed data exists | return integer |

## Voucher

| CaseID | Method | Path | Precondition | Expected |
|---|---|---|---|---|
| V02 | POST | `/voucher/seckill` | Login token | seckill voucher created |
| V03 | GET | `/voucher/list/{shopId}` | None | voucher list |

