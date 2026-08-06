# 小蚕活动平台

一个自托管的活动查询、提醒和大牌券自动领取工具。项目包含 Spring Boot 后端与 Vue 3 前端，Docker 会同时启动 MySQL、Redis、后端和前端，因此部署电脑不需要预装 Java 或数据库。

## 自动领取大牌卷

导航中的“自动领取大牌卷”使用抓包确认的接口：

```text
POST https://gw.xiaocantech.com/rpc
servername: SilkwormVip
methodname: VipRightsService.GrabExtraBrandCard
body: {"type":99,"silk_id":你的 silk_id}
```

执行节奏固定为：

- 每天 `09:29:58` 启动准备任务。
- 直到 `09:30:00.000` 才会发送第一个领券请求。
- 后续以 `100-400ms` 随机间隔补请求，默认最多 12 次，最晚到 `09:30:03.000`。
- 领取成功、已领取、已抢完、需要验证、登录态失效或明确业务失败会立即停止；网络超时和连接失败会继续下一次。

这不是绕过小蚕验证的功能。遇到 `verify_method != 0` 会记录“需要验证”并停止。上游库存、风控、网络质量和登录态都可能影响结果，不能保证领取成功。

`X-Sivir` 来自你自己的小蚕抓包请求头；它和本项目网页登录用的 `Token` 不是同一个东西。保存后页面只显示掩码，真实值不会通过读取接口回显。登录态失效后，需要重新抓包并覆盖保存。

## 监控发现后自动抢单

自动抢单默认关闭。可以使用“门店关键字”监控，也可以使用首页铃铛创建的指定门店监控。配置步骤：

1. 先在“自动领取大牌卷”页面保存当前账号的 `silk_id` 和 `X-Sivir`。
2. 在“监控管理”新增/编辑“门店关键字”监控，或在首页通过目标门店的铃铛创建指定门店监控，打开“启用自动抢单”。
3. 设置最大请求次数和 `100-400ms` 范围内的随机间隔，然后保存并启用监控。
4. 关键词监控默认只对门店名称完全匹配的结果执行；同名但存在多个不同门店 ID 时会跳过，避免误抢。
5. 系统每 2 秒检查一次门店活动；活动未到开始时间或没有库存时只观察，到可抢时间后才发抢单请求。
6. 同一门店活动每天只进入一次抢单流程；网络失败允许下一轮继续。图文评价返利严格高于无需评价时选图文评价，否则选无需评价；抢单记录可在监控卡片的“抢单记录”中查看。

自动抢单使用抓包确认的 `SilkwormService.GrabPromotionQuota` 接口，并按活动实际时间、库存、评价条件和平台参数发起请求。网络异常会按配置重试；成功、售罄、已领取、登录态失效、需要验证或明确业务失败会停止。不实现验证码、滑块或风控绕过，也不保证一定抢到。

## Docker 部署

### 首次部署

在新的 Mac 安装并启动 Docker Desktop 后执行：

```bash
git clone https://github.com/qq1739920004/xiaochan.git
cd xiaochan
cp .env.example .env
```

编辑 `.env`：保留数据库默认值即可；若使用 Cloudflare Tunnel，填入已有隧道的 `CLOUDFLARE_TUNNEL_TOKEN`。该文件已被 Git 忽略，不能提交真实 token。

没有 Cloudflare Tunnel 时：

```bash
docker compose up -d --build
```

使用 Cloudflare Tunnel 时：

```bash
docker compose --profile tunnel up -d --build
```

本机访问地址为 `http://localhost:8080`。在 Cloudflare Zero Trust 的隧道 Public Hostname 中，将你的域名路由到：

```text
http://frontend:80
```

查看运行状态：

```bash
docker compose ps
docker compose logs -f cloudflared
```

### 创建项目网页登录 Token（不使用微信推送）

网页的项目 Token 只用于登录本项目，和 `X-Sivir` 无关。若不使用 SPT/WxPusher 注册，可在部署后生成并插入一个本地账号：

```bash
PROJECT_TOKEN=$(openssl rand -hex 16)
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" xiaocan' <<SQL
INSERT INTO \`user\` (\`token\`, \`spt\`) VALUES ('$PROJECT_TOKEN', 'no_wxpusher');
SQL
echo "$PROJECT_TOKEN"
```

把终端最后输出的 `PROJECT_TOKEN` 填入网页的“已有 Token”即可。请妥善保管它。

### 配置自动领取

1. 在网页登录后打开“自动领取大牌卷”。
2. 从你自己的小蚕抓包请求中填写 `silk_id` 和 `X-Sivir`。
3. 确认默认“最大请求次数 12”和“100-400ms”间隔，保存后启用。
4. 可使用“手动试领”验证签名和登录态；它只会立即请求一次。
5. 次日查看执行历史，确认任务是否在 09:30 附近产生记录。

### 已部署实例更新

先拉取代码并执行新表迁移。迁移使用 `CREATE TABLE IF NOT EXISTS`，不会删除现有数据。

```bash
git pull
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" xiaocan' < deploy/migrations/20260731_brand_card.sql
docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" xiaocan' < deploy/migrations/20260803_store_auto_claim.sql
docker compose --profile tunnel up -d --build
```

不用 Cloudflare Tunnel 时，最后一条换成：

```bash
docker compose up -d --build
```

### 拉取镜像超时或代理

若 `cloudflare/cloudflared`、MySQL 或构建时下载依赖出现 `connect timeout`，终端代理和 Docker Desktop 的镜像拉取代理是两回事。

1. 在 Docker Desktop 的 Settings > Resources > Proxies 中启用 Manual proxy configuration。
2. 填写本机可用的 HTTP 代理地址。例如你的代理软件在 1082 提供的是 HTTP 代理，可填 `http://host.docker.internal:1082`；如果 1082 是 SOCKS 端口，改用该软件提供的 HTTP 端口。
3. Apply & Restart Docker Desktop 后重新执行 `docker compose ... up -d --build`。

## 技术结构

```text
frontend (Nginx, port 80)
  -> /api/ 反向代理
backend (Spring Boot, port 10234)
  -> MySQL 8.4 + Redis 7
cloudflared (optional)
  -> frontend:80
```

## 开发验证

后端：

```bash
mvn test
```

前端：

```bash
cd frontend
npm ci --include=dev
npm run build
```

## 上游项目

- 后端上游：[lyrric/xiaochan](https://github.com/lyrric/xiaochan)
- 前端上游：[lyrric/xiaocan-front](https://github.com/lyrric/xiaocan-front)
