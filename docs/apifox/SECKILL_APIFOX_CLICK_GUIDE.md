# Apifox 秒杀压测点击版步骤

这份说明默认你已经导入了：

- `docs/apifox/hmdp_microservices_apifox_collection.postman_collection.json`
- `docs/apifox/hmdp_seckill_load_test_environment.postman_environment.json`

导入后你会在集合里看到：

- `06 Seckill Load Test`

里面有 4 个请求：

1. `Query Seckill Voucher Detail`
2. `Send Code For Dynamic User`
3. `Login Dynamic User`
4. `Seckill Voucher Order`

## 一、先确认接口能跑通

1. 选中环境 `HM-DianPing Seckill Load Test Local`
2. 把环境变量里的 `voucherId` 改成你本地真实可用的秒杀券 ID
3. 手动运行 `Query Seckill Voucher Detail`
4. 确认返回里有 `success`

如果这一步都不通，后面压测没有意义。

## 二、创建自动化测试场景

1. 进入 Apifox 的 `自动化测试` 或 `测试场景`
2. 点击 `新建场景`
3. 场景名称填：`秒杀压测场景`
4. 在场景中按顺序加入 3 个步骤：
   - `Send Code For Dynamic User`
   - `Login Dynamic User`
   - `Seckill Voucher Order`

不要把 `Query Seckill Voucher Detail` 放进正式压测步骤里，它只适合联调检查。

## 三、导入测试数据 CSV

1. 在场景里找到 `测试数据` 或 `数据驱动`
2. 选择导入文件
3. 导入 [seckill_load_test_data_100.csv](D:\code\project\hm-dianping\hm-dianping\docs\apifox\seckill_load_test_data_100.csv)
4. 确认字段映射成功：
   - `phone`
   - `voucherId`

这一步很关键。

没有 CSV，你只是一个账号重复请求；有了 CSV，才是 100 个独立用户一起抢。

## 四、配置变量提取

### 第 1 步：Send Code For Dynamic User

在该步骤的 `后置操作 / 提取变量 / 响应提取` 中新增：

- 变量名：`login_code`
- 提取来源：响应体 JSON
- 表达式：`$.data`

### 第 2 步：Login Dynamic User

在该步骤的 `后置操作 / 提取变量 / 响应提取` 中新增：

- 变量名：`token`
- 提取来源：响应体 JSON
- 表达式：`$.data`

## 五、确认下单请求带 token

打开第 3 步 `Seckill Voucher Order`，确认它请求里使用的是：

- `Bearer {{token}}`

如果你是从我给你的集合导入的，这里通常已经带好了。

## 六、开启性能测试

如果你的 Apifox 版本支持场景性能测试，推荐这样配：

- 并发数：`100`
- 循环次数：`1`
- 启动方式：尽量同时启动
- 测试数据：按行分配，每个并发用户取一行

如果 Apifox 页面里有以下类似选项，优先这样选：

- `每次迭代使用一行数据`
- `并发用户不复用同一行数据`

## 七、结果怎么判断

压测跑完后，不要只看“接口都 200 了没”，而要看：

1. 成功请求数
2. 失败请求数
3. 平均响应时间 / P95
4. TPS
5. 成功下单数是否等于库存数

你的项目如果库存设成 `10`，那理想结果应该是：

- 成功下单大约 `10`
- 其余大多是 `库存不足`
- 不会出现成功数大于 `10`

## 八、如果你只想快速验证

最小可用流程：

1. 导入总集合
2. 导入环境
3. 新建场景，加入那 3 个步骤
4. 导入 CSV
5. 提取 `login_code` 和 `token`
6. 开 `100` 并发跑一次

## 九、常见误区

### 误区 1：导入集合就等于压测

不是。

导入集合只是把接口放进 Apifox，压测还需要：

- 测试场景
- 并发参数
- 测试数据

### 误区 2：同一个账号重复压测也能验证秒杀

不行。

秒杀要验证一人一单，必须多个不同账号并发请求，所以必须配 CSV。

### 误区 3：只看 HTTP 200 就说明压测成功

不对。

你的业务返回很多失败场景也是 200，需要看响应体里的：

- `success`
- `errorMsg`

## 十、你现在最省事的做法

直接用这几个文件：

- 总集合：[hmdp_microservices_apifox_collection.postman_collection.json](D:\code\project\hm-dianping\hm-dianping\docs\apifox\hmdp_microservices_apifox_collection.postman_collection.json)
- 环境：[hmdp_seckill_load_test_environment.postman_environment.json](D:\code\project\hm-dianping\hm-dianping\docs\apifox\hmdp_seckill_load_test_environment.postman_environment.json)
- 测试数据：[seckill_load_test_data_100.csv](D:\code\project\hm-dianping\hm-dianping\docs\apifox\seckill_load_test_data_100.csv)

这样你只要导总集合一次，就能在里面直接找到 `06 Seckill Load Test`。
