$ErrorActionPreference = 'Stop'
if (Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue) { throw '端口9090已占用，停止阶段4冒烟' }
$base = 'http://localhost:9090'
$jar = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\ruoyi-admin.jar'
$log = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\smoke4.log'
$errlog = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\smoke4.err.log'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.3\bin\mysql.exe'
# mock 模式经启动参数 gas.h5.mock-mode=true 启用；openid = MOCK-<code>
$smokePhone = '13800002222'
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

# mock 模式经启动参数开启，无需数据库配置
$p = Start-Process -FilePath 'java' -ArgumentList @('-jar', "`"$jar`"", '--shiro.user.captchaEnabled=false', '--gas.h5.mock-mode=true') -RedirectStandardOutput $log -RedirectStandardError $errlog -PassThru -WindowStyle Hidden
Write-Output "PID=$($p.Id)"
try {
    $up = $false
    for ($i = 0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2
        if ($p.HasExited) { break }
        try {
            $r = Invoke-WebRequest -Uri "$base/login" -UseBasicParsing -TimeoutSec 3
            if ($r.StatusCode -eq 200) { $up = $true; break }
        } catch {}
    }
    if (-not $up) { Write-Output 'APP_NOT_UP'; exit 1 }

    # HttpClient 禁自动重定向，精确捕获 302 Location
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false
    $http = New-Object System.Net.Http.HttpClient($handler)

    # 1. authorize 302 → callback（mock）
    $r1 = $http.GetAsync("$base/api/h5/wx/authorize?redirect=/index").Result
    Check 'AUTHORIZE_302' ($r1.StatusCode -eq [System.Net.HttpStatusCode]::Redirect -and "$($r1.Headers.Location)" -like '*callback*')
    $loc1 = "$($r1.Headers.Location)"
    if ($loc1.StartsWith('/')) { $loc1 = $base + $loc1 }

    # 2. callback 302 → /index?h5_code=；一次性登录码经 XHR 换 JWT
    $r2 = $http.GetAsync($loc1).Result
    $loc2 = "$($r2.Headers.Location)"
    Check 'CALLBACK_302_CODE' ($r2.StatusCode -eq [System.Net.HttpStatusCode]::Redirect -and $loc2 -like '*h5_code=*')
    $loginCode = ($loc2 -split 'h5_code=')[1]
    $login = Invoke-RestMethod -Uri "$base/api/h5/wx/exchange" -Method Post -Body @{ code = $loginCode }
    $token = $login.data
    Check 'EXCHANGE_TOKEN' ($login.code -eq 0 -and ![string]::IsNullOrEmpty($token))

    # 业务断言统一带 XHR 头（RuoYi GlobalExceptionHandler 的 ServiceException JSON 分支判定）
    $authHdr = @{ Authorization = "Bearer $token"; 'X-Requested-With' = 'XMLHttpRequest' }

    # 3. me：有 token → MEMBER
    $me = Invoke-RestMethod -Uri "$base/api/h5/c/me" -Headers $authHdr
    Check 'ME_MEMBER' ($me.code -eq 0 -and $me.data.role -eq 'MEMBER' -and $me.data.phoneBound -eq $false -and $me.data.memberId -gt 0)

    # 4. me：无 token → 401
    $no = Invoke-WebRequest -Uri "$base/api/h5/c/me" -SkipHttpErrorCheck
    Check 'ME_NO_TOKEN_401' ($no.StatusCode -eq 401)

    # 5. 伪造 token → 401
    $bad = Invoke-WebRequest -Uri "$base/api/h5/c/me" -SkipHttpErrorCheck -Headers @{ Authorization = "Bearer fake.token.here" }
    Check 'ME_BAD_TOKEN_401' ($bad.StatusCode -eq 401)

    # 6. 发送验证码 → 成功
    $s1 = Invoke-RestMethod -Uri "$base/api/h5/sms/send-code" -Method Post -ContentType 'application/json' -Body ('{"phone":"' + $smokePhone + '"}') -Headers $authHdr
    Check 'SEND_CODE_OK' ($s1.code -eq 0)

    # 7. 60 秒内重发 → 拒绝
    $s2 = Invoke-RestMethod -Uri "$base/api/h5/sms/send-code" -Method Post -ContentType 'application/json' -Body ('{"phone":"' + $smokePhone + '"}') -Headers $authHdr
    Check 'SEND_CODE_INTERVAL_REJECT' ($s2.code -ne 0 -and $s2.msg -like '*频繁*')

    # 8. 错误验证码绑定 → 拒绝
    $b1 = Invoke-RestMethod -Uri "$base/api/h5/sms/bind-phone" -Method Post -ContentType 'application/json' -Body ('{"phone":"' + $smokePhone + '","code":"000000"}') -Headers $authHdr
    Check 'BIND_BAD_CODE_REJECT' ($b1.code -ne 0)

    # 9. 手机号格式校验
    $s3 = Invoke-RestMethod -Uri "$base/api/h5/sms/send-code" -Method Post -ContentType 'application/json' -Body '{"phone":"12345"}' -Headers $authHdr
    Check 'SEND_CODE_BAD_PHONE_REJECT' ($s3.code -ne 0)

    # 10. 员工绑定：admin 非加气员/站管角色 → 拒绝
    $st = Invoke-RestMethod -Uri "$base/api/h5/staff/bind" -Method Post -ContentType 'application/json' -Body '{"loginName":"admin","password":"admin123"}' -Headers $authHdr
    Check 'STAFF_BIND_NON_STAFF_REJECT' ($st.code -ne 0 -and $st.msg -like '*加气员或站点管理员*')

    # 11. 员工绑定：错误密码 → 拒绝
    $st2 = Invoke-RestMethod -Uri "$base/api/h5/staff/bind" -Method Post -ContentType 'application/json' -Body '{"loginName":"admin","password":"wrong"}' -Headers $authHdr
    Check 'STAFF_BIND_BAD_PWD_REJECT' ($st2.code -ne 0)

    # 12. jssdk-config（mock 占位签名）
    $js = Invoke-RestMethod -Uri "$base/api/h5/wx/jssdk-config?url=http%3A%2F%2Flocalhost%3A8080%2F"
    Check 'JSSDK_CONFIG' ($js.code -eq 0 -and $js.data.signature -eq 'MOCK-MODE')

    # 13. H5 API 不受后台登录限制（匿名段可直接访问 authorize）
    Check 'H5_ANON_OK' ($r1.StatusCode -eq 302)

    Write-Output ("RESULT FAIL_COUNT=" + $fail)
}
finally {
    if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 1
    $l = Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue
    if ($l) { $l | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue } }
    RunSql "delete from gas_member where openid like 'MOCK-%'; delete from gas_staff_binding where openid like 'MOCK-%'; delete from gas_sms_log where phone='$smokePhone';"
    Write-Output 'CLEANED'
}
exit $fail
