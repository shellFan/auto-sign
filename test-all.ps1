# 自动签到系统 API 测试脚本

$baseUrl = "http://localhost:8080"

# 1. 测试登录
Write-Host "=== 1. 测试登录 ===" -ForegroundColor Cyan
$loginBody = '{"username":"admin","password":"admin123"}'
$loginResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method POST -ContentType "application/json" -Body $loginBody
$token = $loginResp.data.token
Write-Host "登录成功: $($loginResp.data.username), Token: $($token.Substring(0, 20))..." -ForegroundColor Green

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

# 2. 测试获取用户信息
Write-Host "`n=== 2. 测试获取用户信息 ===" -ForegroundColor Cyan
$meResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/me" -Method GET -Headers $headers
Write-Host "用户信息: $($meResp.data.username) - $($meResp.data.nickname)" -ForegroundColor Green

# 3. 测试创建平台账号
Write-Host "`n=== 3. 测试创建平台账号 ===" -ForegroundColor Cyan
$accountBody = '{"platform":"weibo","accountName":"测试微博账号","remark":"测试用"}'
$createAccountResp = Invoke-RestMethod -Uri "$baseUrl/api/account/create" -Method POST -Headers $headers -Body $accountBody
Write-Host "创建账号结果: code=$($createAccountResp.code), msg=$($createAccountResp.message)" -ForegroundColor Green

# 4. 测试获取账号列表
Write-Host "`n=== 4. 测试获取账号列表 ===" -ForegroundColor Cyan
$accountsResp = Invoke-RestMethod -Uri "$baseUrl/api/account/list" -Method GET -Headers $headers
Write-Host "账号数量: $($accountsResp.data.Count)" -ForegroundColor Green
$accountId = $null
if ($accountsResp.data.Count -gt 0) {
    $accountId = $accountsResp.data[0].id
    Write-Host "第一个账号ID: $accountId, 平台: $($accountsResp.data[0].platform)" -ForegroundColor Green
}

# 5. 测试获取账号详情
if ($accountId) {
    Write-Host "`n=== 5. 测试获取账号详情 ===" -ForegroundColor Cyan
    $accountResp = Invoke-RestMethod -Uri "$baseUrl/api/account/$accountId" -Method GET -Headers $headers
    Write-Host "账号详情: $($accountResp.data.accountName), 登录状态: $($accountResp.data.loginStatus)" -ForegroundColor Green
}

# 6. 测试初始化默认超话
if ($accountId) {
    Write-Host "`n=== 6. 测试初始化默认超话 ===" -ForegroundColor Cyan
    $initResp = Invoke-RestMethod -Uri "$baseUrl/api/weibo/topic/init-default?accountId=$accountId" -Method POST -Headers $headers
    Write-Host "初始化超话结果: code=$($initResp.code), msg=$($initResp.message)" -ForegroundColor Green
}

# 7. 测试获取超话列表
Write-Host "`n=== 7. 测试获取超话列表 ===" -ForegroundColor Cyan
$topicsResp = Invoke-RestMethod -Uri "$baseUrl/api/weibo/topic/list" -Method GET -Headers $headers
Write-Host "超话数量: $($topicsResp.data.Count)" -ForegroundColor Green
foreach ($topic in $topicsResp.data) {
    Write-Host "  - $($topic.topicName): $($topic.topicUrl)" -ForegroundColor Gray
}

# 8. 测试创建超话
if ($accountId) {
    Write-Host "`n=== 8. 测试创建超话 ===" -ForegroundColor Cyan
    $topicBody = "{`"accountId`":`"$accountId`",`"topicName`":`"测试超话`",`"topicUrl`":`"https://weibo.com/p/test`",`"sort`":10}"
    $createTopicResp = Invoke-RestMethod -Uri "$baseUrl/api/weibo/topic/create" -Method POST -Headers $headers -Body $topicBody
    Write-Host "创建超话结果: code=$($createTopicResp.code), msg=$($createTopicResp.message)" -ForegroundColor Green
}

# 9. 测试创建签到任务
if ($accountId) {
    Write-Host "`n=== 9. 测试创建签到任务 ===" -ForegroundColor Cyan
    $taskBody = "{`"accountId`":`"$accountId`",`"taskName`":`"微博超话签到`",`"cronExpr`":`"0 20 8 * * ?`",`"retryCount`":1}"
    $createTaskResp = Invoke-RestMethod -Uri "$baseUrl/api/sign/task/create" -Method POST -Headers $headers -Body $taskBody
    Write-Host "创建任务结果: code=$($createTaskResp.code), msg=$($createTaskResp.message)" -ForegroundColor Green
}

# 10. 测试获取任务列表
Write-Host "`n=== 10. 测试获取任务列表 ===" -ForegroundColor Cyan
$tasksResp = Invoke-RestMethod -Uri "$baseUrl/api/sign/task/list" -Method GET -Headers $headers
Write-Host "任务数量: $($tasksResp.data.Count)" -ForegroundColor Green
foreach ($task in $tasksResp.data) {
    Write-Host "  - $($task.taskName): $($task.cronExpr)" -ForegroundColor Gray
}

# 11. 测试获取签到记录
Write-Host "`n=== 11. 测试获取签到记录 ===" -ForegroundColor Cyan
$recordsResp = Invoke-RestMethod -Uri "$baseUrl/api/sign/record/list?pageNum=1&pageSize=10" -Method GET -Headers $headers
Write-Host "记录数量: $($recordsResp.data.total)" -ForegroundColor Green

# 12. 测试启用/禁用账号
if ($accountId) {
    Write-Host "`n=== 12. 测试禁用账号 ===" -ForegroundColor Cyan
    $disableResp = Invoke-RestMethod -Uri "$baseUrl/api/account/$accountId/disable" -Method POST -Headers $headers
    Write-Host "禁用结果: code=$($disableResp.code)" -ForegroundColor Green
    
    Write-Host "`n=== 13. 测试启用账号 ===" -ForegroundColor Cyan
    $enableResp = Invoke-RestMethod -Uri "$baseUrl/api/account/$accountId/enable" -Method POST -Headers $headers
    Write-Host "启用结果: code=$($enableResp.code)" -ForegroundColor Green
}

Write-Host "`n=== 测试完成 ===" -ForegroundColor Yellow
