$ErrorActionPreference = 'Stop'
foreach ($occupiedPort in @(9090, 5300)) {
    if (Get-NetTCPConnection -LocalPort $occupiedPort -State Listen -ErrorAction SilentlyContinue) { throw "端口$occupiedPort已占用，停止阶段4 E2E冒烟" }
}
# 端到端联调：后端(9090) + 前端 vite dev(8080) + mock 授权全链路
$base = 'http://localhost:9090'
$h5 = 'http://localhost:5300'
$jar = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\ruoyi-admin.jar'
$backendLog = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\e2e-backend.log'
$frontendLog = 'd:\Project\workspace\gas\h5\e2e-frontend.log'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.3\bin\mysql.exe'
$mockOpenid = 'MOCK-SMOKE-4'
$fail = 0

function Check($name, $cond) {
    if ($cond) { Write-Output ("PASS " + $name) }
    else { Write-Output ("FAIL " + $name); $script:fail++ }
}
function RunSql($sql) {
    $tmp = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tmp, $sql, (New-Object System.Text.UTF8Encoding($false)))
    cmd /c "`"$mysql`" -uroot -proot ry --default-character-set=utf8mb4 < `"$tmp`" 2>nul" | Out-Null
    Remove-Item $tmp -ErrorAction SilentlyContinue
}

# 绝对回跳地址必须与 h5.base.url 同源；测试结束后恢复原开发/生产配置
$oldH5BaseRows = @(& $mysql -uroot -proot ry -N -e "select config_value from gas_config where config_key='h5.base.url';" 2>$null)
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$hadH5Base = $oldH5BaseRows.Count -gt 0
$oldH5Base = if ($hadH5Base) { $oldH5BaseRows[0] } else { '' }
RunSql "insert into gas_config(config_key,config_value,update_by,update_time) values('h5.base.url','$h5','smoke4e2e',sysdate()) on duplicate key update config_value=values(config_value),update_by='smoke4e2e',update_time=sysdate();"

$backend = Start-Process -FilePath 'java' -ArgumentList @('-jar', "`"$jar`"", '--shiro.user.captchaEnabled=false', '--gas.h5.mock-mode=true') -RedirectStandardOutput $backendLog -WindowStyle Hidden -PassThru
$frontend = Start-Process -FilePath 'cmd' -ArgumentList @('/c', 'npm run dev') -WorkingDirectory 'd:\Project\workspace\gas\h5' -RedirectStandardOutput $frontendLog -WindowStyle Hidden -PassThru
Write-Output "BACKEND_PID=$($backend.Id) FRONTEND_PID=$($frontend.Id)"
try {
    $upB = $false; $upF = $false
    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2
        if (-not $upB) {
            try { $r = Invoke-WebRequest -Uri "$base/login" -UseBasicParsing -TimeoutSec 3; if ($r.StatusCode -eq 200) { $upB = $true } } catch {}
        }
        if (-not $upF) {
            try { $r = Invoke-WebRequest -Uri "$h5/" -UseBasicParsing -TimeoutSec 3; if ($r.StatusCode -eq 200) { $upF = $true } } catch {}
        }
        if ($upB -and $upF) { break }
    }
    Check 'BACKEND_UP' $upB
    Check 'FRONTEND_UP' $upF
    if (-not ($upB -and $upF)) { exit 1 }

    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false
    $http = New-Object System.Net.Http.HttpClient($handler)

    # 1. 前端页面可达（SPA index.html）
    $page = $http.GetAsync("$h5/index").Result
    Check 'H5_PAGE_INDEX' ($page.StatusCode -eq [System.Net.HttpStatusCode]::OK -and $page.Content.ReadAsStringAsync().Result -like '*app*')

    # 2. 完整授权链路：authorize → mock callback → 前端一次性登录码 → XHR 换 JWT
    $authUrl = "$base/api/h5/wx/authorize?redirect=$([uri]::EscapeDataString("$h5/index"))"
    $r1 = $http.GetAsync($authUrl).Result
    $loc1 = "$($r1.Headers.Location)"
    Check 'E2E_AUTHORIZE' ($r1.StatusCode -eq [System.Net.HttpStatusCode]::Redirect -and $loc1 -like '*callback*')
    if ($loc1.StartsWith('/')) { $loc1 = $base + $loc1 }
    $r2 = $http.GetAsync($loc1).Result
    $loc2 = "$($r2.Headers.Location)"
    Check 'E2E_CALLBACK_TO_FRONTEND' ($r2.StatusCode -eq [System.Net.HttpStatusCode]::Redirect -and $loc2.StartsWith($h5) -and $loc2 -like '*h5_code=*')
    $loginCode = ($loc2 -split 'h5_code=')[1]
    $login = Invoke-RestMethod -Uri "$base/api/h5/wx/exchange" -Method Post -Body @{ code = $loginCode }
    $token = $login.data
    Check 'E2E_EXCHANGE_TOKEN' ($login.code -eq 0 -and ![string]::IsNullOrEmpty($token))

    # 3. 回跳 URL 恰是前端路由页（/index）
    $back = $http.GetAsync($loc2).Result
    Check 'E2E_LANDING_PAGE' ($back.StatusCode -eq [System.Net.HttpStatusCode]::OK)

    # 4. 前端代理到后端：通过 8080 的 /api 代理访问 jssdk-config（验证 vite proxy）
    $js = Invoke-RestMethod -Uri "$h5/api/h5/wx/jssdk-config?url=http%3A%2F%2Flocalhost%3A8080%2Findex"
    Check 'E2E_PROXY_API' ($js.code -eq 0 -and $js.data.signature -eq 'MOCK-MODE')

    # 5. 带 token 走代理访问 me
    $me = Invoke-RestMethod -Uri "$h5/api/h5/c/me" -Headers @{ Authorization = "Bearer $token"; 'X-Requested-With' = 'XMLHttpRequest' }
    Check 'E2E_ME_VIA_PROXY' ($me.code -eq 0 -and $me.data.role -eq 'MEMBER')

    Write-Output ("RESULT FAIL_COUNT=" + $fail)
}
finally {
    foreach ($p in @($backend, $frontend)) {
        if ($p -and -not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
    }
    # npm 会派生 node 子进程，按端口兜底清理
    Start-Sleep -Seconds 1
    foreach ($port in @(9090, 5300)) {
        $l = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        if ($l) { $l | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue } }
    }
    # 清理 mock 授权产生的测试会员并恢复授权配置
    RunSql "delete from gas_member where openid like 'MOCK-%';"
    if ($hadH5Base) {
        RunSql "update gas_config set config_value='$oldH5Base',update_by='smoke4e2e-restore',update_time=sysdate() where config_key='h5.base.url';"
    } else {
        RunSql "delete from gas_config where config_key='h5.base.url';"
    }
    Write-Output 'CLEANED'
}
exit $fail
