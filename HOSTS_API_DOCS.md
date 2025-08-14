# Clash Meta Hosts API 文档

## 概览

Clash Meta 提供了强大的 Hosts API，允许用户通过 RESTful API 动态管理 DNS hosts 映射。所有的 hosts 修改都会实时生效，无需重启服务。

## 基础信息

- **基础路径**: `/hosts`
- **认证**: 如果配置了 `secret`，需要在请求头中包含 `Authorization: Bearer <secret>`
- **内容类型**: `application/json`

## API 端点

### 1. 获取所有 Hosts 映射

**端点**: `GET /hosts`

**描述**: 获取当前所有 hosts 映射

**响应示例**:
```json
{
  "hosts": {
    "example.com": "192.168.1.100",
    "multi.example.com": ["192.168.1.101", "192.168.1.102"],
    "alias.example.com": "example.com",
    "localhost": "127.0.0.1"
  },
  "count": 4
}
```

### 2. 获取指定域名的映射

**端点**: `GET /hosts/{domain}`

**描述**: 获取指定域名的 hosts 映射

**路径参数**:
- `domain`: 要查询的域名

**响应示例**:
```json
{
  "domain": "example.com",
  "ip": "192.168.1.100"
}
```

**多 IP 响应示例**:
```json
{
  "domain": "multi.example.com",
  "ips": ["192.168.1.101", "192.168.1.102"]
}
```

**错误响应**:
```json
{
  "message": "host not found"
}
```

### 3. 添加新的 Hosts 映射

**端点**: `POST /hosts`

**描述**: 添加新的 hosts 映射。如果域名已存在，将返回错误。

**请求体**:
```json
{
  "domain": "example.com",
  "value": "192.168.1.100"
}
```

**支持的值类型**:
- 单个 IP 地址: `"192.168.1.100"`
- 多个 IP 地址: `["192.168.1.100", "192.168.1.101"]`
- 域名别名: `"another-domain.com"`

**成功响应**:
```json
{
  "message": "Host example.com added successfully",
  "domain": "example.com",
  "value": "192.168.1.100"
}
```

**错误响应**:
```json
{
  "message": "host example.com already exists, use PUT to update"
}
```

### 4. 更新 Hosts 映射

**端点**: `PUT /hosts`

**描述**: 更新现有的 hosts 映射，如果不存在则创建新的

**请求体**:
```json
{
  "domain": "example.com",
  "value": "192.168.1.200"
}
```

**成功响应**:
```json
{
  "message": "Host example.com updated successfully",
  "domain": "example.com",
  "value": "192.168.1.200"
}
```

### 5. 删除 Hosts 映射

**端点**: `DELETE /hosts/{domain}`

**描述**: 删除指定域名的 hosts 映射

**路径参数**:
- `domain`: 要删除的域名

**成功响应**:
```json
{
  "message": "Host example.com deleted successfully",
  "domain": "example.com"
}
```

**错误响应**:
```json
{
  "message": "host example.com not found"
}
```

### 6. 清空所有 Hosts 映射

**端点**: `DELETE /hosts`

**描述**: 清空所有 hosts 映射，只保留 localhost

**成功响应**:
```json
{
  "message": "All hosts cleared successfully",
  "count": 1
}
```

### 7. 批量操作 Hosts 映射

**端点**: `POST /hosts/batch`

**描述**: 批量执行 hosts 操作（添加、更新、删除）

**请求体**:
```json
{
  "operation": "add",
  "hosts": {
    "batch1.example.com": "192.168.1.201",
    "batch2.example.com": "192.168.1.202",
    "batch3.example.com": ["192.168.1.203", "192.168.1.204"]
  }
}
```

**支持的操作**:
- `add`: 添加新映射（如果已存在则失败）
- `update`: 更新映射（如果不存在则创建）
- `delete`: 删除映射（value 设为 null）

**删除示例**:
```json
{
  "operation": "delete",
  "hosts": {
    "batch1.example.com": null,
    "batch2.example.com": null
  }
}
```

**成功响应**:
```json
{
  "operation": "add",
  "total": 3,
  "success_count": 2,
  "error_count": 1,
  "results": {
    "batch1.example.com": "success",
    "batch2.example.com": "success"
  },
  "errors": {
    "batch3.example.com": "host batch3.example.com already exists, use PUT to update"
  }
}
```

## 便利操作模式

为了方便快速操作，我们推荐以下模式：

### 1. 快速添加模式
```bash
# 添加单个 IP
curl -X POST http://localhost:9090/hosts \
  -H "Content-Type: application/json" \
  -d '{"domain": "test.com", "value": "192.168.1.100"}'

# 添加多个 IP
curl -X POST http://localhost:9090/hosts \
  -H "Content-Type: application/json" \
  -d '{"domain": "cdn.test.com", "value": ["192.168.1.100", "192.168.1.101"]}'
```

### 2. 批量导入模式
```bash
# 批量添加多个域名
curl -X POST http://localhost:9090/hosts/batch \
  -H "Content-Type: application/json" \
  -d '{
    "operation": "add",
    "hosts": {
      "api.test.com": "192.168.1.100",
      "web.test.com": "192.168.1.101",
      "cdn.test.com": ["192.168.1.102", "192.168.1.103"]
    }
  }'
```

### 3. 安全更新模式
```bash
# 使用 PUT 确保更新而不是意外创建
curl -X PUT http://localhost:9090/hosts \
  -H "Content-Type: application/json" \
  -d '{"domain": "existing.com", "value": "192.168.1.200"}'
```

## 外部 API 修改的自动加载机制

### 🟢 立即生效的配置项

通过 API 修改这些配置会**立即生效**，无需重启服务：

#### **1. Hosts API 修改**
```bash
# ✅ 立即生效
curl -X POST http://127.0.0.1:9090/hosts \
  -H "Content-Type: application/json" \
  -d '{"domain": "test.com", "value": "192.168.1.100"}'
```

#### **2. PATCH 配置修改**
```bash
# ✅ 立即生效的配置项
curl -X PATCH -H "Authorization: Bearer secret" \
  -H "Content-Type: application/json" \
  -d '{"mode":"direct"}' http://127.0.0.1:9090/configs

# 支持立即生效的字段：
# - mode: 代理模式 (rule/global/direct)
# - log-level: 日志级别
# - ipv6: IPv6 开关
# - port, socks-port, mixed-port: 端口配置
# - allow-lan: 允许局域网访问
# - sniffing: 流量嗅探
# - tcp-concurrent: TCP 并发
# - interface-name: 网卡接口
```

#### **3. 内存实时更新机制**
所有 Hosts API 修改通过以下机制实现实时生效：

1. **内存更新**: 直接更新内存中的 trie 数据结构
2. **全局同步**: 立即替换全局的 `resolver.DefaultHosts`
3. **DNS 解析**: 新的 DNS 查询立即使用更新后的映射
4. **并发安全**: 使用读写锁确保线程安全

```go
// 实现原理
hostsMutex.Lock()
resolver.DefaultHosts = resolver.NewHosts(newTrie)
memoryHosts[domain] = hostValue
hostsMutex.Unlock()
// DNS 查询立即使用新映射
```

### 🟡 需要重新加载的配置项

这些配置修改需要触发完整的配置重载：

#### **1. PUT 完整配置**
```bash
# 🔄 触发配置重载，包含 hosts 在内的所有配置立即生效
curl -X PUT -H "Authorization: Bearer secret" \
  -H "Content-Type: application/json" \
  -d '{"payload":"完整YAML配置内容"}' \
  http://127.0.0.1:9090/configs
```

立即生效的配置包括：
- ✅ **Hosts 映射**: 所有 hosts 配置
- ✅ **代理规则**: rules 配置
- ✅ **代理服务器**: proxies 配置
- ✅ **DNS 配置**: DNS 服务器和设置
- ✅ **规则提供者**: rule-providers

#### **2. Override 配置机制**
```bash
# 修改 override.json 后需要触发重载
echo '{"hosts":{"api.test.com":"1.2.3.4"}}' > override.json

# 方法1: 通过空 payload 触发重载
curl -X PUT -H "Authorization: Bearer secret" \
  -d '{"payload":""}' http://127.0.0.1:9090/configs

# 方法2: 重启应用触发重载
adb shell am broadcast -a com.github.kr328.clash.action.STOP_CLASH
adb shell am broadcast -a com.github.kr328.clash.action.START_CLASH
```

#### **3. 配置加载顺序**
每次配置重载时的处理顺序：
```
1. 读取 config.yaml 基础配置
2. 应用 Override 持久化配置 (override.json)
3. 应用 Override 会话配置 (内存中)
4. 处理 hosts 解析并构建 trie 树
5. 更新全局 resolver 和 tunnel 配置
6. 立即生效，无需重启
```

### 🔴 必须重启的配置项

以下配置修改需要重启整个应用：

- ❌ **外部控制器端口**: `external-controller` 地址变更
- ❌ **TUN 模式配置**: `tun` 相关重大变更  
- ❌ **监听地址绑定**: `bind-address` 重大变更
- ❌ **核心组件升级**: 核心二进制文件更新

### 配置持久化策略

#### **1. API 动态配置 (临时)**
- **存储位置**: 仅内存中
- **生效时间**: 立即
- **持久性**: 重启后丢失
- **适用场景**: 临时调试、动态切换

```bash
# 临时添加 hosts，重启后自动清除
curl -X POST http://127.0.0.1:9090/hosts \
  -d '{"domain": "temp.test.com", "value": "192.168.1.100"}'
```

#### **2. Override 配置 (持久)**
- **存储位置**: `/data/data/com.github.kr328.clash/files/clash/override.json`
- **生效时间**: 下次配置加载时
- **持久性**: 重启后保留
- **适用场景**: 长期覆盖、配置调优

```bash
# 持久化 hosts 配置
echo '{
  "hosts": {
    "persistent.test.com": "192.168.1.100",
    "api.example.com": "192.168.1.101"
  },
  "mode": "rule"
}' > /data/data/com.github.kr328.clash/files/clash/override.json
```

#### **3. 配置文件 (永久)**
- **存储位置**: `config.yaml`
- **生效时间**: 配置重载时
- **持久性**: 永久保留
- **适用场景**: 基础配置、默认设置

### DNS 解析优先级

实际 DNS 解析时的查找顺序：

```
1. API 动态 Hosts (内存中，最高优先级)
   ↓ 未找到
2. Override 配置 Hosts (override.json)
   ↓ 未找到  
3. 配置文件 Hosts (config.yaml)
   ↓ 未找到
4. 系统 Hosts 文件 (/etc/hosts，如果启用)
   ↓ 未找到
5. 上游 DNS 服务器解析
```

### 实际应用场景

#### **调试场景**
```bash
# 1. 临时重定向API调试
curl -X POST http://127.0.0.1:9090/hosts \
  -d '{"domain": "api.prod.com", "value": "192.168.1.100"}'

# 2. 验证连接
curl http://api.prod.com/health

# 3. 调试完成后删除
curl -X DELETE http://127.0.0.1:9090/hosts/api.prod.com
```

#### **生产环境配置**
```bash
# 1. 持久化关键服务映射
echo '{
  "hosts": {
    "internal-api.company.com": "10.0.0.100",
    "cache-server.company.com": ["10.0.0.101", "10.0.0.102"]
  }
}' > override.json

# 2. 重载配置使其生效
curl -X PUT -H "Authorization: Bearer secret" \
  -d '{"payload":""}' http://127.0.0.1:9090/configs
```

### 循环引用检测

系统实现了智能的循环引用检测：

```json
// ❌ 会被检测并拒绝的循环引用
{
  "a.com": "b.com",
  "b.com": "c.com", 
  "c.com": "a.com"
}

// ✅ 允许的多级映射
{
  "alias.com": "real.com",
  "real.com": "192.168.1.100"
}
```

检测算法：
```go
// 遍历域名链，检测循环
visited := make(map[string]bool)
for checkDomain != "" {
    if visited[checkDomain] || checkDomain == domain {
        return fmt.Errorf("circular domain mapping detected")
    }
    visited[checkDomain] = true
    // 继续检查下一级域名...
}
```

### 性能优化特性

- **Trie 树结构**: O(m) 时间复杂度查找，m 为域名长度
- **内存复制**: 更新时采用复制策略，确保读取时零锁竞争
- **批量优化**: 批量操作共享 trie 重建过程
- **读写分离**: 读操作使用读锁，写操作使用写锁

## 错误处理

### 常见错误码
- `400 Bad Request`: 请求格式错误或缺少必要参数
- `401 Unauthorized`: 认证失败（secret 不正确）
- `404 Not Found`: 指定的域名不存在
- `500 Internal Server Error`: 服务器内部错误

### 错误响应格式
```json
{
  "message": "详细的错误描述"
}
```

### 常见错误处理
```bash
# 检查API是否可用
curl -i http://127.0.0.1:9090/hosts

# 验证认证配置
curl -H "Authorization: Bearer your-secret" http://127.0.0.1:9090/hosts

# 检查域名是否存在
curl http://127.0.0.1:9090/hosts/example.com
```

## 使用总结和最佳实践

### 🎯 API 使用优先级推荐

根据不同场景选择合适的配置方法：

#### **1. 临时调试 → Hosts API**
```bash
# ✅ 最佳选择：临时测试、快速验证
curl -X POST http://127.0.0.1:9090/hosts \
  -d '{"domain": "debug.test.com", "value": "192.168.1.100"}'

# 优势：立即生效，重启后自动清除
# 适用：开发调试、临时重定向、A/B测试
```

#### **2. 长期配置 → Override 文件**
```bash
# ✅ 最佳选择：持久化配置、生产环境
echo '{"hosts": {"api.prod.com": "10.0.0.100"}}' > override.json

# 优势：重启后保留，不影响原配置
# 适用：生产环境、长期覆盖、配置微调
```

#### **3. 基础配置 → YAML 文件**
```yaml
# ✅ 最佳选择：默认配置、标准设置
hosts:
  localhost: 127.0.0.1
  api.company.com: 10.0.0.100
```

### 🚀 高效操作模式

#### **快速开发模式**
```bash
#!/bin/bash
# quick-hosts.sh - 快速 hosts 管理脚本

API_BASE="http://127.0.0.1:9090/hosts"
SECRET="your-secret"  # 如果需要认证

# 快速添加
add_host() {
    curl -X POST "$API_BASE" \
        ${SECRET:+-H "Authorization: Bearer $SECRET"} \
        -H "Content-Type: application/json" \
        -d "{\"domain\": \"$1\", \"value\": \"$2\"}"
}

# 快速删除
del_host() {
    curl -X DELETE "$API_BASE/$1" \
        ${SECRET:+-H "Authorization: Bearer $SECRET"}
}

# 查看所有
list_hosts() {
    curl "$API_BASE" \
        ${SECRET:+-H "Authorization: Bearer $SECRET"} | jq
}

# 使用示例
add_host "api.test.com" "192.168.1.100"
add_host "cdn.test.com" "192.168.1.101"
list_hosts
```

#### **批量导入模式**
```bash
# 从文件批量导入 hosts
cat hosts.json
{
  "operation": "add",
  "hosts": {
    "api1.test.com": "192.168.1.100",
    "api2.test.com": "192.168.1.101",
    "cdn.test.com": ["192.168.1.102", "192.168.1.103"]
  }
}

curl -X POST http://127.0.0.1:9090/hosts/batch \
  -H "Content-Type: application/json" \
  -d @hosts.json
```

#### **环境切换模式**
```bash
# 开发环境配置
switch_to_dev() {
    curl -X POST http://127.0.0.1:9090/hosts/batch \
        -d '{
            "operation": "update",
            "hosts": {
                "api.company.com": "192.168.1.100",
                "db.company.com": "192.168.1.101",
                "cache.company.com": "192.168.1.102"
            }
        }'
}

# 生产环境配置
switch_to_prod() {
    curl -X POST http://127.0.0.1:9090/hosts/batch \
        -d '{
            "operation": "update", 
            "hosts": {
                "api.company.com": "10.0.0.100",
                "db.company.com": "10.0.0.101",
                "cache.company.com": "10.0.0.102"
            }
        }'
}
```

### 🛡️ 安全和稳定性建议

#### **1. 认证安全**
```bash
# ✅ 推荐：使用强密码
echo '{"external-controller": "127.0.0.1:9090", "secret": "your-strong-secret-key"}' > override.json

# ❌ 避免：生产环境无认证
# "external-controller": "0.0.0.0:9090"  # 危险！
```

#### **2. 网络安全**
```bash
# ✅ 推荐：仅本地访问
"external-controller": "127.0.0.1:9090"

# ⚠️ 谨慎：局域网访问（仅开发环境）
"external-controller": "0.0.0.0:9090"
```

#### **3. 配置备份**
```bash
# 定期备份当前配置
backup_config() {
    DATE=$(date +%Y%m%d_%H%M%S)
    curl -H "Authorization: Bearer $SECRET" \
         http://127.0.0.1:9090/hosts > "hosts_backup_$DATE.json"
    cp override.json "override_backup_$DATE.json"
}
```

#### **4. 错误恢复**
```bash
# 清除所有动态 hosts（紧急恢复）
curl -X DELETE -H "Authorization: Bearer $SECRET" \
     http://127.0.0.1:9090/hosts

# 重载配置文件（恢复到原始状态）
curl -X PUT -H "Authorization: Bearer $SECRET" \
     -d '{"payload":""}' http://127.0.0.1:9090/configs
```

### 📊 性能监控和调试

#### **1. 验证 Hosts 生效**
```bash
# 方法1: 通过 nslookup（如果配置了DNS端口）
nslookup api.test.com 127.0.0.1:1053

# 方法2: 通过实际请求测试
curl -v http://api.test.com/health

# 方法3: 检查 Clash 日志
curl -N -H "Authorization: Bearer $SECRET" \
     http://127.0.0.1:9090/logs | grep -i "hosts\|dns"
```

#### **2. 性能监控**
```bash
# 检查内存使用
curl -H "Authorization: Bearer $SECRET" \
     http://127.0.0.1:9090/memory

# 监控实时流量
curl -N -H "Authorization: Bearer $SECRET" \
     http://127.0.0.1:9090/traffic
```

### 📚 总结

**Hosts API 的核心优势**：
- ✅ **实时生效**: 无需重启，立即应用
- ✅ **动态管理**: 支持临时和持久化配置
- ✅ **批量操作**: 高效处理大量映射
- ✅ **安全性**: 循环引用检测和并发安全
- ✅ **灵活性**: 支持IP、域名、多IP映射

**最佳实践建议**：
1. **开发调试**: 使用 Hosts API 进行临时映射
2. **生产环境**: 使用 Override 配置进行持久化
3. **安全第一**: 配置强认证和本地绑定
4. **定期备份**: 保存重要的配置状态
5. **监控验证**: 确保配置按预期生效

通过合理使用这些API和配置机制，你可以实现高效、安全、灵活的 hosts 管理！🎉

