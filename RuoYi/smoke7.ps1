$ErrorActionPreference = 'Stop'
if (Get-NetTCPConnection -LocalPort 9090 -State Listen -ErrorAction SilentlyContinue) { throw '端口9090已占用，停止冒烟以避免接管其他服务' }
$base = 'http://localhost:9090'
$jar = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\ruoyi-admin.jar'
$log = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\smoke7.log'
$errlog = 'd:\Project\workspace\gas\RuoYi\ruoyi-admin\target\smoke7.err.log'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.3\bin\mysql.exe'
${tag} = 'S7' + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$qr = ${tag} + '-QR'
$phone = '1390777' + (Get-Random -Minimum 1000 -Maximum 9999)
$fail = 0
$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Check($name, $cond) {
    if ($cond) { Write-Output "PASS $name" } else { Write-Output "FAIL $name"; $script:fail++ }
}
function RunSql($sql) {
    $tmp = [System.IO.Path]::GetTempFileName()
    [System.IO.File]::WriteAllText($tmp, $sql, (New-Object System.Text.UTF8Encoding($false)))
    $sourcePath = $tmp.Replace('\', '/')
    & $mysql -uroot -proot ry --default-character-set=utf8mb4 -e "source $sourcePath" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "SQL failed: $sql" }
    Remove-Item $tmp -ErrorAction SilentlyContinue
}
function Post($path, $headers, $body = @{}) {
    return Invoke-RestMethod -Uri "$base$path" -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ($body | ConvertTo-Json -Depth 8)
}
function FormPost($path, $headers, $body) {
    return Invoke-RestMethod -Uri "$base$path" -Method Post -Headers $headers -ContentType 'application/x-www-form-urlencoded' -Body $body -WebSession $adminSession
}
function TokenFor($code) {
    $handler = New-Object System.Net.Http.HttpClientHandler
    $handler.AllowAutoRedirect = $false
    $http = New-Object System.Net.Http.HttpClient($handler)
    $r1 = $http.GetAsync("$base/api/h5/wx/authorize?redirect=/index").Result
    $loc1 = "$($r1.Headers.Location)" -replace 'code=MOCK', "code=$code"
    if ($loc1.StartsWith('/')) { $loc1 = $base + $loc1 }
    $loc2 = "$($http.GetAsync($loc1).Result.Headers.Location)"
    $loginCode = ($loc2 -split 'h5_code=')[1]
    return (Invoke-RestMethod -Uri "$base/api/h5/wx/exchange" -Method Post -Body @{ code=$loginCode }).data
}
function HeadersFor($token) { return @{ Authorization="Bearer $token"; 'X-Requested-With'='XMLHttpRequest' } }
function PayMock($orderNo, $headers) {
    $pay = Post "/api/h5/c/order/$orderNo/pay" $headers
    $result = Invoke-WebRequest -Uri "$base/api/pay/mock/cashier/pay" -Method Post -Body @{ outTradeNo=($pay.data.payUrl -replace '.*outTradeNo=',''); payChannel='WECHAT' } -UseBasicParsing
    return Invoke-RestMethod -Uri "$base/api/h5/c/order/$orderNo" -Headers $headers
}
function NewTemplateAndCoupon($name, $admin, $buyerId, $memberPhone) {
    $created = FormPost '/gas/coupon/add' $admin @{ buyerId=$buyerId; name=$name; faceValue='5.00'; thresholdAmount='20.00'; validType='DAYS'; validDays='30'; validFrom=''; validTo=''; totalQty='1'; status='0'; remark='阶段7冒烟' }
    Check "TEMPLATE_CREATE_$name" ($created.code -eq 0) | Out-Host
    $templateId = & $mysql -uroot -proot ry -N -e "select template_id from gas_coupon_template where name='$name';" 2>$null
    $granted = FormPost '/gas/coupon/grant' $admin @{ templateId=$templateId; phone=$memberPhone; memberId='' }
    Check "GRANT_$name" ($granted.code -eq 0) | Out-Host
    $couponId = & $mysql -uroot -proot ry -N -e "select coupon_id from gas_coupon where template_id=$templateId;" 2>$null
    return @($templateId, $couponId)
}

RunSql @"
set @buyer=null;
insert into gas_buyer (buyer_name,short_name,status,create_time) values ('${tag}采购商','${tag}','0',sysdate()); set @buyer=last_insert_id();
insert into gas_buyer_merchant (buyer_id,channel,merchant_no,status,create_by,create_time) values (@buyer,'LAKALA','${tag}-MKT','0','smoke7',sysdate());
insert into sys_dept (parent_id,ancestors,dept_name,order_num,status,del_flag,create_by,create_time) values (100,'0,100','${tag}部门',99,'0','0','smoke7',sysdate()); set @dept=last_insert_id();
insert into gas_station (station_code,station_name,buyer_id,dept_id,qr_token,status,create_time) values ('${tag}','${tag}站点',@buyer,@dept,'$qr','0',sysdate()); set @station=last_insert_id();
insert into gas_gun (station_id,gun_no,gas_type,status,create_time) values (@station,1,'CNG','0',sysdate()); set @gun=last_insert_id();
insert into gas_gun_price (gun_id,price,effective_from,create_time) values (@gun,3.5000,date_sub(now(),interval 1 minute),sysdate());
insert into gas_inventory_pool (station_id,buyer_id,gas_type,total_in,adjusted,consumed,reserved,update_time) values (@station,@buyer,'CNG',1000,0,0,0,sysdate());
insert into gas_purchase_batch (batch_no,station_id,buyer_id,gas_type,qty,unit_price,amount,purchase_date,pay_status,invoice_status,create_by,create_time) values ('B-${tag}',@station,@buyer,'CNG',1000,3,3000,curdate(),'UNPAID','UNINVOICED','smoke7',sysdate()); set @batch=last_insert_id();
insert into gas_purchase_receipt (batch_id,buyer_id,station_id,gas_type,qty,consumed,adjusted,received_at,create_by,create_time) values (@batch,@buyer,@station,'CNG',1000,0,0,sysdate(),'smoke7',sysdate());
insert into sys_user (login_name,user_name,password,status,del_flag,create_by,create_time,dept_id) values ('${tag}-staff','${tag}加气员','unused','0','0','smoke7',sysdate(),@dept); set @staff=last_insert_id();
insert into sys_user_role (user_id,role_id) values (@staff,103);
"@

$p = Start-Process -FilePath 'java' -ArgumentList @('-jar',"`"$jar`"",'--shiro.user.captchaEnabled=false','--gas.h5.mock-mode=true') -RedirectStandardOutput $log -RedirectStandardError $errlog -PassThru -WindowStyle Hidden
Write-Output "PID=$($p.Id)"
try {
    $up = $false
    for ($i=0; $i -lt 45; $i++) {
        Start-Sleep -Seconds 2
        if ($p.HasExited) { break }
        try { if ((Invoke-WebRequest -Uri "$base/login" -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200) { $up=$true; break } } catch {}
    }
    if (-not $up) { Write-Output 'APP_NOT_UP'; exit 1 }

    $admin = @{ 'X-Requested-With'='XMLHttpRequest' }
    $login = FormPost '/login' $admin @{ username='admin'; password='admin123'; rememberMe='false' }
    Check 'ADMIN_LOGIN' ($login.code -eq 0)
    $adminPage = Invoke-WebRequest -Uri "$base/gas/coupon" -UseBasicParsing -WebSession $adminSession
    Check 'ADMIN_COUPON_PAGE' ($adminPage.StatusCode -eq 200 -and $adminPage.Content -like '*优惠券管理*')

    $member = HeadersFor (TokenFor "${tag}-M")
    RunSql "update gas_member set phone='$phone',phone_bound_at=sysdate() where openid='MOCK-${tag}-M';"
    RunSql "insert into gas_staff_binding(openid,user_id,station_id,bound_at) select 'MOCK-${tag}-S',user_id,station_id,sysdate() from sys_user,gas_station where login_name='${tag}-staff' and station_code='${tag}';"
    $staff = HeadersFor (TokenFor "${tag}-S")
    $buyerId = & $mysql -uroot -proot ry -N -e "select buyer_id from gas_station where station_code='${tag}';" 2>$null
    $entry = Invoke-RestMethod -Uri "$base/api/h5/c/station/$qr" -Headers $member
    $gunId = $entry.data.guns[0].gunId

    $selfIds = NewTemplateAndCoupon "${tag}-SELF" $admin $buyerId $phone
    $selfCoupon = $selfIds[1]
    $limited = FormPost '/gas/coupon/grant' $admin @{ templateId=$selfIds[0]; phone=$phone; memberId='' }
    Check 'ISSUE_TOTAL_LIMIT' ($limited.code -ne 0)
    $blankTarget = FormPost '/gas/coupon/grant' $admin @{ templateId=$selfIds[0]; phone=''; memberId='' }
    Check 'GRANT_EMPTY_TARGET_REJECT' ($blankTarget.code -ne 0)
    $unused = Invoke-RestMethod -Uri "$base/api/h5/c/coupon/list?status=UNUSED" -Headers $member
    Check 'MEMBER_COUPON_LIST' ($unused.code -eq 0 -and $unused.data.Count -eq 1 -and $unused.data[0].couponId -eq $selfCoupon)
    $preview = Post '/api/h5/c/order/preview' $member @{ gunId=$gunId; orderAmount='35.00'; couponId=$selfCoupon }
    Check 'PREVIEW_DISCOUNT' ($preview.code -eq 0 -and [decimal]$preview.data.discountAmount -eq 5 -and [decimal]$preview.data.payAmount -eq 30 -and [decimal]$preview.data.estVolume -eq 10)
    $selfRequestId = [guid]::NewGuid().ToString('N')
    $selfOrder = Post '/api/h5/c/order' $member @{ gunId=$gunId; orderAmount='35.00'; couponId=$selfCoupon; requestId=$selfRequestId }
    Check 'SELF_ORDER_DISCOUNT' ($selfOrder.code -eq 0 -and $selfOrder.data.couponId -eq $selfCoupon -and [decimal]$selfOrder.data.payAmount -eq 30)
    $idempotent = Post '/api/h5/c/order' $member @{ gunId=$gunId; orderAmount='35.00'; couponId=$selfCoupon; requestId=$selfRequestId }
    Check 'SELF_ORDER_IDEMPOTENT' ($idempotent.code -eq 0 -and $idempotent.data.orderNo -eq $selfOrder.data.orderNo)
    $duplicate = Post '/api/h5/c/order' $member @{ gunId=$gunId; orderAmount='35.00'; couponId=$selfCoupon }
    Check 'DUPLICATE_COUPON_REJECT' ($duplicate.code -ne 0)
    $selfPaid = PayMock $selfOrder.data.orderNo $member
    Check 'SELF_DISCOUNT_PAID' ($selfPaid.data.status -eq 'PAID' -and [decimal]$selfPaid.data.payAmount -eq 30)

    $shift = Post '/api/h5/s/shift/start' $staff
    $redeemCode = (Invoke-RestMethod -Uri "$base/api/h5/c/order/$($selfOrder.data.orderNo)/token" -Headers $member).data.token
    $redeemed = Post '/api/h5/s/redeem' $staff @{ token=$redeemCode; shiftId=$shift.data.shiftId; actualVolume='10.00' }
    Check 'COUPON_ORDER_REDEEMED' ($redeemed.code -eq 0 -and $redeemed.data.status -eq 'COMPLETED')
    $stats = FormPost '/gas/coupon/list' $admin @{ name="${tag}-SELF"; status='' }
    Check 'ADMIN_REDEEM_STATS' ($stats.rows.Count -eq 1 -and $stats.rows[0].usedQty -eq 1 -and [decimal]$stats.rows[0].discountTotal -eq 5)

    $proxyIds = NewTemplateAndCoupon "${tag}-PROXY" $admin $buyerId $phone
    $proxyCoupon = $proxyIds[1]
    $memberCode = (Invoke-RestMethod -Uri "$base/api/h5/c/member/token" -Headers $member).data.token
    $proxy = Post '/api/h5/s/proxy/order' $staff @{ token=$memberCode; shiftId=$shift.data.shiftId; gunId=$gunId; orderAmount='35.00' }
    $beforeStatus = & $mysql -uroot -proot ry -N -e "select status from gas_coupon where coupon_id=$proxyCoupon;" 2>$null
    Check 'PROXY_NO_LOCK_BEFORE_CONFIRM' ($proxy.data.status -eq 'WAIT_CONFIRM' -and $beforeStatus -eq 'UNUSED')
    $available = Invoke-RestMethod -Uri "$base/api/h5/c/coupon/available?orderNo=$($proxy.data.orderNo)" -Headers $member
    Check 'PROXY_AVAILABLE_COUPON' ($available.code -eq 0 -and $available.data.Count -eq 1 -and $available.data[0].couponId -eq $proxyCoupon)
    $confirmed = Post "/api/h5/c/order/$($proxy.data.orderNo)/confirm" $member @{ couponId=$proxyCoupon }
    Check 'PROXY_CONFIRM_DISCOUNT' ($confirmed.code -eq 0 -and [decimal]$confirmed.data.discountAmount -eq 5 -and [decimal]$confirmed.data.payAmount -eq 30)
    $proxyPaid = PayMock $proxy.data.orderNo $member
    Check 'PROXY_DISCOUNT_PAID' ($proxyPaid.data.status -eq 'PAID' -and [decimal]$proxyPaid.data.payAmount -eq 30)

    $timeoutIds = NewTemplateAndCoupon "${tag}-TIMEOUT" $admin $buyerId $phone
    $timeoutOrder = Post '/api/h5/c/order' $member @{ gunId=$gunId; orderAmount='35.00'; couponId=$timeoutIds[1] }
    RunSql "update gas_order set expire_at=date_sub(now(),interval 1 minute) where order_no='$($timeoutOrder.data.orderNo)';"
    $run = FormPost '/monitor/job/run' $admin @{ jobId=101; concurrent='0' }
    Check 'TRIGGER_CLOSE_TASK' ($run.code -eq 0)
    $released = ''
    for ($i=0; $i -lt 10; $i++) {
        Start-Sleep -Milliseconds 500
        $released = & $mysql -uroot -proot ry -N -e "select status from gas_coupon where coupon_id=$($timeoutIds[1]);" 2>$null
        if ($released -eq 'UNUSED') { break }
    }
    Check 'TIMEOUT_COUPON_RELEASED' ($released -eq 'UNUSED')
    $usedList = Invoke-RestMethod -Uri "$base/api/h5/c/coupon/list?status=USED" -Headers $member
    Check 'MEMBER_USED_COUPONS' ($usedList.code -eq 0 -and $usedList.data.Count -eq 2)
    Write-Output "RESULT FAIL_COUNT=$fail"
}
finally {
    if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 1
    RunSql @"
delete from gas_dynamic_token where token_type='MEMBER' and ref_id in (select member_id from gas_member where openid like 'MOCK-${tag}-%');
delete from gas_dynamic_token where token_type='REDEEM' and ref_id in (select order_id from gas_order where station_id in (select station_id from gas_station where station_code='${tag}'));
delete from gas_order_log where order_id in (select order_id from gas_order where station_id in (select station_id from gas_station where station_code='${tag}'));
delete from gas_pay_txn where order_id in (select order_id from gas_order where station_id in (select station_id from gas_station where station_code='${tag}'));
delete from gas_order where station_id in (select station_id from gas_station where station_code='${tag}');
delete c from gas_coupon c join gas_coupon_template t on c.template_id=t.template_id join gas_station s on t.buyer_id=s.buyer_id where s.station_code='${tag}';
delete t from gas_coupon_template t join gas_station s on t.buyer_id=s.buyer_id where s.station_code='${tag}';
delete from gas_shift where staff_name='${tag}加气员';
delete from gas_staff_binding where openid='MOCK-${tag}-S';
delete from sys_user_role where user_id in (select user_id from sys_user where login_name='${tag}-staff');
delete from sys_user where login_name='${tag}-staff';
delete from gas_inventory_ledger where pool_id in (select pool_id from gas_inventory_pool where station_id in (select station_id from gas_station where station_code='${tag}'));
delete from gas_inventory_pool where station_id in (select station_id from gas_station where station_code='${tag}');
delete from gas_purchase_receipt where station_id in (select station_id from gas_station where station_code='${tag}');
delete from gas_purchase_batch where station_id in (select station_id from gas_station where station_code='${tag}');
delete from gas_buyer_merchant where merchant_no='${tag}-MKT';
delete from gas_gun_price where gun_id in (select gun_id from gas_gun where station_id in (select station_id from gas_station where station_code='${tag}'));
delete from gas_gun where station_id in (select station_id from gas_station where station_code='${tag}');
delete from sys_dept where dept_name='${tag}部门';
delete from gas_station where station_code='${tag}';
delete from gas_member where openid like 'MOCK-${tag}-%';
delete from gas_buyer where buyer_name='${tag}采购商';
"@
    Write-Output 'CLEANED'
}
exit $fail
