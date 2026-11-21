$ErrorActionPreference = 'Stop'
if (Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue) { throw '端口9090已占用，停止阶段5冒烟' }
$base = 'http://localhost:9090'
$jar = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\ruoyi-admin.jar'
$log = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\smoke5.log'
$errlog = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\smoke5.err.log'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.3\bin\mysql.exe'
$qr = 'SMOKE5-QR'
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

# 准备：采购商 + 商户 + 站点 + 枪 + 价格 + 库存池
RunSql @"
delete from gas_buyer where buyer_name='冒烟5采购商';
insert into gas_buyer (buyer_name, short_name, status, create_time) values ('冒烟5采购商','冒烟5','0',sysdate());
set @buyer5 = last_insert_id();
insert into sys_dept (parent_id, ancestors, dept_name, order_num, status, del_flag, create_by, create_time) values (100,'0,100','冒烟5部门',99,'0','0','smoke',sysdate());
set @dept5 = last_insert_id();
insert into gas_station (station_code, station_name, buyer_id, dept_id, qr_token, status, create_time) values ('SMK5','冒烟5站点',@buyer5,@dept5,'$qr','0',sysdate());
set @station5 = last_insert_id();
insert into gas_buyer_merchant (buyer_id, channel, merchant_no, status, create_by, create_time) values (@buyer5,'LAKALA','MKT-SMK5','0','smoke',sysdate());
insert into gas_gun (station_id, gun_no, gas_type, status, create_time) values (@station5, 1, 'CNG', '0', sysdate());
set @gun5 = last_insert_id();
insert into gas_gun_price (gun_id, price, effective_from, create_time) values (@gun5, 3.5000, date_sub(now(), interval 1 minute), sysdate());
insert into gas_inventory_pool (station_id, buyer_id, gas_type, total_in, adjusted, consumed, reserved, update_time) values (@station5, @buyer5, 'CNG', 1000, 0, 0, 0, sysdate());
"@

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

    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false
    $http = New-Object System.Net.Http.HttpClient($handler)

    # 1. mock 授权 → 一次性登录码 → token
    $r1 = $http.GetAsync("$base/api/h5/wx/authorize?redirect=/index").Result
    $loc1 = "$($r1.Headers.Location)"
    if ($loc1.StartsWith('/')) { $loc1 = $base + $loc1 }
    $r2 = $http.GetAsync($loc1).Result
    $loc2 = "$($r2.Headers.Location)"
    $loginCode = ($loc2 -split 'h5_code=')[1]
    $login = Invoke-RestMethod -Uri "$base/api/h5/wx/exchange" -Method Post -Body @{ code = $loginCode }
    $token = $login.data
    Check 'MOCK_AUTH' ($login.code -eq 0 -and ![string]::IsNullOrEmpty($token))
    $authHdr = @{ Authorization = "Bearer $token"; 'X-Requested-With' = 'XMLHttpRequest' }

    # 2. 绑定手机号（JDBC 直更，聚焦订单链路；验证码链路 smoke4 已覆盖）
    RunSql "update gas_member set phone='13900005555', phone_bound_at=sysdate() where openid='MOCK-MOCK';"

    # 3. 扫码进站
    $entry = Invoke-RestMethod -Uri "$base/api/h5/c/station/$qr" -Headers $authHdr
    Check 'STATION_ENTRY' ($entry.code -eq 0 -and $entry.data.guns.Count -eq 1 -and [decimal]$entry.data.guns[0].unitPrice -eq 3.5)
    $gunId = $entry.data.guns[0].gunId

    # 4. 试算 35 元 = 10 m³
    $pv = Invoke-RestMethod -Uri "$base/api/h5/c/order/preview" -Method Post -ContentType 'application/json' -Body "{`"gunId`":$gunId,`"orderAmount`":`"35.00`"}" -Headers $authHdr
    Check 'PREVIEW' ($pv.code -eq 0 -and [decimal]$pv.data.estVolume -eq 10)

    # 5. 创建订单
    $od = Invoke-RestMethod -Uri "$base/api/h5/c/order" -Method Post -ContentType 'application/json' -Body "{`"gunId`":$gunId,`"orderAmount`":`"35.00`"}" -Headers $authHdr
    Check 'ORDER_CREATE' ($od.code -eq 0 -and $od.data.orderNo.StartsWith('SO'))
    $orderNo = $od.data.orderNo

    # 6. 发起支付（mock 网关）
    $pay = Invoke-RestMethod -Uri "$base/api/h5/c/order/$orderNo/pay" -Method Post -Headers $authHdr
    Check 'PAY_LAUNCH' ($pay.code -eq 0 -and $pay.data.payUrl -like '*mock/cashier*')
    $payUrl = $pay.data.payUrl

    # 7. 模拟收银台页面
    $cashier = Invoke-WebRequest -Uri ($base + $payUrl) -UseBasicParsing
    Check 'MOCK_CASHIER_PAGE' ($cashier.StatusCode -eq 200 -and $cashier.Content -like '*35.00*')

    # 8. 确认支付
    $payResult = Invoke-WebRequest -Uri "$base/api/pay/mock/cashier/pay" -Method Post -Body @{ outTradeNo = ($payUrl -replace '.*outTradeNo=', ''); payChannel = 'WECHAT' } -UseBasicParsing
    Check 'MOCK_PAY_SUCCESS' ($payResult.Content -like '*支付成功*')

    # 9. 订单状态 PAID
    $detail = Invoke-RestMethod -Uri "$base/api/h5/c/order/$orderNo" -Headers $authHdr
    Check 'ORDER_PAID' ($detail.code -eq 0 -and $detail.data.status -eq 'PAID')

    # 10. 核销码
    $rt = Invoke-RestMethod -Uri "$base/api/h5/c/order/$orderNo/token" -Headers $authHdr
    Check 'REDEEM_TOKEN' ($rt.code -eq 0 -and $rt.data.token.Length -eq 32)

    # 11. 会员码
    $mt = Invoke-RestMethod -Uri "$base/api/h5/c/member/token" -Headers $authHdr
    Check 'MEMBER_TOKEN' ($mt.code -eq 0 -and $mt.data.token.Length -eq 32)

    # 12. 订单列表
    $list = Invoke-RestMethod -Uri "$base/api/h5/c/order/list" -Headers $authHdr
    Check 'ORDER_LIST' ($list.code -eq 0 -and $list.rows.Count -ge 1)

    # 13. 池预占 = 10
    $reserved = & $mysql -uroot -proot ry -N -e "select reserved from gas_inventory_pool where gas_type='CNG' and station_id in (select station_id from gas_station where station_code='SMK5');" 2>$null
    Check 'POOL_RESERVED' ([decimal]$reserved -eq 10)

    # 14. 标准 notify：错商户/缺金额拒绝，正确商户金额幂等重放成功
    $outTradeNo = ($payUrl -replace '.*outTradeNo=', '')
    $merchantId = & $mysql -uroot -proot ry -N -e "select merchant_id from gas_buyer_merchant where merchant_no='MKT-SMK5';" 2>$null
    $badMerchant = Invoke-WebRequest -Uri "$base/api/pay/notify/$([int64]$merchantId + 1)" -Method Post -ContentType 'application/json' -Body "{`"out_trade_no`":`"$outTradeNo`",`"trade_no`":`"CH-SMK5`",`"pay_channel`":`"WECHAT`",`"amount`":`"35.00`"}" -UseBasicParsing
    Check 'NOTIFY_MERCHANT_MISMATCH_REJECT' ($badMerchant.Content -eq 'fail: merchant mismatch')
    $noAmount = Invoke-WebRequest -Uri "$base/api/pay/notify/$merchantId" -Method Post -ContentType 'application/json' -Body "{`"out_trade_no`":`"$outTradeNo`",`"trade_no`":`"CH-SMK5`",`"pay_channel`":`"WECHAT`"}" -UseBasicParsing
    Check 'NOTIFY_AMOUNT_REQUIRED' ($noAmount.Content -eq 'fail: amount required')
    $notify = Invoke-WebRequest -Uri "$base/api/pay/notify/$merchantId" -Method Post -ContentType 'application/json' -Body "{`"out_trade_no`":`"$outTradeNo`",`"trade_no`":`"CH-SMK5`",`"pay_channel`":`"WECHAT`",`"amount`":`"35.00`"}" -UseBasicParsing
    Check 'NOTIFY_REPLAY_IDEMPOTENT' ($notify.Content -eq 'success')
    $reserved2 = & $mysql -uroot -proot ry -N -e "select reserved from gas_inventory_pool where gas_type='CNG' and station_id in (select station_id from gas_station where station_code='SMK5');" 2>$null
    Check 'POOL_RESERVED_STILL_10' ([decimal]$reserved2 -eq 10)

    # 15. 未定价枪：列表展示但价格为空（前端禁用）；无价枪下单被拒
    RunSql "insert into gas_gun (station_id, gun_no, gas_type, status, create_time) select station_id, 9, 'CNG', '0', sysdate() from gas_station where station_code='SMK5';"
    $entry2 = Invoke-RestMethod -Uri "$base/api/h5/c/station/$qr" -Headers $authHdr
    $gun9 = $entry2.data.guns | Where-Object { $_.gunNo -eq 9 }
    Check 'GUN_NO_PRICE_LISTED_DISABLED' ($null -ne $gun9 -and $null -eq $gun9.unitPrice)
    $gun9Id = $gun9.gunId
    $noPrice = Invoke-RestMethod -Uri "$base/api/h5/c/order/preview" -Method Post -ContentType 'application/json' -Body "{`"gunId`":$gun9Id,`"orderAmount`":`"35.00`"}" -Headers $authHdr
    Check 'GUN_NO_PRICE_REJECT' ($noPrice.code -ne 0)
    $bad = Invoke-RestMethod -Uri "$base/api/h5/c/order" -Method Post -ContentType 'application/json' -Body "{`"gunId`":999999,`"orderAmount`":`"35.00`"}" -Headers $authHdr
    Check 'ORDER_GUN_INVALID_REJECT' ($bad.code -ne 0)

    Write-Output ("RESULT FAIL_COUNT=" + $fail)
}
finally {
    if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
    for ($i = 0; $i -lt 20; $i++) {
        if (-not (Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Milliseconds 500
    }
    $l = Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue
    if ($l) { $l | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue } }
    RunSql @"
delete from gas_dynamic_token where ref_id in (select member_id from gas_member where openid='MOCK-MOCK');
delete from gas_dynamic_token where token_type='REDEEM' and ref_id in (select order_id from gas_order where station_id in (select station_id from gas_station where station_code='SMK5'));
delete from gas_order_log where order_id in (select order_id from gas_order where station_id in (select station_id from gas_station where station_code='SMK5'));
delete from gas_pay_txn where order_id in (select order_id from gas_order where station_id in (select station_id from gas_station where station_code='SMK5'));
delete from gas_order where station_id in (select station_id from gas_station where station_code='SMK5');
delete from gas_inventory_ledger where pool_id in (select pool_id from gas_inventory_pool where station_id in (select station_id from gas_station where station_code='SMK5'));
delete from gas_inventory_pool where station_id in (select station_id from gas_station where station_code='SMK5');
delete from gas_buyer_merchant where merchant_no='MKT-SMK5';
delete from gas_gun_price where gun_id in (select gun_id from gas_gun where station_id in (select station_id from gas_station where station_code='SMK5'));
delete from gas_gun where station_id in (select station_id from gas_station where station_code='SMK5');
delete from sys_dept where dept_name='冒烟5部门';
delete from gas_station where station_code='SMK5';
delete from gas_member where openid='MOCK-MOCK';
delete from gas_buyer where buyer_name='冒烟5采购商';
"@
    Write-Output 'CLEANED'
}
exit $fail
